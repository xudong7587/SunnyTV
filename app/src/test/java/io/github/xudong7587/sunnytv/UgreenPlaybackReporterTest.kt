package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.network.SafeHttp
import io.github.xudong7587.sunnytv.core.playback.PlaybackReporter
import io.github.xudong7587.sunnytv.source.emby.EmbySource
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class UgreenPlaybackReporterTest {
    @Test fun durationDiscoveredByPlayerReachesSerialStartProgressAndStop() {
        MockWebServer().use {server->
            repeat(3) {server.enqueue(MockResponse().setResponseCode(204))}
            val api=EmbySource(SourceConfig("green",SourceKind.EMBY,"Green",server.url("/emby/").toString(),
                "u","","token",EmbyProfile.UGREEN),SafeHttp(),"test")
            val request=PlaybackRequest("green",server.url("/emby/videos/7500-episode-1/stream.mp4").toString(),"Episode",
                embyItemId="7500-episode-1",mediaSourceId="source",playSessionId="session")
            val failures=AtomicInteger()
            val reporter=PlaybackReporter(request,{api},{failures.incrementAndGet()})
            reporter.start(5000,false,60_000)
            fun check(path:String,position:Long) {
                val call=server.takeRequest(5,TimeUnit.SECONDS) ?: error("No event received")
                assertEquals("/emby/Sessions/$path",call.requestUrl!!.encodedPath)
                val data=JSONObject(call.body.readUtf8())
                assertEquals("7500-episode-1",data.getString("ItemId"))
                assertEquals("session",data.getString("PlaySessionId"))
                assertEquals(position*10_000,data.getLong("PositionTicks"))
                assertEquals(600_000_000L,data.getLong("RunTimeTicks"))
            }
            check("Playing",5000)
            reporter.progress(20_000,false,-1) // TIME_UNSET must not replace a known duration.
            check("Playing/Progress",20_000)
            reporter.close(25_000,true)
            check("Playing/Stopped",25_000)
            assertEquals(0,failures.get())
        }
    }
}
