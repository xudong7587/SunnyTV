package io.github.xudong7587.sunnytv

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.xudong7587.sunnytv.core.model.MediaEntry
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pixel and geometry checks: a border alone can no longer pass the shadow regression. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ShadowAlignmentTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var model: AppModel
    private val entries = (0..6).map { MediaEntry("$it", "fixture", "示例影片 $it", "Movie", year=2020+it) }
    private val selection = mutableIntStateOf(0)
    private val itemCount = mutableIntStateOf(7)
    private fun focus(tag:String) = rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
    private fun snapshot():Bitmap = rule.onNodeWithTag("visual-root").captureToImage().asAndroidBitmap()
        .copy(Bitmap.Config.ARGB_8888, false)
    private fun save(name:String, bitmap:Bitmap) {
        val dir=File(rule.activity.getExternalFilesDir(null), "visual-dev15").apply {mkdirs()}
        File(dir,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun mount(carousel:Boolean=false) {
        rule.activityRule.scenario.onActivity {activity ->
            model=AppModel(activity.application,false)
            model.saveSettings(model.settings.copy(darkTheme=false,reduceMotion=!carousel,shadowsEnabled=true))
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect { input.requestInputMode(InputMode.Keyboard) }
                CompositionLocalProvider(LocalAppModel provides model, LocalCompact provides false) {
                    SunnyTheme(model.settings) {
                        Box(Modifier.fillMaxSize().background(Color.White).testTag("visual-root")) {
                            if(carousel) RotatingHeroCards(entries.take(itemCount.intValue), selection.intValue,
                                {selection.intValue=it}, Modifier.padding(32.dp).width(700.dp), id="test-hero")
                            else FocusTile("shadow-card",Modifier.offset(70.dp,60.dp).size(230.dp,130.dp),
                                restoreFocus=false,onClick={}) { Box(Modifier.fillMaxSize().background(Color.White)) }
                            FocusTile("outside",Modifier.offset(530.dp,350.dp).size(48.dp),restoreFocus=false,onClick={}) {
                                Box(Modifier.fillMaxSize().background(Color.White))
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        focus("outside")
        rule.waitForIdle()
    }
    private fun meanBelow(bitmap:Bitmap, fromDp:Float, toDp:Float):Double {
        val root=rule.onNodeWithTag("visual-root").getUnclippedBoundsInRoot()
        val card=rule.onNodeWithTag("shadow-card").getUnclippedBoundsInRoot()
        val scale=bitmap.width/(root.right.value-root.left.value)
        val width=card.right.value-card.left.value
        val x0=((card.left.value-root.left.value+width*.3f)*scale).roundToInt()
        val x1=((card.left.value-root.left.value+width*.7f)*scale).roundToInt()
        val y0=((card.bottom.value-root.top.value+fromDp)*scale).roundToInt()
        val y1=((card.bottom.value-root.top.value+toDp)*scale).roundToInt()
        require(x0>=0 && y0>=0 && x1<bitmap.width && y1<bitmap.height && y1>y0)
        var sum=0.0; var n=0
        for(y in y0..y1) for(x in x0..x1) {
            val c=bitmap.getPixel(x,y)
            sum+=(.2126*AndroidColor.red(c)+.7152*AndroidColor.green(c)+.0722*AndroidColor.blue(c))/255.0
            n++
        }
        return sum/n
    }
    @Test fun shadowContainsVisiblePixelsOutsideTheCardAndSoftlyFades() {
        mount()
        val before=snapshot();save("shadow-unfocused.png",before)
        assertTrue(meanBelow(before,6f,12f)>.99)
        focus("shadow-card");rule.waitForIdle()
        rule.onNodeWithTag("shadow-card").assertIsFocused()
        val after=snapshot();save("shadow-focused.png",after)
        val near=meanBelow(after,6f,12f);val far=meanBelow(after,32f,36f)
        assertTrue("The actual outer shadow must visibly darken white; got $near",near<.94)
        assertTrue("Falloff must approach the original surface; near=$near far=$far",far>.99 && far-near>.06)
        File(rule.activity.getExternalFilesDir(null),"visual-dev15/pixel-metrics.txt")
            .writeText("nearShadowBrightness=$near\nfarSurfaceBrightness=$far\n")
        focus("outside");rule.waitForIdle()
        assertTrue("No stale shadow after focus leaves",meanBelow(snapshot(),6f,12f)>.99)
    }
    @Test fun shadowSwitchAndDarkModeRemainRespected() {
        mount()
        focus("shadow-card")
        rule.runOnIdle {model.saveSettings(model.settings.copy(shadowsEnabled=false))}
        rule.waitForIdle()
        assertTrue(meanBelow(snapshot(),6f,12f)>.99)
        rule.runOnIdle {model.saveSettings(model.settings.copy(shadowsEnabled=true,darkTheme=true))}
        rule.waitForIdle()
        assertTrue(meanBelow(snapshot(),6f,12f)>.99)
    }
    private fun assertAligned() {
        val main=rule.onNodeWithTag("test-hero:featured").getUnclippedBoundsInRoot()
        entries.take(itemCount.intValue).filter {it.id!=selection.intValue.toString()}.forEach {item ->
            val strip=rule.onNodeWithTag("test-hero:strip:${item.key}").getUnclippedBoundsInRoot()
            assertTrue("Top edge drift: main=$main strip=$strip",abs(main.top.value-strip.top.value)<.6f)
            assertTrue("Bottom edge drift: main=$main strip=$strip",abs(main.bottom.value-strip.bottom.value)<.6f)
            assertTrue(abs((main.bottom.value-main.top.value)-(strip.bottom.value-strip.top.value))<.6f)
        }
        assertTrue("Featured card remains square",abs((main.right.value-main.left.value)-(main.bottom.value-main.top.value))<.6f)
    }
    @Test fun heroAlignmentSurvivesFocusAnimationManualRotationAndUnfocusedRotation() {
        mount(carousel=true)
        assertAligned()
        rule.mainClock.autoAdvance=false
        focus("test-hero:featured")
        for(frame in 0..5) {rule.mainClock.advanceTimeBy(40);assertAligned()}
        rule.mainClock.autoAdvance=true
        for(i in 1..3) {
            rule.onNodeWithTag("test-hero:featured").performKeyInput {pressKey(Key.DirectionRight)}
            rule.waitForIdle();assertAligned()
        }
        focus("outside")
        rule.runOnIdle {selection.intValue=6}
        rule.waitForIdle();assertAligned()
        rule.runOnIdle {selection.intValue=0}
        rule.waitForIdle();assertAligned()
        save("carousel-aligned.png",snapshot())
    }
    @Test fun oneAndTwoItemCarouselsKeepTheirFixedSquareGeometry() {
        mount(carousel=true)
        for(count in listOf(1,2,7)) {
            rule.runOnIdle {selection.intValue=0;itemCount.intValue=count}
            rule.waitForIdle();assertAligned()
        }
    }
}
