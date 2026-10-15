package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.network.SafeHttp
import io.github.xudong7587.sunnytv.source.emby.EmbySource
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UgreenEmbyContractTest {
    private fun source(server:MockWebServer) = EmbySource(
        SourceConfig("green",SourceKind.EMBY,"UGREEN",server.url("/emby/").toString(),"open-user","","test-token"),
        SafeHttp(),"test-device")
    private val episode = MediaEntry("7500-episode-8874","green","Episode","Episode",durationMs=120_000)
    private fun info(path:String,protocol:String="Http",extra:String="") = """
        {"PlaySessionId":"session","MediaSources":[{"Id":"source","Protocol":"$protocol",
        "Path":"$path","Container":"mkv","RunTimeTicks":27000000000,
        "SupportsDirectPlay":true,"SupportsDirectStream":true$extra}]}
    """.trimIndent()

    @Test fun suppliedRelativeHttpStreamPreservesPathQueryAndRealContainer() = runBlocking {
        MockWebServer().use {server ->
            val api=source(server)
            for(path in listOf("/emby/videos/7500-episode-8874/stream.mp4?part=a%2Fb&time=01:02&part=two",
                "videos/7500-episode-8874/stream.mp4?part=a%2Fb&time=01:02&part=two")) {
                server.enqueue(MockResponse().setBody(info(path)))
                val result=api.playback(episode,preferServerStream=true)
                val call=server.takeRequest()
                assertEquals("/emby/Items/7500-episode-8874/PlaybackInfo",call.path)
                assertEquals("test-token",call.getHeader("X-Emby-Token"))
                assertEquals(server.url("/emby/videos/7500-episode-8874/stream.mp4").toString()+
                    "?part=a%2Fb&time=01:02&part=two",result.stableUrl)
                assertEquals("video/x-matroska",result.mimeHint)
                assertEquals("DirectStream",result.playMethod)
                assertEquals(2_700_000L,result.durationMs)
                assertNull(result.sourceMediaUrl)
                assertNull(result.fallbackUrl)
            }
            assertEquals(2,server.requestCount) // No speculative stream reads or playback events.
        }
    }

    @Test fun localFilesAndOutOfScopeReferencesAreNotAcceptedAsHttpStreams() = runBlocking {
        MockWebServer().use {server ->
            val api=source(server)
            for((protocol,path) in listOf("File" to "/emby/videos/not-a-stream.mkv", "File" to "/volume2/film.mkv",
                "Http" to "/volume2/film.mkv", "Http" to "//other.invalid/emby/stream.mp4",
                "Http" to "../outside.mp4", "Http" to "/emby/%2e%2e/private.mp4")) {
                server.enqueue(MockResponse().setBody(info(path,protocol)))
                val result=api.playback(episode)
                server.takeRequest()
                assertTrue(result.stableUrl.startsWith(server.url("/emby/Videos/${episode.id}/stream").toString()))
                assertNull(result.fallbackUrl)
            }
        }
    }

    @Test fun explicitDirectStreamUrlStillTakesPrecedence() = runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setBody(info("/emby/videos/original/stream.mp4",
                extra=",\"DirectStreamUrl\":\"/emby/videos/selected/stream.mkv?Static=true\"")))
            val result=source(server).playback(episode)
            assertEquals(server.url("/emby/videos/selected/stream.mkv?Static=true").toString(),result.stableUrl)
        }
    }

    @Test fun progressReportsKeepActualEpisodeAndDurationAcrossEmpty204Responses() = runBlocking {
        MockWebServer().use {server ->
            val api=source(server)
            server.enqueue(MockResponse().setBody(info("/emby/videos/${episode.id}/stream.mp4")))
            val request=api.playback(episode)
            server.takeRequest()
            for(event in listOf("Playing","Playing/Progress","Playing/Stopped")) {
                server.enqueue(MockResponse().setResponseCode(204))
                api.report(request,event,95_000,false)
                val call=server.takeRequest()
                assertEquals("/emby/Sessions/$event",call.path)
                val body=JSONObject(call.body.readUtf8())
                assertEquals(episode.id,body.getString("ItemId"))
                assertEquals("source",body.getString("MediaSourceId"))
                assertEquals("session",body.getString("PlaySessionId"))
                assertEquals(950_000_000L,body.getLong("PositionTicks"))
                assertEquals(27_000_000_000L,body.getLong("RunTimeTicks"))
            }
        }
    }

    @Test fun unknownOrInvalidDurationIsOmittedAndItemDurationIsFallback() = runBlocking {
        MockWebServer().use {server ->
            val api=source(server)
            server.enqueue(MockResponse().setBody(info("/emby/videos/${episode.id}/stream.mp4").replace("27000000000","0")))
            val request=api.playback(episode)
            server.takeRequest()
            assertEquals(episode.durationMs,request.durationMs)
            for(duration in listOf(0L,-1L,Long.MAX_VALUE)) {
                server.enqueue(MockResponse().setResponseCode(204))
                api.report(request.copy(durationMs=duration),"Playing/Progress",0,false)
                assertFalse(JSONObject(server.takeRequest().body.readUtf8()).has("RunTimeTicks"))
            }
        }
    }
}
