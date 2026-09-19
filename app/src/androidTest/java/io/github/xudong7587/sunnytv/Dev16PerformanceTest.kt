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
        rule.onNodeWithTag("grid:fixture:0").assertIsFocused()
        val header=y("library:pinned-tools");val first=y("grid:fixture:0")
        key("grid:fixture:0",Key.DirectionDown);rule.waitForIdle()
        rule.onNodeWithTag("grid:fixture:4").assertIsFocused()
        assertEquals("A fully visible destination must not move the grid",first,y("grid:fixture:0"),1f)
        key("grid:fixture:4",Key.DirectionDown);rule.waitForIdle()
        rule.onNodeWithTag("grid:fixture:8").assertIsFocused()
        assertEquals("Header remains pinned",header,y("library:pinned-tools"),1f)
        val middle=y("grid:fixture:4")
        key("grid:fixture:8",Key.DirectionUp);rule.waitForIdle()
        assertEquals(middle,y("grid:fixture:4"),1f)
        key("grid:fixture:4",Key.DirectionUp);rule.waitForIdle()
        rule.onNodeWithTag("grid:fixture:0").assertIsFocused().assertIsDisplayed()
        assertEquals(header,y("library:pinned-tools"),1f)
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
        fun width(id:String)=rule.onNodeWithTag(id).getUnclippedBoundsInRoot().width.value
        assertEquals(width("portrait:fixture:1"),width("portrait:fixture:0"),.1f)
        assertEquals(rule.onNodeWithTag("portrait:viewport").getUnclippedBoundsInRoot().left.value,
            rule.onNodeWithTag("portrait:fixture:0").getUnclippedBoundsInRoot().left.value,.1f)
        focus("portrait:fixture:0");rule.waitForIdle()
        assertEquals(width("portrait:fixture:1"),width("portrait:fixture:0"),.1f)
    }
}
