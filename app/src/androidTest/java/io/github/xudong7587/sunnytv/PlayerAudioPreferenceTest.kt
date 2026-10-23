package io.github.xudong7587.sunnytv

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.player.PlayerActivity
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerAudioPreferenceTest {
    @get:Rule val rule=createEmptyComposeRule()
    private fun player(a:PlayerActivity)=PlayerActivity::class.java.getDeclaredMethod("getPlayer").apply {isAccessible=true}.invoke(a) as ExoPlayer
    @Test fun realContainerUsesPreferredLanguageAndFallsBackWhenMissing() {
        val instrument=InstrumentationRegistry.getInstrumentation()
        val bytes=instrument.context.assets.open("quality/multi-audio.mkv").use {it.readBytes()}
        val server=MockWebServer()
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                val start=request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                val from=start.coerceIn(0,bytes.size)
                return MockResponse().setResponseCode(if(from>0) 206 else 200).setHeader("Content-Type","video/x-matroska")
                    .setHeader("Accept-Ranges","bytes").apply {
                        if(from>0) setHeader("Content-Range","bytes $from-${bytes.lastIndex}/${bytes.size}")
                    }.setBody(Buffer().write(bytes,from,bytes.size-from))
            }
        }
        server.start()
        try {
            for((preference,expected) in listOf("ja" to setOf("jpn","ja"),"fr" to setOf("eng","en"),"default" to setOf("eng","en"))) {
                val request=AudioPreference.apply(PlaybackRequest("fixture",server.url("/multi.mkv").toString(),"Synthetic audio fixture",mimeHint="video/x-matroska"),preference)
                ActivityScenario.launch<PlayerActivity>(PlayerActivity.intent(instrument.targetContext,request)).use {scenario->
                    rule.waitUntil(20_000) {var found=false;scenario.onActivity {a->
                        val p=player(a)
                        found=p.currentPosition>100 && p.currentTracks.groups.filter {it.type==C.TRACK_TYPE_AUDIO}.any {g->
                            (0 until g.length).any {g.isTrackSelected(it) && g.getTrackFormat(it).language in expected}
                        }
                    };found}
                    // The preference is a starting choice: an actual user's later choice remains effective.
                    scenario.onActivity {a->val p=player(a);p.pause()}
                    rule.onNodeWithContentDescription("音轨").performClick()
                    rule.onNode((hasText("eng",substring=true) or hasText("en ·",substring=true)) and hasClickAction()).performClick()
                    rule.waitUntil(5_000) {var english=false;scenario.onActivity {a->
                        english=player(a).currentTracks.groups.filter {it.type==C.TRACK_TYPE_AUDIO}.any {g->
                            (0 until g.length).any {g.isTrackSelected(it) && g.getTrackFormat(it).language in setOf("en","eng")}
                        }
                    };english}
                }
            }
        } finally {server.shutdown()}
    }
}
