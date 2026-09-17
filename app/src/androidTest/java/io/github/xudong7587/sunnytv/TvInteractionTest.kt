package io.github.xudong7587.sunnytv

import androidx.compose.foundation.layout.*
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import android.content.pm.ActivityInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.Route
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class TvInteractionTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val entries=(0..5).map {MediaEntry("$it","fixture","媒体 $it","Movie")}
    private fun focus(tag:String) {rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)}
    @Test fun heroRemoteNavigationWrapsAndTracksFocusDuringRapidReversal() {
        var selected=0
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            activity.setContentForTest {
                var index by remember {mutableIntStateOf(0)}
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme {
                    RotatingHeroCards(entries,index,{index=it;selected=it},Modifier.fillMaxWidth(),id="test-hero")
                }}
            }
        }
        focus("test-hero:featured")
        rule.onNodeWithTag("test-hero:featured").performKeyInput {pressKey(Key.DirectionLeft)}
        rule.onNodeWithTag("test-hero:featured").assertIsFocused()
        rule.runOnIdle {assertEquals(5,selected)}
        rule.onNodeWithTag("test-hero:featured").performKeyInput {pressKey(Key.DirectionRight)}
        rule.onNodeWithTag("test-hero:featured").assertIsFocused()
        rule.runOnIdle {assertEquals(0,selected)}
    }
    @Test fun ordinaryRowEndsAtViewAllWithoutWrapping() {
        var opened=false
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            activity.setContentForTest {
                var selected by remember {mutableIntStateOf(0)}
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme {
                    AccordionCards(entries.take(1),selected,{selected=it},Modifier.fillMaxWidth(),id="test-row",onMore={opened=true})
                }}
            }
        }
        focus("test-row:all")
        rule.onNodeWithTag("test-row:all").performKeyInput {pressKey(Key.DirectionRight)}
        rule.onNodeWithTag("test-row:all").assertIsFocused()
        rule.onNodeWithTag("test-row:all").performClick()
        rule.runOnIdle {assertTrue(opened)}
    }
    @Test fun actionLabelsAppearOnFocusAndRemainAccessibleAsIcons() {
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme {
                    Row {Action("字幕",id="sub") {};Action("音频",id="audio") {}}
                }}
            }
        }
        focus("sub")
        rule.onNodeWithTag("sub").assertContentDescriptionEquals("字幕")
        rule.onNodeWithText("字幕").assertIsDisplayed()
        focus("audio")
        rule.onNodeWithText("音频").assertIsDisplayed()
        rule.onNodeWithTag("sub").assertContentDescriptionEquals("字幕")
    }
    @Test fun pinnedNavigationMovesDownIntoSettingsAndLibraryContent() {
        lateinit var model:AppModel
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false)
            model.navigate(Route.Settings)
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme {SunnyRoot {_,_->error("Unexpected playback")}}}
            }
        }
        focus("nav:设置")
        rule.onNodeWithTag("nav:设置").performKeyInput {pressKey(Key.DirectionDown)}
        rule.onNodeWithTag("settings:媒体来源").assertIsFocused()
        focus("nav:媒体库")
        rule.onNodeWithTag("nav:媒体库").performClick()
        rule.onNodeWithTag("nav:媒体库").performKeyInput {pressKey(Key.DirectionDown)}
        rule.onNodeWithTag("管理来源").assertIsFocused()
    }
    @Test fun collapsedActionsAreCircularAndConfirmationUsesCheckmark() {
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme {
                    Row {Action("验证并保存",id="save") {};Action("取消",id="cancel") {}}
                }}
            }
        }
        focus("cancel")
        rule.onNodeWithTag("save").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        assertEquals("check",actionIcon("验证并保存"))
        assertEquals("check",actionIcon("确认移除"))
    }
    @Test fun seriesDetailCannotExposeBulkPlayedAction() {
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme {
                    MediaDetailContent(MediaEntry("series","fixture","纪录片","Series")) {_,_->error("Unexpected playback")}
                }}
            }
        }
        rule.onNodeWithTag("detail-played").assertDoesNotExist()
        rule.onNodeWithTag("detail-favorite").assertExists()
    }
    @Test fun portraitHomeAndSetupRemainWithinViewport() {
        rule.activityRule.scenario.onActivity {it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides true) {SunnyTheme {
                    Box(Modifier.fillMaxSize()) {HomeHeroContent(entries[0],entries,0,{},emptyList(),false,{}, {_,_->})}
                }}
            }
        }
        rule.onNodeWithTag("home-carousel:featured").assertIsDisplayed()
        val square=rule.onNodeWithTag("home-carousel:featured").fetchSemanticsNode().boundsInRoot
        assertEquals(square.width,square.height,1f)
        saveScreenshot("portrait-home.png")
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides true) {SunnyTheme {
                    AddSourceDialog(SourceKind.EMBY,onClose={},firstConnection=true)
                }}
            }
        }
        rule.onNodeWithText("连接你的媒体库").assertIsDisplayed()
        rule.onNodeWithTag("验证并保存").assertExists()
    }

    @Test fun rowRestoresFirstCardWhenFocusLeaves() {
        var selected=-1
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            activity.setContentForTest {
                var index by remember {mutableIntStateOf(0)}
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme {
                    Column {
                        AccordionCards(entries,index,{index=it;selected=it},Modifier.fillMaxWidth(),id="reset-row")
                        Action("离开",id="outside-row") {}
                    }
                }}
            }
        }
        focus("reset-row:fixture:1")
        rule.runOnIdle {assertEquals(1,selected)}
        focus("outside-row")
        rule.runOnIdle {assertEquals(0,selected)}
    }
    @Test fun detailHidesPinnedNavigation() {
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            model.navigate(Route.Detail(entries.first()))
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme {SunnyRoot {_,_->}}}
            }
        }
        rule.onNodeWithTag("nav:首页").assertDoesNotExist()
        rule.onNodeWithTag("detail-play").assertExists()
    }
    private fun saveScreenshot(name:String) {
        val bitmap=rule.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(rule.activity.cacheDir,name).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
}

private fun ComponentActivity.setContentForTest(content:@Composable ()->Unit) {
    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContent {
        val input=LocalInputModeManager.current
        SideEffect { input.requestInputMode(InputMode.Keyboard) }
        content()
    }
}
