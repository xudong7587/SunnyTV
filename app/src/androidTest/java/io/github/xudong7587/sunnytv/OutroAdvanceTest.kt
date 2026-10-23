package io.github.xudong7587.sunnytv

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.exoplayer.ExoPlayer
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.storage.ConfigStore
import io.github.xudong7587.sunnytv.feature.player.PlayerActivity
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class OutroAdvanceTest {
 @get:Rule val rule=createEmptyComposeRule()
 private fun player(a:PlayerActivity)=PlayerActivity::class.java.getDeclaredMethod("getPlayer").apply {isAccessible=true}.invoke(a) as ExoPlayer
 @Test fun outroClickOpensNextEpisodeWithoutSeekingCurrentVideoToEnd() {
  val instrument=InstrumentationRegistry.getInstrumentation()
  val context=instrument.targetContext
  val bytes=instrument.context.assets.open("quality/original.mp4").use {it.readBytes()}
  val store=ConfigStore(context);val old=store.sources()
  val oldSettings=store.settings()
  val requests=AtomicInteger()
  val server=MockWebServer()
  server.start()
  server.dispatcher=object:Dispatcher() {
   override fun dispatch(request:RecordedRequest):MockResponse {
    val path=request.path.orEmpty()
    if(path.contains("PlaybackInfo")) {
     requests.incrementAndGet()
     return MockResponse().setBody("""{"MediaSources":[{"Id":"next","SupportsDirectPlay":true,"Path":"${server.url("next.mp4")}","Container":"mp4"}]}""")
    }
    if(path.contains(".mp4")) {
     val start=request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
     val data=bytes.copyOfRange(start.coerceIn(0,bytes.size),bytes.size)
     return MockResponse().setResponseCode(if(start>0) 206 else 200).setHeader("Content-Type","video/mp4")
      .apply {if(start>0) setHeader("Content-Range","bytes $start-${bytes.size-1}/${bytes.size}")}.setBody(Buffer().write(data))
    }
    return MockResponse().setBody("{}")
   }
  }
  var launched:PlayerActivity?=null
  val monitor=instrument.addMonitor(PlayerActivity::class.java.name,null,false)
  try {
   store.saveSources(listOf(SourceConfig("outro-fixture",SourceKind.EMBY,"Fixture",server.url("/").toString(),"fixture-user")))
   store.saveSettings(oldSettings.copy(mediaAudioPreferences=mapOf("outro-fixture:series" to "ja","outro-fixture:current" to "en","outro-fixture:next" to "yue")))
   ActivityScenario.launch<PlayerActivity>(PlayerActivity.intent(context,PlaybackRequest("outro-fixture",server.url("current.mp4").toString(),"第一集",
    audioPreference="en",audioPreferenceScope="outro-fixture:series"))).use {scenario->
    rule.waitUntil(15_000) {var ready=false;scenario.onActivity {ready=player(it).isPlaying};ready}
    // Consume the first launch; the next monitor hit must be the adjacent episode.
    monitor.waitForActivityWithTimeout(100)
    scenario.onActivity {a->
     player(a).pause()
     val current=MediaEntry("current","outro-fixture","第一集","Episode",seriesId="series")
     val next=MediaEntry("next","outro-fixture","第二集","Episode",seriesId="series")
     // Metadata duration deliberately differs from the decoder duration. The skip must not
     // rely on a seek hitting either end exactly or on a subsequent STATE_ENDED callback.
     val pc=PlayerMediaContext(current,next=next,skipSegments=listOf(SkipSegment("outro","outro",0,50_000)))
     PlayerActivity::class.java.getDeclaredMethod("setMediaContext",PlayerMediaContext::class.java).apply {isAccessible=true}.invoke(a,pc)
    }
    rule.onNodeWithTag("player:skip").assertExists().performClick()
    launched=monitor.waitForActivityWithTimeout(15_000) as? PlayerActivity
    assertNotNull("Outro skip must launch the adjacent episode",launched)
    assertEquals(1,requests.get())
    var title=""
    instrument.runOnMainSync {title=PlayerActivity::class.java.getDeclaredField("request").apply {isAccessible=true}.get(launched).let {(it as PlaybackRequest).title}}
    assertEquals("第二集",title)
    instrument.runOnMainSync {
     val nextRequest=PlayerActivity::class.java.getDeclaredField("request").apply {isAccessible=true}.get(launched) as PlaybackRequest
     assertEquals("Single-episode defaults must not override the series language", "ja",nextRequest.audioPreference)
     assertEquals("outro-fixture:series",nextRequest.audioPreferenceScope)
    }
   }
  } finally {
   instrument.runOnMainSync {launched?.finish()}
   instrument.removeMonitor(monitor);store.saveSources(old)
   context.getSharedPreferences("sunny-config",0).edit().remove("mediaAudio:outro-fixture:series").remove("mediaAudio:outro-fixture:current").remove("mediaAudio:outro-fixture:next").commit()
   store.saveSettings(oldSettings);server.shutdown()
  }
 }
}
