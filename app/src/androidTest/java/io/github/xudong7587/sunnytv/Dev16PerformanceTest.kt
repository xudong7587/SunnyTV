package io.github.xudong7587.sunnytv

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
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
class Dev16PerformanceTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val entries=(0..31).map {MediaEntry("$it","fixture","影片 $it","Movie")}
    private fun focus(id:String)=rule.onNodeWithTag(id).performSemanticsAction(SemanticsActions.RequestFocus)
    private fun key(id:String,key:Key)=rule.onNodeWithTag(id).performKeyInput {pressKey(key)}
    private fun y(id:String)=rule.onNodeWithTag(id).getUnclippedBoundsInRoot().top.value
    @Test fun visibleGridRowsStayStillAndHeaderStaysPinnedAcrossBoundary() {
        val lib=MediaEntry("grid","fixture","媒体库","CollectionFolder",isFolder=true)
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            model.pages[lib.key]=MediaPage(entries,entries.size)
            model.saveSettings(model.settings.copy(reduceMotion=true,darkTheme=false))
            activity.setContent {
                SideEffect { }
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides false,LocalNavVisible provides false) {
                    SunnyTheme(model.settings) {Box(Modifier.width(660.dp).height(700.dp)) {LibraryScreen(lib) {_,_->}}}
                }
            }
        }
        rule.onNodeWithTag("library:grid").performScrollToIndex(1)
        focus("library-sort");key("library-sort",Key.DirectionDown);rule.waitForIdle()
        // 660dp minus 60dp side padding fits four 132dp cells.
        // dev20 replaced the poster wall with stable virtual slots: tags are library-slot:N.
        rule.onNodeWithTag("library-slot:0").assertIsFocused()
        // dev22: the title/tool row scrolls with the wall instead of staying pinned, so the
        // assertions below only track the grid itself.
        val first=y("library-slot:0")
        key("library-slot:0",Key.DirectionDown);rule.waitForIdle()
        rule.onNodeWithTag("library-slot:4").assertIsFocused()
        assertEquals("A fully visible destination must not move the grid",first,y("library-slot:0"),1f)
        key("library-slot:4",Key.DirectionDown);rule.waitForIdle()
        rule.onNodeWithTag("library-slot:8").assertIsFocused()
        val middle=y("library-slot:4")
        key("library-slot:8",Key.DirectionUp);rule.waitForIdle()
        assertEquals(middle,y("library-slot:4"),1f)
        key("library-slot:4",Key.DirectionUp);rule.waitForIdle()
        rule.onNodeWithTag("library-slot:0").assertIsFocused().assertIsDisplayed()
    }
    @Test fun portraitFirstPosterIsNarrowAndLeftAlignedEvenWhenFocused() {
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            activity.setContent {
                CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides true) {SunnyTheme {
                    var selected by remember {mutableIntStateOf(0)}
                    Box(Modifier.width(400.dp).testTag("portrait:viewport")) {
                        AccordionCards(entries,selected,{selected=it},id="portrait")
                    }
                }}
            }
        }
        // DpRect has no composable width extension in this Compose test version: subtract explicitly.
        fun width(id:String):Float {
            val bounds=rule.onNodeWithTag(id).getUnclippedBoundsInRoot()
            return (bounds.right-bounds.left).value
        }
        assertEquals(width("portrait:fixture:1"),width("portrait:fixture:0"),.1f)
        // dev23: the row keeps its focus-shadow gutter inside its own bounds, so the first block
        // starts one gutter in from the viewport edge instead of flush against it.
        assertEquals(rule.onNodeWithTag("portrait:viewport").getUnclippedBoundsInRoot().left.value+
            FocusShadowGutter.value,
            rule.onNodeWithTag("portrait:fixture:0").getUnclippedBoundsInRoot().left.value,.1f)
        focus("portrait:fixture:0");rule.waitForIdle()
        assertEquals(width("portrait:fixture:1"),width("portrait:fixture:0"),.1f)
    }
}
