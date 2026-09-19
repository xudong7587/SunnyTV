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
    private fun showSourceForm(firstConnection:Boolean) {
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme {
                    AddSourceDialog(SourceKind.EMBY,onClose={},firstConnection=firstConnection)
                }}
            }
        }
    }
    @Test fun sourceImeConfirmationAdvancesAllFieldsAndReachesSave() {
        showSourceForm(true)
        val fields=listOf("source:name","source:address","source:username","source:password","source:save")
        fields.zipWithNext().forEach {(current,next)->
            focus(current)
            rule.onNodeWithTag(current).performTextReplacement(if(current=="source:address") "https://example.invalid/emby" else "test")
            rule.onNodeWithTag(current).performImeAction()
            rule.onNodeWithTag(next).assertIsFocused()
        }
    }
    @Test fun sourceRemoteReachesVisibleConfirmationAndAdvancesWithoutImeAction() {
        showSourceForm(false)
        focus("source:address")
        rule.onNodeWithTag("source:address").performKeyInput {pressKey(Key.DirectionDown)}
        rule.onNodeWithTag("source:address:confirm").assertIsFocused().performKeyInput {pressKey(Key.DirectionCenter)}
        rule.onNodeWithTag("source:username").assertIsFocused()
        focus("source:password")
        rule.onNodeWithTag("source:password").performKeyInput {pressKey(Key.DirectionDown)}
        rule.onNodeWithTag("source:password:confirm").assertIsFocused().performKeyInput {pressKey(Key.DirectionCenter)}
        rule.onNodeWithTag("source:save").assertIsFocused()
        rule.onNodeWithText("验证并保存").assertExists()
    }
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
    @Test fun detailPlaybackActionsMoveDownIntoEpisodeControls() {
        val series=MediaEntry("series-down","fixture","可继续播放的剧集","Series")
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            model.children[series.key]=listOf(MediaEntry("episode-1","fixture","第一集","Episode",seriesId=series.id,episode=1))
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme {
                    MediaDetailContent(series) {_,_->}
                }}
            }
        }
        focus("detail-resume")
        rule.onNodeWithTag("detail-resume").performKeyInput {pressKey(Key.DirectionDown)}
        rule.waitForIdle()
        rule.onNodeWithTag("episode-layout:horizontal").assertIsFocused()
    }
    @Test fun offscreenAccentRowsMoveDownThroughPartialRowInThemeCategory() {
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides true) {SunnyTheme {
                    Box(Modifier.width(560.dp).height(300.dp)) {SettingsScreen()}
                }}
            }
        }
        rule.onNodeWithTag("settings:主题").performClick()
        rule.onNodeWithTag("settings:viewport").performScrollToIndex(3)
        focus("accent:2")
        listOf("accent:5","accent:8","accent:9").fold("accent:2") {current,next->
            rule.onNodeWithTag(current).performKeyInput {pressKey(Key.DirectionDown)}
            rule.waitForIdle()
            rule.onNodeWithTag(next).assertIsFocused()
            next
        }
    }
    @Test fun posterGridTraversesOffscreenRowsPartialLastRowAndPagination() {
        val library=MediaEntry("poster-root","fixture","综艺","CollectionFolder",isFolder=true)
        val posters=(0..27).map {MediaEntry("poster-$it","fixture","节目 $it","Series")}
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            model.pages[library.key]=MediaPage(posters,40)
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides false) {SunnyTheme {
                    Box(Modifier.width(560.dp).height(280.dp)) {LibraryScreen(library) {_,_->}}
                }}
            }
        }
        rule.onNodeWithTag("library:grid").performScrollToIndex(1)
        focus("library-sort")
        rule.onNodeWithTag("library-sort").performKeyInput {pressKey(Key.DirectionDown)}
        (0..27 step 3).forEach {index->
            rule.onNodeWithTag("grid:fixture:poster-$index").assertIsFocused().performKeyInput {pressKey(Key.DirectionDown)}
        }
        rule.onNodeWithTag("library-more").assertIsFocused().performKeyInput {pressKey(Key.DirectionUp)}
        (27 downTo 0 step 3).forEach {index->
            rule.onNodeWithTag("grid:fixture:poster-$index").assertIsFocused().performKeyInput {pressKey(Key.DirectionUp)}
        }
        rule.onNodeWithTag("library-sort").assertIsFocused()
    }
    @Test fun shelfSortCyclesIndependentlyWithoutMovingItsButton() {
        val libraries=(0..1).map {MediaEntry("sort-$it","fixture","媒体库 $it","CollectionFolder",isFolder=true)}
        lateinit var model:AppModel
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false)
            libraries.forEach {model.libraryLatest[it.key]=entries}
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme {
                    Column {libraries.forEach {LibraryLatestRow(it)}}
                }}
            }
        }
        val tag="shelf-sort:${libraries.first().key}"
        focus(tag)
        val bounds=rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        listOf("最新上映","随机","最新入库").forEach {label->
            rule.onNodeWithTag(tag).performClick()
            rule.onNodeWithTag(tag).assertIsFocused().assertTextContains(label)
            assertEquals(bounds.top,rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top)
        }
        rule.onNodeWithTag("shelf-sort:${libraries.last().key}").assertContentDescriptionEquals("最新入库")
    }
    @Test fun folderAccordionMovesBetweenToolsHeadingsAndOffscreenRows() {
        val library=MediaEntry("folders-root","fixture","文件夹媒体库","CollectionFolder",isFolder=true)
        val folders=(0..2).map {MediaEntry("folder-$it","fixture","文件夹 $it","Folder",isFolder=true)}
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            model.pages[library.key]=MediaPage(emptyList(),0)
            model.folderPages[library.key]=MediaPage(folders,folders.size)
            folders.forEachIndexed {index,folder->model.folderPreviews[folder.key]=if(index==1) emptyList() else entries}
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides false) {SunnyTheme {
                    LibraryScreen(library) {_,_->}
                }}
            }
        }
        rule.onNodeWithTag("library:grid").performScrollToIndex(1)
        rule.onNodeWithTag("library-folder-mode").performClick()
        focus("library-sort")
        rule.onNodeWithTag("library-sort").performKeyInput {pressKey(Key.DirectionDown)}
        rule.onNodeWithTag("more:fixture:folder-0").assertIsFocused().performKeyInput {pressKey(Key.DirectionDown)}
        rule.onNodeWithTag("latest:fixture:folder-0:fixture:0").assertIsFocused().performKeyInput {pressKey(Key.DirectionRight)}
        rule.onNodeWithTag("latest:fixture:folder-0:fixture:1").assertIsFocused().performKeyInput {pressKey(Key.DirectionUp)}
        rule.onNodeWithTag("more:fixture:folder-0").assertIsFocused().performKeyInput {pressKey(Key.DirectionDown)}
        rule.onNodeWithTag("latest:fixture:folder-0:fixture:0").performKeyInput {pressKey(Key.DirectionDown)}
        rule.onNodeWithTag("more:fixture:folder-1").assertIsFocused().performKeyInput {pressKey(Key.DirectionDown)}
        rule.onNodeWithTag("more:fixture:folder-2").assertIsFocused().performKeyInput {pressKey(Key.DirectionUp)}
        rule.onNodeWithTag("more:fixture:folder-1").assertIsFocused().performKeyInput {pressKey(Key.DirectionUp)}
        rule.onNodeWithTag("more:fixture:folder-0").assertIsFocused().performKeyInput {pressKey(Key.DirectionUp)}
        rule.onNodeWithTag("library-sort").assertIsFocused()
    }
    @Test fun allFontSizeButtonsMoveDownToOffscreenFontSetting() {
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides true) {SunnyTheme {
                    Box(Modifier.width(560.dp).height(300.dp)) {SettingsScreen()}
                }}
            }
        }
        rule.onNodeWithTag("settings:字体").performClick()
        repeat(5) {index->
            rule.onNodeWithTag("settings:viewport").performScrollToNode(hasTestTag("font-size:$index"))
            focus("font-size:$index")
            rule.onNodeWithTag("font-size:$index").performKeyInput {pressKey(Key.DirectionDown)}
            rule.waitForIdle()
            rule.onNodeWithTag("setting:字体").assertIsFocused()
        }
    }
    @Test fun actorsMoveDownIntoOffscreenRecommendationsAndBackUp() {
        val movie=MediaEntry("cast-down","fixture","影片","Movie",people=listOf(MediaPerson("actor","演员","主演",null)))
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            model.similar[movie.key]=entries
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme {
                    Box(Modifier.width(900.dp).height(240.dp)) {MediaDetailContent(movie) {_,_->}}
                }}
            }
        }
        rule.onNodeWithTag("detail:viewport").performScrollToIndex(3)
        focus("person:fixture:actor")
        rule.onNodeWithTag("person:fixture:actor").performKeyInput {pressKey(Key.DirectionDown)}
        rule.waitForIdle()
        rule.onNodeWithTag("shelf:相似推荐:fixture:0").assertIsFocused().performKeyInput {pressKey(Key.DirectionUp)}
        rule.waitForIdle()
        rule.onNodeWithTag("person:fixture:actor").assertIsFocused()
    }
    @Test fun portraitHomeAndSetupRemainWithinViewport() {
        rule.activityRule.scenario.onActivity {it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        rule.waitUntil(5000) {rule.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT}
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides true) {SunnyTheme {
                    Box(Modifier.fillMaxSize()) {HomeHeroContent(entries[0],entries,0,{},emptyList(),{}, {_,_->})}
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
        // Dialog placement/IME transitions use Android's clock after the portrait activity rotates.
        rule.waitUntil(5_000) {rule.onNodeWithText("连接你的媒体库").isDisplayed()}
        rule.onNodeWithText("连接你的媒体库").assertIsDisplayed()
        rule.onNodeWithTag("source:save").assertExists()
    }

    @Test fun tvRowResetsItsSelectionWhenFocusLeaves() {
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
        focus("reset-row:fixture:0")
        focus("reset-row:fixture:1")
        rule.runOnIdle {assertEquals(1,selected)}
        focus("outside-row")
        rule.runOnIdle {assertEquals(0,selected)}
    }
    @Test fun detailAndLibraryDoNotExposeTopLeftBackButtons() {
        lateinit var model:AppModel
        val library=MediaEntry("library-back-test","fixture","测试媒体库","CollectionFolder",isFolder=true)
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false)
            model.pages[library.key]=MediaPage(entries,entries.size)
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme {
                    LibraryScreen(library) {_,_->}
                }}
            }
        }
        rule.onNodeWithTag("library-back").assertDoesNotExist()
        rule.activityRule.scenario.onActivity {activity->
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme {
                    MediaDetailContent(entries.first()) {_,_->}
                }}
            }
        }
        rule.onNodeWithTag("detail-back").assertDoesNotExist()
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

    @Test fun libraryHeaderCanMoveDownAndEscapeReturnsToParent() {
        lateinit var model:AppModel
        val library=MediaEntry("library","fixture","示例媒体库","CollectionFolder")
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false)
            model.pages[library.key]=MediaPage(entries,entries.size)
            model.navigate(Route.Library(library))
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme {SunnyRoot {_,_->}}}
            }
        }
        focus("hero:fixture:0")
        rule.onNodeWithTag("hero:fixture:0").performKeyInput {pressKey(Key.DirectionDown)}
        rule.onNodeWithTag("library-sort").assertIsFocused()
        rule.onNodeWithTag("library-sort").performKeyInput {pressKey(Key.Escape)}
        rule.runOnIdle {assertEquals(Route.Home,model.route)}
    }
    @Test fun episodeLayoutsSwitchWithoutChangingRealSettings() {
        lateinit var model:AppModel
        val season=MediaEntry("season","fixture","示例季","Season")
        val episodes=entries.mapIndexed {index,entry->entry.copy(type="Episode",episode=index+1,overview="完全虚构的单集简介")}
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false)
            model.pages[season.key]=MediaPage(episodes,episodes.size)
            activity.setContentForTest {
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme {LibraryScreen(season) {_,_->}}}
            }
        }
        rule.onNodeWithTag("episode-layout:vertical").performScrollTo().performClick()
        rule.runOnIdle {assertEquals("vertical",model.settings.episodeLayouts[season.key])}
        rule.onNodeWithTag("episode-layout:numbers").performClick()
        rule.runOnIdle {assertEquals("numbers",model.settings.episodeLayouts[season.key])}
        rule.onNodeWithTag("library:grid").performScrollToKey("fixture:0")
        rule.onNodeWithTag("episode:fixture:0").assertContentDescriptionEquals("第 1 集 · 媒体 0")
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
