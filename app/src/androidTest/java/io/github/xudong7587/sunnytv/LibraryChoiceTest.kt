package io.github.xudong7587.sunnytv

import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.storage.ConfigStore
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

@OptIn(ExperimentalTestApi::class)
class LibraryChoiceTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun glassShellSamplesPageAndChoicesKeepTheirFocusAndSelection() {
        assumeTrue(Build.VERSION.SDK_INT>=30)
        lateinit var model:AppModel
        var changed by mutableStateOf(false)
        var visible by mutableStateOf(true)
        var selected by mutableStateOf("ja")
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false,emptyList())
            model.saveSettings(AppSettings(appearanceMaterial="glass",appearanceCorner="round",highPerformanceThemes=true,performanceMode="standard",reduceMotion=true,darkTheme=false))
            activity.setContent {CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {
              val page=rememberGlassBackdrop(true)
              CompositionLocalProvider(LocalChoiceBackdrop provides page) {
               Box(Modifier.fillMaxSize().captureGlassBackdrop(page)) {
                GlassButtonScene {
                    Box(Modifier.fillMaxSize().captureButtonSceneBackdrop().background(Color.Red))
                    // Visible poster-wall foreground differs from the bottom artwork recording.
                    val posterColor=if(changed) Color(0xFF88BBDD) else Color(0xFFDDAA88)
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(posterColor)
                        drawRect(Color.White,Offset(size.width*.2f,size.height*.2f),Size(size.width*.12f,size.height*.6f))
                    }
                    if(visible) ChoiceDialog("默认音频",AudioPreference.choices,selected,onDismiss={},description="没有匹配音轨时跟随媒体默认。") {selected=it}
                }
               }
              }
            }}}
        }
        rule.onNodeWithTag("dialog:默认音频:ja").assertIsDisplayed().assertIsFocused()
        fun pixel():Int {val b=rule.onNodeWithTag("choice:panel").captureToImage().asAndroidBitmap();return b.getPixel(b.width/2,4.coerceAtMost(b.height-1))}
        rule.waitUntil(10_000) {val c=pixel();(c shr 8 and 255)>120 && (c and 255)>90 && (c shr 16 and 255)>150}
        val before=pixel()
        val outline=rule.onNodeWithTag("choice:panel").captureToImage().asAndroidBitmap()
        val density=rule.activity.resources.displayMetrics.density
        val cornerAlpha=outline.getPixel((6*density).toInt(),(22*density).toInt()) ushr 24
        assertTrue("Round mode must keep the button-sized corner rather than cutting deep into the panel",cornerAlpha>240)
        assertTrue("The foreground poster wall must cover the red bottom artwork in the lens",(before shr 8 and 255)>120 && (before and 255)>90)
        // A native dialog samples the current page when it opens. Reopen over a different poster
        // wall to check provenance, without assuming that separate windows share a redraw loop.
        rule.runOnIdle {visible=false;changed=true};rule.waitForIdle()
        rule.runOnIdle {visible=true};rule.waitForIdle()
        rule.waitUntil(10_000) {val c=pixel();c!=before && (c and 255)>(c shr 16 and 255)}
        assertNotEquals("Reopening must sample the current foreground poster wall",before,pixel())
        val screen=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val outside=screen.getPixel(8,40)
        for((shift,expected) in listOf(16 to 0x88,8 to 0xBB,0 to 0xDD)) {
            assertTrue("Opening a choice must not dim the page behind it",kotlin.math.abs(((outside shr shift) and 255)-expected)<=4)
        }
        screen.recycle()
        rule.onNodeWithTag("dialog:默认音频:ja").performKeyInput {pressKey(Key.DirectionDown)}
        rule.onNodeWithTag("dialog:默认音频:ko").assertIsFocused().performClick()
        rule.runOnIdle {assertEquals("ko",selected)}
        for(dark in listOf(false,true)) {
            rule.runOnIdle {model.saveSettings(model.settings.copy(darkTheme=dark))};rule.waitForIdle()
            val image=rule.onNodeWithTag("choice:panel").captureToImage().asAndroidBitmap()
            File(rule.activity.getExternalFilesDir(null),"choice-glass-$dark.png").outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            rule.onNodeWithTag("dialog:默认音频:cancel").assertIsDisplayed()
        }
    }
    @Test fun audioPreferencesPersistAndStayScopedToEachMedia() {
        val store=ConfigStore(rule.activity);val old=store.settings()
        try {
            store.saveSettings(old.copy(mediaAudioPreferences=mapOf("fixture:movies" to "cmn","fixture:series" to "ja")))
            val restored=ConfigStore(rule.activity).settings()
            assertEquals("cmn",restored.mediaAudioPreferences["fixture:movies"])
            assertEquals("ja",restored.mediaAudioPreferences["fixture:series"])
            assertNull(restored.mediaAudioPreferences["other:movies"])
        } finally {
            rule.activity.getSharedPreferences("sunny-config",0).edit().remove("mediaAudio:fixture:movies").remove("mediaAudio:fixture:series").commit()
            store.saveSettings(old)
        }
    }
    @Test fun mediaDetailSavesAudioChoiceAndLibrarySortKeepsAlignedOptionsAndFocus() {
        lateinit var model:AppModel
        val library=MediaEntry("fixture-library","fixture","示例电影","CollectionFolder",isFolder=true)
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false,emptyList())
            model.pages[library.key]=MediaPage(listOf(MediaEntry("film","fixture","示例影片","Movie")),1)
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                CompositionLocalProvider(LocalAppModel provides model,LocalPageKey provides "lib:${library.key}",
                    LocalCompact provides (LocalConfiguration.current.screenWidthDp<600)) {SunnyTheme(model.settings) {LibraryScreen(library) {_,_->}}}
            }
        }
        rule.onNodeWithTag("library:grid").performScrollToIndex(1)
        rule.onNodeWithTag("library-audio").assertDoesNotExist()
        fun mountDetail(entry:MediaEntry) {
            rule.activityRule.scenario.onActivity {activity->activity.setContent {
                CompositionLocalProvider(LocalAppModel provides model,LocalPageKey provides "detail:${entry.key}",
                    LocalCompact provides (LocalConfiguration.current.screenWidthDp<600)) {SunnyTheme(model.settings) {MediaDetailContent(entry) {_,_->}}}
            }}
        }
        for(type in listOf("Movie","Episode")) {
            mountDetail(MediaEntry("single-$type","fixture","单集资源",type))
            rule.onNodeWithTag("detail-audio-preference").assertDoesNotExist()
            rule.onNodeWithContentDescription("音轨").assertHasClickAction()
        }
        for(type in listOf("Series","Season")) {
            mountDetail(MediaEntry("multi-$type","fixture","多集资源",type))
            val button=rule.onNodeWithTag("detail-audio-preference").performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
            button.assertHasClickAction().assertContentDescriptionEquals("默认音频")
            rule.onNodeWithText("默认音频").assertDoesNotExist()
            val bounds=button.getUnclippedBoundsInRoot()
            assertTrue("Focused audio default stays icon-only",(bounds.right-bounds.left).value<=49f)
        }
        val film=MediaEntry("media-audio-series","fixture","示例剧集","Series")
        mountDetail(film)
        rule.onNodeWithTag("detail-audio-preference").performScrollTo().performClick()
        rule.onNodeWithTag("dialog:默认音频:en").performScrollTo().performClick()
        rule.runOnIdle {assertEquals("en",model.settings.mediaAudioPreferences[film.key]);assertNull(model.settings.mediaAudioPreferences[library.key])}
        rule.onNodeWithTag("detail-audio-preference").performClick()
        rule.onNodeWithTag("dialog:默认音频:en").assertIsDisplayed().assertIsFocused()
        rule.onNodeWithTag("dialog:默认音频:cancel").performClick()
        rule.activityRule.scenario.onActivity {activity->activity.setContent {
            CompositionLocalProvider(LocalAppModel provides model,LocalPageKey provides "lib:${library.key}",
                LocalCompact provides (LocalConfiguration.current.screenWidthDp<600)) {SunnyTheme(model.settings) {LibraryScreen(library) {_,_->}}}
        }}
        rule.onNodeWithTag("library:grid").performScrollToIndex(1)
        rule.onNodeWithTag("library-sort").performSemanticsAction(SemanticsActions.RequestFocus).performClick()
        Presentation.sorts.forEach {(key,_)->rule.onNodeWithTag("dialog:排序:$key").assertIsDisplayed()}
        val bounds=Presentation.sorts.map {rule.onNodeWithTag("dialog:排序:${it.first}").getUnclippedBoundsInRoot()}
        assertTrue("All sort tiles must have equal height",bounds.maxOf {(it.bottom-it.top).value}-bounds.minOf {(it.bottom-it.top).value}<1)
        assertTrue("All sort tiles must have equal width",bounds.maxOf {(it.right-it.left).value}-bounds.minOf {(it.right-it.left).value}<1)
        rule.onNodeWithTag("dialog:排序:Bitrate").performClick()
        rule.onNodeWithTag("library-sort").performClick()
        rule.onNodeWithTag("dialog:排序:Bitrate").assertIsFocused()
    }
}
