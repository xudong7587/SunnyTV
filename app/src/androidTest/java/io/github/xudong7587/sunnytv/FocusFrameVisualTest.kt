package io.github.xudong7587.sunnytv

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.Route
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Visual checks for the two dev23 items: the focused frame must wrap the rounded corner and the
 * focus shadow must not be cut by the row that owns the card. Exports PNGs to the app cache dir
 * so they can be pulled and inspected; nothing here touches a real Emby source.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class FocusFrameVisualTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val entries=(0..9).map {MediaEntry("$it","fixture","测试影片 $it","Movie",year=2000+it,rating=7.0)}
    private val libraries=(0..8).map {MediaEntry("lib$it","fixture","测试媒体库 $it","CollectionFolder",isFolder=true)}
    private fun focus(tag:String)=rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
    private fun shoot(name:String) {
        rule.waitForIdle()
        val bitmap=rule.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(rule.activity.cacheDir,name).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun lightModel():AppModel {
        lateinit var model:AppModel
        rule.activityRule.scenario.onActivity {activity->
            val config=SourceConfig("fixture",SourceKind.EMBY,"测试来源","https://example.invalid","user")
            model=AppModel(activity.application,false,listOf(config))
            model.saveSettings(model.settings.copy(heroMode="latest",showNextUp=false,showResume=false,
                reduceMotion=true,darkTheme=false,shadowsEnabled=true))
            model.feeds[config.id]=HomeFeed(libraries=libraries,latest=entries)
            libraries.forEach {lib->model.libraryLatest[lib.key]=entries}
        }
        return model
    }

    /** The focus ring has to stay continuous around the rounded corner of a focused poster card. */
    @Test fun focusFrameOutlineStaysConcentricWithTheCardCorner() {
        // 132x198dp card with a 12dp corner at density 2 is 264x396px with a 24px corner radius.
        // The frame is stroked along the returned outline, so it is given half of the 4px stroke.
        val outline=concentricInsetOutline(RoundedCornerShape(12.dp),Size(264f,396f),2f,
            LayoutDirection.Ltr,Density(2f)) as Outline.Rounded
        val rect=outline.roundRect
        assertEquals("frame is inset by half the stroke",2f,rect.left,0.01f)
        // The frame's own corner radius shrinks with the inset; otherwise its arc no longer follows
        // the card's arc and a sliver of artwork stays outside the frame at the corner.
        assertEquals(22f,rect.topLeftCornerRadius.x,0.01f)
        assertEquals(22f,rect.topLeftCornerRadius.y,0.01f)
        assertEquals(22f,rect.bottomRightCornerRadius.x,0.01f)
    }

    @Test fun focusedCardFrameWrapsItsRoundedCorner() {
        val model=lightModel()
        rule.activityRule.scenario.onActivity {activity->
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {
                    Box(Modifier.fillMaxSize().background(Color(0xFFF4F5F2))) {
                        FocusTile("corner-probe",Modifier.size(132.dp,198.dp).align(androidx.compose.ui.Alignment.Center),
                            shape=RoundedCornerShape(12.dp),onClick={}) {
                            Box(Modifier.fillMaxSize().background(Color(0xFF20343A)))
                        }
                    }
                }}
            }
        }
        focus("corner-probe")
        rule.onNodeWithTag("corner-probe").assertIsFocused()
        shoot("dev23-frame-corner.png")
    }

    /** The grid poster in the first column keeps its whole focus shadow, corner included. */
    @Test fun libraryGridPosterKeepsItsFocusShadow() {
        lateinit var model:AppModel
        val library=MediaEntry("library","fixture","纪录片","CollectionFolder")
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false)
            model.saveSettings(model.settings.copy(darkTheme=false,shadowsEnabled=true,reduceMotion=true))
            model.pages[library.key]=MediaPage(entries,entries.size)
            model.navigate(Route.Library(library))
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {SunnyRoot {_,_->}}}
            }
        }
        // Item 0 is the banner, item 1 the tool row: the first poster is item 2.
        rule.onNodeWithTag("library:grid").performScrollToIndex(2)
        focus("library-slot:0")
        rule.onNodeWithTag("library-slot:0").assertIsFocused()
        shoot("dev23-grid-poster.png")
    }

    /** Home rows: the leftmost library block and the leftmost shelf block keep their soft shadow. */
    @Test fun homeLeftmostBlocksKeepTheirFocusShadow() {
        val model=lightModel()
        rule.activityRule.scenario.onActivity {activity->
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {SunnyRoot {_,_->}}}
            }
        }
        focus("nav:首页")
        rule.onNodeWithTag("nav:首页").performKeyInput {pressKey(Key.DirectionDown)}
        rule.onNodeWithTag("home-carousel:featured").performKeyInput {pressKey(Key.DirectionDown)}
        rule.waitUntil(8_000) {rule.onAllNodes(hasTestTag("library:fixture:lib0") and isFocused()).fetchSemanticsNodes().isNotEmpty()}
        assertSoftShadowLeftOf("library:fixture:lib0")
        shoot("dev23-home-library-row.png")
        rule.onNodeWithTag("library:fixture:lib0").performKeyInput {pressKey(Key.DirectionDown)}
        rule.waitUntil(8_000) {rule.onAllNodes(hasTestTag("latest:fixture:lib0:fixture:0") and isFocused()).fetchSemanticsNodes().isNotEmpty()}
        assertSoftShadowLeftOf("latest:fixture:lib0:fixture:0")
        shoot("dev23-home-shelf-row.png")
    }

    /** The row clips its own bounds, so a leftmost block must still show its soft shadow. */
    private fun assertSoftShadowLeftOf(tag:String) {
        val bitmap=rule.onRoot().captureToImage().asAndroidBitmap()
        val bounds=rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        val density=rule.density.density
        val left=(bounds.left.value*density).toInt()
        val midY=(((bounds.top.value+bounds.bottom.value)/2f)*density).toInt()
        fun luminance(x:Int,y:Int):Int {
            val p=bitmap.getPixel(x,y)
            return (((p shr 16) and 0xFF)*299+((p shr 8) and 0xFF)*587+(p and 0xFF)*114)/1000
        }
        val background=luminance((left-60).coerceAtLeast(0),midY)
        val shadow=(2..13).minOf {luminance(left-it,midY)}
        assertTrue("no soft shadow left of $tag: background=$background best=$shadow",background-shadow>6)
    }
}
