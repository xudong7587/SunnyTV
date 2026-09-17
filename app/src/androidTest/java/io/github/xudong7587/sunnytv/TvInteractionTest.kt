package io.github.xudong7587.sunnytv

import androidx.compose.foundation.layout.*
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class TvInteractionTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val entries=(0..5).map {MediaEntry("$it","fixture","媒体 $it","Movie")}
    private fun focus(tag:String) {rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)}
    @Test fun heroRemoteNavigationWrapsAndTracksFocusDuringRapidReversal() {
        var selected=0
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application)
            activity.setContentForTest {
                var index by remember {mutableIntStateOf(0)}
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme {
                    AccordionCards(entries,index,{index=it;selected=it},Modifier.width(500.dp),hero=true,id="test-hero")
                }}
            }
        }
        focus("test-hero:fixture:0")
        rule.onNodeWithTag("test-hero:fixture:0").performKeyInput {pressKey(Key.DirectionLeft)}
        rule.onNodeWithTag("test-hero:fixture:5").assertIsFocused()
        rule.runOnIdle {assertEquals(5,selected)}
        rule.onNodeWithTag("test-hero:fixture:5").performKeyInput {pressKey(Key.DirectionRight)}
        rule.onNodeWithTag("test-hero:fixture:0").assertIsFocused()
        rule.runOnIdle {assertEquals(0,selected)}
    }
    @Test fun ordinaryRowEndsAtViewAllWithoutWrapping() {
        var opened=false
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application)
            activity.setContentForTest {
                var selected by remember {mutableIntStateOf(0)}
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme {
                    AccordionCards(entries.take(1),selected,{selected=it},Modifier.width(500.dp),id="test-row",onMore={opened=true})
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
            val model=AppModel(activity.application)
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
}

private fun MainActivity.setContentForTest(content:@Composable ()->Unit) {
    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContent {
        val input=LocalInputModeManager.current
        SideEffect { input.requestInputMode(InputMode.Keyboard) }
        content()
    }
}
