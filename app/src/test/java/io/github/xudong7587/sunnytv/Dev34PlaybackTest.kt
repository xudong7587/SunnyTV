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
    @Test fun successfulEmptyDownloadResponseFindsNewExternalTrack()=runBlocking {
        MockWebServer().use {server->
            server.enqueue(MockResponse().setBody("""{"Id":"film","Name":"test","Type":"Movie","MediaSources":[{"Id":"source","MediaStreams":[]}]}"""))
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(MockResponse().setBody("""{"Id":"film","Name":"test","Type":"Movie","MediaSources":[{"Id":"source","MediaStreams":[{"Index":7,"Type":"Subtitle","IsExternal":true,"Codec":"srt","Language":"chi"}]}]}"""))
            val result=source(server).downloadAndSelectSubtitle("film","source",RemoteSubtitle("id","subtitle","provider","chi","srt",false))
            assertEquals(7,result?.index)
            server.takeRequest()
            assertEquals("POST",server.takeRequest().method)
            assertEquals("GET",server.takeRequest().method)
        }
    }
    @Test fun permissionFailureRetainsActualHttpCode()=runBlocking {
        MockWebServer().use {server->
            server.enqueue(MockResponse().setResponseCode(403))
            try {source(server).downloadSubtitle("film","source",RemoteSubtitle("id","subtitle","provider","chi","srt",false));fail("expected refusal")}
            catch(e:Exception) {assertTrue(e.message.orEmpty().contains("HTTP 403"))}
            assertEquals(1,server.requestCount)
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
