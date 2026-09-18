package io.github.xudong7587.sunnytv

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.Route
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Compose + D-pad regressions with synthetic metadata, never a user's NAS credentials. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class HomeHotfixTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val entries=(0..9).map {MediaEntry("$it","fixture","测试影片 $it","Movie",year=2000+it)}
    private val libraries=(0..8).map {MediaEntry("lib$it","fixture","测试媒体库 $it","CollectionFolder",isFolder=true)}
    private fun focus(tag:String)=rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
    private fun key(tag:String,key:Key)=rule.onNodeWithTag(tag).performKeyInput {pressKey(key)}
    private fun awaitFocused(tag:String) {
        rule.waitUntil(8_000) {
            rule.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag(tag).assertIsFocused().assertIsDisplayed()
    }
    private fun mount(nextUp:Boolean=false,emptyFirst:Boolean=false) {
        rule.activityRule.scenario.onActivity {activity->
            val config=SourceConfig("fixture",SourceKind.EMBY,"测试来源","https://example.invalid","user")
            val model=AppModel(activity.application,false,listOf(config))
            model.saveSettings(model.settings.copy(heroMode="latest",showNextUp=nextUp,showResume=false,
                reduceMotion=true,darkTheme=false,shadowsEnabled=true))
            model.feeds[config.id]=HomeFeed(libraries=libraries,latest=entries,nextUp=if(nextUp) entries.take(2) else emptyList())
            libraries.forEachIndexed {i,lib->model.libraryLatest[lib.key]=if(emptyFirst && i==0) emptyList() else entries}
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {SunnyRoot {_,_->}}}
            }
        }
        focus("nav:首页")
        key("nav:首页",Key.DirectionDown)
        awaitFocused("home-play")
        key("home-play",Key.DirectionDown)
        awaitFocused("library:fixture:lib0")
    }
    @Test fun downTraversesFourLazyShelvesAndUpRestoresLibrarySelection() {
        mount()
        key("library:fixture:lib0",Key.DirectionDown)
        awaitFocused("more:fixture:lib0")
        for (i in 0..3) {
            key("more:fixture:lib$i",Key.DirectionDown)
            awaitFocused("latest:fixture:lib$i:fixture:0")
            if(i<3) {
                key("latest:fixture:lib$i:fixture:0",Key.DirectionDown)
                awaitFocused("more:fixture:lib${i+1}")
            }
        }
        key("latest:fixture:lib3:fixture:0",Key.DirectionUp)
        awaitFocused("more:fixture:lib3")
        for(i in 3 downTo 1) {
            key("more:fixture:lib$i",Key.DirectionUp)
            awaitFocused("more:fixture:lib${i-1}")
        }
        key("more:fixture:lib0",Key.DirectionUp)
        awaitFocused("library:fixture:lib0")
    }
    @Test fun selectedThirdLibraryDoesNotJumpIntoTheFirstLibrary() {
        mount()
        key("library:fixture:lib0",Key.DirectionRight)
        awaitFocused("library:fixture:lib1")
        key("library:fixture:lib1",Key.DirectionRight)
        awaitFocused("library:fixture:lib2")
        key("library:fixture:lib2",Key.DirectionDown)
        awaitFocused("more:fixture:lib2")
        key("more:fixture:lib2",Key.DirectionDown)
        awaitFocused("latest:fixture:lib2:fixture:0")
        key("latest:fixture:lib2:fixture:0",Key.DirectionUp)
        awaitFocused("more:fixture:lib2")
        key("more:fixture:lib2",Key.DirectionUp)
        awaitFocused("library:fixture:lib2")
    }
    @Test fun nextUpIsReachableAndEmptyLatestDoesNotTrapDown() {
        mount(nextUp=true,emptyFirst=true)
        key("library:fixture:lib0",Key.DirectionDown)
        awaitFocused("shelf:接着看下一集:fixture:0")
        key("shelf:接着看下一集:fixture:0",Key.DirectionDown)
        awaitFocused("more:fixture:lib0")
        key("more:fixture:lib0",Key.DirectionDown)
        awaitFocused("more:fixture:lib1")
        key("more:fixture:lib1",Key.DirectionDown)
        awaitFocused("latest:fixture:lib1:fixture:0")
    }
    @Test fun horizontalLibraryEdgesKeepSpaceForFocusAndShadow() {
        mount()
        for(i in 0..8) {
            if(i>0) {key("library:fixture:lib${i-1}",Key.DirectionRight);awaitFocused("library:fixture:lib$i")}
            val box=rule.onNodeWithTag("library:fixture:lib$i").getUnclippedBoundsInRoot()
            val root=rule.onRoot().getUnclippedBoundsInRoot()
            assertTrue("Focused tile left edge needs a gutter", box.left-root.left>=20.dp)
            assertTrue("Focused tile right edge needs a gutter", root.right-box.right>=20.dp)
        }
        for(i in 8 downTo 1) {key("library:fixture:lib$i",Key.DirectionLeft);awaitFocused("library:fixture:lib${i-1}")}
    }
    @Test fun aboutPageOnlyDescribesSunnyTVAndKeepsItsVersion() {
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides false) {
                    SunnyTheme(model.settings) {SettingsScreen()}
                }
            }
        }
        rule.onNodeWithTag("settings:关于").performClick()
        rule.onNodeWithText("针对电视遥控器操作与流畅浏览设计。",substring=true).assertExists()
        rule.onAllNodes(hasText("Moonfin",substring=true,ignoreCase=true)).assertCountEquals(0)
        rule.onAllNodes(hasText("LumiPlayer",substring=true,ignoreCase=true)).assertCountEquals(0)
        rule.onNodeWithText(BuildConfig.VERSION_NAME,substring=true).assertExists()
    }
}
