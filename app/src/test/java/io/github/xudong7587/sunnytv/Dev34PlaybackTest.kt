package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.network.SafeHttp
import io.github.xudong7587.sunnytv.source.emby.EmbySource
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class Dev34PlaybackTest {
    private fun source(server:MockWebServer)=EmbySource(SourceConfig("s",SourceKind.EMBY,"Test",server.url("/emby/").toString(),"u","","test-token"),SafeHttp(),"test-device")
    @Test fun compatibilityUsesServerNegotiatedUrlAndSelectedAudio()=runBlocking {
        MockWebServer().use {server->
            server.enqueue(MockResponse().setBody("""{"Policy":{"EnableAudioPlaybackTranscoding":true}}"""))
            server.enqueue(MockResponse().setBody("""{"PlaySessionId":"server-session","MediaSources":[{"Id":"source","TranscodingUrl":"/emby/Videos/123/master.m3u8?AudioCodec=aac&AudioStreamIndex=3&VideoCodec=hevc&api_key=test-token&PlaySessionId=server-session"}]}"""))
            val request=PlaybackRequest("s","https://example.org/media.mkv","test",embyItemId="123",mediaSourceId="source",
                sourceTracks=listOf(MediaTrack(0,"Video","","4K","hevc"),MediaTrack(3,"Audio","eng","English","eac3")))
            val compatible=source(server).compatibleAudio(request,3)
            val url=compatible.stableUrl.toHttpUrl()
            assertEquals("/emby/Videos/123/master.m3u8",url.encodedPath)
            assertEquals("hevc",url.queryParameter("VideoCodec"))
            assertEquals("aac",url.queryParameter("AudioCodec"))
            assertEquals("3",url.queryParameter("AudioStreamIndex"))
            server.takeRequest()
            val negotiation=server.takeRequest()
            assertEquals("/emby/Items/123/PlaybackInfo",negotiation.requestUrl!!.encodedPath)
            val body=JSONObject(negotiation.body.readUtf8())
            assertEquals(3,body.getInt("AudioStreamIndex"))
            assertFalse(body.getBoolean("AllowAudioStreamCopy"))
            assertTrue(body.getBoolean("AllowVideoStreamCopy"))
            assertFalse(body.getBoolean("EnableDirectPlay"))
            val profile=body.getJSONObject("DeviceProfile").getJSONArray("TranscodingProfiles").getJSONObject(0)
            assertEquals("aac",profile.getString("AudioCodec"))
            assertEquals("hevc",profile.getString("VideoCodec"))
            assertEquals("server-session",compatible.playSessionId)
            assertTrue(compatible.audioCompatibility);assertNull(compatible.fallbackUrl)
            assertNotEquals(request.playSessionId,compatible.playSessionId)
            assertFalse(url.toString().contains("test-token"))
        }
    }
    @Test fun compatibilityRejectsForeignServerUrl()=runBlocking {
        MockWebServer().use {server->
            server.enqueue(MockResponse().setBody("""{"Policy":{}}"""))
            server.enqueue(MockResponse().setBody("""{"PlaySessionId":"session","MediaSources":[{"Id":"source","TranscodingUrl":"https://foreign.invalid/video.m3u8?api_key=secret"}]}"""))
            val request=PlaybackRequest("s","https://example.org/media.mkv","test",embyItemId="123",mediaSourceId="source",
                sourceTracks=listOf(MediaTrack(0,"Video","","HD","h264"),MediaTrack(1,"Audio","eng","English","eac3")))
            try {source(server).compatibleAudio(request,1);fail("expected refusal")}
            catch(e:IllegalArgumentException) {assertTrue(e.message.orEmpty().contains("当前服务器"))}
        }
    }
    @Test fun deniedAudioTranscodeIsExplicit()=runBlocking {
        MockWebServer().use {server->
            server.enqueue(MockResponse().setBody("""{"Policy":{"EnableAudioPlaybackTranscoding":false}}"""))
            val request=PlaybackRequest("s","https://example.org/media.mkv","test",embyItemId="123")
            try {source(server).compatibleAudio(request,1);fail("expected refusal")} catch(e:Exception) {assertTrue(e.message.orEmpty().contains("音频播放转换"))}
        }
    }
    @Test fun searchUsesInstalledProviderAndDownloadEncodesItsOpaqueId()=runBlocking {
        MockWebServer().use {server->
            val api=source(server)
            server.enqueue(MockResponse().setBody("""[{"Id":"provider/id?x","Name":"test","ProviderName":"provider","Format":"srt","IsHashMatch":true}]"""))
            val results=api.searchSubtitles("film","source","chi")
            assertTrue(results.single().hashMatch)
            val search=server.takeRequest()
            assertEquals("/emby/Items/film/RemoteSearch/Subtitles/chi",search.requestUrl!!.encodedPath)
            assertEquals("source",search.requestUrl!!.queryParameter("MediaSourceId"))
            server.enqueue(MockResponse().setBody("""{"NewIndex":7}"""))
            assertEquals(7,api.downloadSubtitle("film","source",results.single()))
            val download=server.takeRequest()
            assertEquals("POST",download.method)
            assertEquals("provider/id?x",download.requestUrl!!.pathSegments.last())
            assertEquals("source",download.requestUrl!!.queryParameter("MediaSourceId"))
        }
    }
    @Test fun playbackCarriesAudioIndexAndStableExtractedSubtitleIdentity()=runBlocking {
        MockWebServer().use {server->
            server.enqueue(MockResponse().setBody("""{"PlaySessionId":"session","MediaSources":[{"Id":"version","Container":"mkv","SupportsDirectStream":true,"MediaStreams":[{"Index":0,"Type":"Video","Codec":"h264"},{"Index":1,"Type":"Audio","Language":"eng","Codec":"eac3"},{"Index":2,"Type":"Audio","Language":"fre","Codec":"eac3"},{"Index":3,"Type":"Subtitle","Codec":"srt","IsTextSubtitleStream":true,"SupportsExternalStream":true}]}]}"""))
            // STRM resolver probes the stream but does not read a video body as text.
            server.enqueue(MockResponse().setHeader("Content-Type","video/x-matroska"))
            val result=source(server).playback(MediaEntry("film","s","test","Movie"),versionId="version",audioIndex=2)
            val body=JSONObject(server.takeRequest().body.readUtf8())
            assertEquals(2,body.getInt("AudioStreamIndex"));assertEquals("version",body.getString("MediaSourceId"))
            assertEquals(1,result.audioOrdinal)
            assertEquals("emby-sub:3",result.subtitles.single().id)
            assertEquals(2,result.sourceTracks.count {it.type=="Audio"})
        }
    }
}
