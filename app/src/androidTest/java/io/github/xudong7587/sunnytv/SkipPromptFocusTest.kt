package io.github.xudong7587.sunnytv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.player.PlayerActivity
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Test the real PlayerActivity focus wiring with local media and a real rendered first frame. */
@OptIn(ExperimentalTestApi::class)
class SkipPromptFocusTest {
    @get:Rule val rule=createEmptyComposeRule()
    private fun player(a:PlayerActivity)=PlayerActivity::class.java.getDeclaredMethod("getPlayer")
        .apply {isAccessible=true}.invoke(a) as ExoPlayer
    private fun focus(tag:String) {
        try {
            rule.waitUntil(5000) {
                rule.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
            }
        } catch(e:Throwable) {
            val input=InstrumentationRegistry.getInstrumentation()
            java.io.File(input.targetContext.cacheDir,"skip-focus-failure.txt")
                .writeText(rule.onRoot(useUnmergedTree=true).printToString())
            java.io.File(input.targetContext.cacheDir,"skip-focus-failure.png").outputStream().use {
                input.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            val actual=rule.onAllNodes(isFocused(),useUnmergedTree=true).fetchSemanticsNodes().map {it.config.toString()}
            throw AssertionError("Expected focus $tag; actual=$actual",e)
        }
    }
    @Test fun introPromptCanBeRevisitedFromBottomControlsAndDismissedWithoutDeadFocus() {
        val input=InstrumentationRegistry.getInstrumentation()
        val context=input.targetContext
        val bytes=input.context.assets.open("quality/original.mp4").use {it.readBytes()}
        val server=MockWebServer()
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                val start=request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull()
                    ?.coerceIn(0,bytes.size) ?: 0
                return MockResponse().setResponseCode(if(start>0)206 else 200)
                    .setHeader("Content-Type","video/mp4")
                    .apply {if(start>0)setHeader("Content-Range","bytes $start-${bytes.size-1}/${bytes.size}")}
                    .setBody(Buffer().write(bytes.copyOfRange(start,bytes.size)))
            }
        }
        server.start()
        try {
            ActivityScenario.launch<PlayerActivity>(PlayerActivity.intent(context,
                PlaybackRequest("",server.url("intro.mp4").toString(),"片头焦点测试"))).use {scenario->
                rule.waitUntil(20_000) {
                    var ready=false
                    scenario.onActivity {a->ready=player(a).isPlaying && PlayerActivity::class.java
                        .getDeclaredField("rendered").apply {isAccessible=true}.getBoolean(a)}
                    ready
                }
                input.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
                val item=MediaEntry("current","fixture","片头焦点测试","Episode")
                fun metadata(segments:List<SkipSegment>)=scenario.onActivity {a->
                    player(a).pause()
                    PlayerActivity::class.java.getDeclaredMethod("setMediaContext",PlayerMediaContext::class.java)
                        .apply {isAccessible=true}.invoke(a,PlayerMediaContext(item,skipSegments=segments))
                }
                metadata(listOf(SkipSegment("intro","intro",0,60_000)))
                focus("player:skip")
                repeat(2) {
                    rule.onNodeWithTag("player:skip").performKeyInput {pressKey(Key.DirectionDown)}
                    focus("player:progress")
                    rule.onNodeWithTag("player:progress").performKeyInput {pressKey(Key.DirectionDown)}
                    rule.waitUntil(5000) {rule.onAllNodes(isFocused()).fetchSemanticsNodes().any {
                        it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag){""}
                            .let {tag->tag.startsWith("player:") && tag!="player:progress" && tag!="player:skip" && tag!="player:close"}
                    }}
                    // Send actual hardware keys through Activity dispatch, as a remote does.
                    input.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP)
                    focus("player:progress")
                    rule.onNodeWithTag("player:progress").performKeyInput {pressKey(Key.DirectionUp)}
                    focus("player:skip")
                }
                rule.onNodeWithTag("player:skip").performKeyInput {pressKey(Key.DirectionRight)}
                focus("player:close")
                rule.onNodeWithTag("player:close").performKeyInput {pressKey(Key.DirectionDown)}
                focus("player:progress")
                rule.onNodeWithTag("player:progress").performKeyInput {pressKey(Key.DirectionUp)}
                focus("player:skip")
                rule.onNodeWithTag("player:skip").performKeyInput {pressKey(Key.DirectionUp)}
                rule.onNodeWithContentDescription("音频输出").assertIsFocused()
                input.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
                focus("player:skip")
                // If the marker expires while navigating, no route may point to its detached node.
                metadata(emptyList())
                rule.onNodeWithTag("player:skip-prompt").assertDoesNotExist()
                rule.onNodeWithTag("player:speed").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
                    .performKeyInput {pressKey(Key.DirectionUp)}
                focus("player:progress")
                rule.onNodeWithTag("player:progress").performKeyInput {pressKey(Key.DirectionUp)}
                rule.onNodeWithContentDescription("音频输出").assertIsFocused()
                metadata(listOf(SkipSegment("intro-dismiss","intro",0,60_000)))
                focus("player:skip")
                rule.onNodeWithTag("player:skip").performKeyInput {pressKey(Key.DirectionRight)}
                focus("player:close")
                rule.onNodeWithTag("player:close").performKeyInput {pressKey(Key.DirectionCenter)}
                rule.onNodeWithTag("player:skip-prompt").assertDoesNotExist()
                focus("player:progress")
                rule.onNodeWithTag("player:progress").performKeyInput {pressKey(Key.DirectionUp)}
                rule.onNodeWithContentDescription("音频输出").assertIsFocused()
                metadata(listOf(SkipSegment("intro-skip","intro",0,8000)))
                focus("player:skip")
                rule.onNodeWithTag("player:skip").performKeyInput {pressKey(Key.DirectionCenter)}
                rule.onNodeWithTag("player:skip-prompt").assertDoesNotExist()
                focus("player:progress")
                rule.waitUntil(5000) {
                    var skipped=false
                    scenario.onActivity {a->skipped=player(a).currentPosition>=7900}
                    skipped
                }
            }
        } finally {server.shutdown()}
    }
}
