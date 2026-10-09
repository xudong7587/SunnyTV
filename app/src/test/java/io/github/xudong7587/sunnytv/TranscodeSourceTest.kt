package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.network.SafeHttp
import io.github.xudong7587.sunnytv.source.transcode.TranscodeSource
import io.github.xudong7587.sunnytv.source.emby.EmbySource
import io.github.xudong7587.sunnytv.core.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class TranscodeSourceTest {
    @Test fun pendingSegmentsHaveBoundedRetryWithoutRetryingOtherErrors() {
        assertTrue(TranscodeSource.retryPendingSegment(504,1,true))
        assertTrue(TranscodeSource.retryPendingSegment(504,2,true))
        assertFalse(TranscodeSource.retryPendingSegment(504,3,true))
        assertFalse(TranscodeSource.retryPendingSegment(503,1,true))
        assertFalse(TranscodeSource.retryPendingSegment(504,1,false))
    }
    @Test fun knownServerErrorIsShownWithoutLeakingUnknownDetails() = runBlocking {
        MockWebServer().use { server ->
            val source = TranscodeSource.from(SafeHttp(), server.url("/api/play/token").toString())!!
            val known = "暂不支持此杜比视界视频的色调映射，请使用原画"
            server.enqueue(MockResponse().setResponseCode(503).setBody("""{"detail":"$known"}"""))
            assertEquals(known, runCatching { source.create("720p@2", 0) }.exceptionOrNull()?.message)
            server.enqueue(MockResponse().setResponseCode(503).setBody("""{"detail":"https://secret.invalid?token=private"}"""))
            assertEquals("转码服务暂时不可用", runCatching { source.create("720p@2", 0) }.exceptionOrNull()?.message)
        }
    }
    @Test fun bitrateOptionsRequireServerAdvertisement() = runBlocking {
        MockWebServer().use {server->
            server.enqueue(MockResponse().setBody("""{"protocol":"mediaindex-transcode-v1","available":true,"profiles":["4k"],"bitrates":{"4k":[10000000,20000000]}}"""))
            val id="a".repeat(32);val token="t".repeat(43)
            server.enqueue(MockResponse().setBody("""{"sessionId":"$id","sessionToken":"$token","playlistUrl":"/api/transcode/sessions/$id/index.m3u8?st=$token","startPositionMs":0,"durationMs":500000,"seekMode":"hls-vod"}"""))
            val source=TranscodeSource.from(SafeHttp(),server.url("/api/play/token").toString())!!
            assertEquals(listOf("4k","4k@20","4k@10"),source.capabilities())
            server.takeRequest()
            source.create("4k@10",0)
            val body=org.json.JSONObject(server.takeRequest().body.readUtf8())
            assertEquals("4k",body.getString("profile"));assertEquals(10000000,body.getInt("videoBitrate"))
        }
    }
    @Test fun explicitTestOriginUsesOnlyTheTestServer() = runBlocking {
        MockWebServer().use {server->
            server.enqueue(MockResponse().setBody("""{"protocol":"mediaindex-transcode-v1","available":true,"profiles":["1080p"]}"""))
            val http=SafeHttp()
            val source=TranscodeSource.from(http,"https://production.invalid/api/play/token",server.url("/").toString())!!
            assertEquals(listOf("1080p"),source.capabilities())
            assertEquals("/api/transcode/capabilities",server.takeRequest().path)
            assertNull(TranscodeSource.from(http,"https://production.invalid/api/play/token","http://user:password@host/"))
            assertNull(TranscodeSource.from(http,"https://production.invalid/api/play/token","http://host/path"))
            assertNull(TranscodeSource.from(http,"https://production.invalid/other",server.url("/").toString()))
        }
    }
    @Test fun onlyStableMediaIndexEntryIsRecognized() {
        val http=SafeHttp()
        assertNull(TranscodeSource.from(http,"https://cdn.test/movie.mp4?sign=secret"))
        assertNull(TranscodeSource.from(http,"https://nas.test/emby/Videos/1/stream?api_key=secret"))
        assertNull(TranscodeSource.from(http,"https://nas.test/api/play/token?anything=secret"))
        assertNotNull(TranscodeSource.from(http,"https://nas.test/api/play/token"))
        assertEquals(listOf("原画","4K / 2160P","2K / 1440P","1K / 1080P","0.75K / 720P"),TranscodeSource.qualities.map {it.second})
    }
    @Test fun capabilitiesAndSessionUseIdsWithoutServerCredentials() = runBlocking {
        MockWebServer().use {server->
            val id="a".repeat(32);val token="t".repeat(43)
            server.enqueue(MockResponse().setBody("""{"protocol":"mediaindex-transcode-v1","available":true,"profiles":["4k","1440p","1080p","720p"]}"""))
            server.enqueue(MockResponse().setBody("""{"sessionId":"$id","sessionToken":"$token","playlistUrl":"/api/transcode/sessions/$id/index.m3u8?st=$token","startPositionMs":123000,"durationMs":500000,"seekMode":"hls-vod"}"""))
            val source=TranscodeSource.from(SafeHttp(),server.url("/api/play/asset-token").toString())!!
            assertTrue("1080p" in source.capabilities())
            val session=source.create("1440p",123000)
            assertEquals(123000L,session.startMs)
            assertTrue(session.vod)
            val capability=server.takeRequest()
            assertEquals("/api/transcode/capabilities",capability.path)
            assertNull(capability.getHeader("X-Emby-Token"));assertNull(capability.getHeader("Authorization"))
            val create=server.takeRequest()
            val body=create.body.readUtf8()
            assertTrue(body.contains("\"profile\":\"1440p\""))
            assertTrue(body.contains("\"startPositionMs\":123000"))
            assertTrue(body.contains("\"delivery\":\"vod\""))
            assertNull(create.getHeader("X-Emby-Token"))
        }
    }
    @Test fun foreignPlaylistOriginIsRejected() = runBlocking {
        MockWebServer().use {server->
            val id="a".repeat(32);val token="t".repeat(43)
            server.enqueue(MockResponse().setBody("""{"sessionId":"$id","sessionToken":"$token","playlistUrl":"https://foreign.test/api/transcode/sessions/$id/index.m3u8?st=$token","startPositionMs":0,"durationMs":500000}"""))
            val source=TranscodeSource.from(SafeHttp(),server.url("/api/play/token").toString())!!
            assertTrue(runCatching {source.create("1080p",0)}.isFailure)
        }
    }
    @Test fun bitrateSelectionHasSafeControlLabel() {
        assertTrue(TranscodeSource.menuQualityOptions.all { it.first=="original" || '@' in it.first })
        assertEquals("4K",TranscodeSource.resolutionBadge(3840,1600))
        assertEquals("1K",TranscodeSource.resolutionBadge(1920,1080))
        assertEquals("2K",TranscodeSource.resolutionBadge(1440,2560))
        assertEquals("1K / 1080P · 4 Mbps",TranscodeSource.qualityLabel("1080p@4"))
        assertEquals("4K / 2160P",TranscodeSource.qualityLabel("4k"))
        assertEquals("原画",TranscodeSource.qualityLabel("original"))
    }
    @Test fun qualityButtonReflectsSelectionInsteadOfDecodedVideoSize() {
        assertEquals("原片", TranscodeSource.selectedQualityBadge("original"))
        assertEquals("4K", TranscodeSource.selectedQualityBadge("4k@20"))
        assertEquals("4K", TranscodeSource.selectedQualityBadge("4k@10"))
        assertEquals("2K", TranscodeSource.selectedQualityBadge("1440p@6"))
        assertEquals("1080P", TranscodeSource.selectedQualityBadge("1080p@4"))
        assertEquals("720P", TranscodeSource.selectedQualityBadge("720p@2"))
        assertEquals("原片", TranscodeSource.selectedQualityBadge("unknown"))
    }
    @Test fun embyServerPreferenceRetainsRemoteEntryForQualitySelection() = runBlocking {
        MockWebServer().use {server->
            val remote="https://mediaindex.invalid/api/play/signed-token"
            server.enqueue(MockResponse().setBody("""{"PlaySessionId":"play-session","MediaSources":[{"Id":"media-source","Path":"$remote","SupportsDirectPlay":true,"SupportsDirectStream":true,"Container":"mp4","MediaStreams":[]}]}"""))
            val source=EmbySource(SourceConfig("emby",SourceKind.EMBY,"Emby",server.url("/emby/").toString(),"user","","token"),SafeHttp(),"device")
            val request=source.playback(MediaEntry("movie","emby","Movie","Movie"),preferServerStream=true)
            assertEquals(remote,request.sourceMediaUrl)
            assertNotEquals(remote,request.stableUrl)
            assertNotNull(TranscodeSource.from(SafeHttp(),request.sourceMediaUrl!!))
            assertEquals("play-session",request.playSessionId)
        }
    }
}
