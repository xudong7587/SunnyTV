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
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.storage.ConfigStore
import io.github.xudong7587.sunnytv.core.storage.FontLibrary
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.Route
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * dev27 fixes, one case per reported problem: the detail page's focus, the sort chooser, the
 * artwork-mode button, the search keyboard, and the fonts that must stay selectable after a public
 * build replaces a private one. Layouts are mounted TV-shaped, like the other interaction tests.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class Dev27UiTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val films=(0..39).map {MediaEntry("film-$it","fixture","影片 $it","Movie")}
    private fun focus(tag:String)=rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
    private fun key(tag:String,key:Key)=rule.onNodeWithTag(tag).performKeyInput {pressKey(key)}
    private fun bounds(tag:String)=rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    /** Focus requests that a page makes on its own arrive a few frames later, like on the device. */
    private fun awaitFocused(tag:String) {
        rule.waitUntil(8_000) {rule.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag(tag).assertIsFocused()
    }
    private fun mount(compact:Boolean=false,setup:(AppModel)->Unit={},content:@Composable (AppModel)->Unit) {
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            setup(model)
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides compact) {
                    SunnyTheme(model.settings) {Box(Modifier.fillMaxSize()) {content(model)}}
                }
            }
        }
    }
    private fun mountLibrary(library:MediaEntry):AppModel {
        lateinit var created:AppModel
        mount(setup={model->created=model;model.pages[library.key]=MediaPage(films,films.size)}) {
            Box(Modifier.width(1160.dp).height(700.dp)) {LibraryScreen(library) {_,_->}}
        }
        return created
    }

    /** Entering a detail page lands on 播放, and the pinned bar stays reachable both ways. */
    @Test fun detailPageFocusesThePlayButtonAndUpStillReachesThePinnedBar() {
        val film=films.first()
        lateinit var model:AppModel
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false)
            model.navigate(Route.Detail(film))
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {SunnyRoot {_,_->}}}
            }
        }
        rule.waitForIdle()
        awaitFocused("detail-play")
        // Up from the first row returns to the top bar instead of being swallowed by the row.
        key("detail-play",Key.DirectionUp)
        awaitFocused("nav:媒体库")
        // Down from the bar comes back to the play button, not to some other tile.
        key("nav:媒体库",Key.DirectionDown)
        awaitFocused("detail-play")
    }

    /** A series offers 继续播放 first and focuses it, with 从头播放 right after. */
    @Test fun seriesDetailFocusesResumeBeforeStart() {
        val series=MediaEntry("series","fixture","示例剧集","Series")
        mount {MediaDetailContent(series) {_,_->}}
        awaitFocused("detail-resume")
        assertTrue("继续播放 should sit before 从头播放",bounds("detail-resume").left<bounds("detail-start").left)
        assertTrue("both play actions should stay before 收藏",bounds("detail-start").left<bounds("detail-favorite").left)
    }

    /** The sort chooser shows all twelve keys at once and keeps focus on the chosen one. */
    @Test fun sortChooserShowsEveryKeyWithoutScrollingAndFocusesTheCurrentOne() {
        val library=MediaEntry("sort-lib","fixture","电影","CollectionFolder",isFolder=true)
        mountLibrary(library)
        rule.onNodeWithTag("library:grid").performScrollToIndex(1)
        focus("library-sort")
        rule.onNodeWithTag("library-sort").performClick()
        rule.waitForIdle()
        Presentation.sorts.forEach {(sortKey,_)->
            rule.onNodeWithTag("dialog:排序:$sortKey").assertIsDisplayed()
        }
        rule.onNodeWithTag("dialog:排序:Bitrate").performClick()
        rule.waitForIdle()
        focus("library-sort")
        rule.onNodeWithTag("library-sort").performClick()
        rule.waitForIdle()
        // 比特率 sits below 官方评级 in the list; the reopened dialog must still focus it.
        rule.onNodeWithTag("dialog:排序:Bitrate").assertIsFocused()
        rule.onNodeWithTag("dialog:排序:cancel").assertIsNotFocused()
    }

    /** 视图 walks 海报 → 背景 → 横幅 on repeated clicks; there is no submenu any more. */
    @Test fun artworkButtonCyclesThroughTheThreeModes() {
        val library=MediaEntry("view-lib","fixture","电影","CollectionFolder",isFolder=true)
        val model=mountLibrary(library)
        rule.onNodeWithTag("library:grid").performScrollToIndex(1)
        focus("library-view")
        rule.onNodeWithTag("library-view").assertContentDescriptionEquals("视图：海报")
        listOf("视图：背景","视图：横幅","视图：海报").forEach {expected->
            rule.onNodeWithTag("library-view").performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("library-view").assertContentDescriptionEquals(expected)
        }
        rule.runOnIdle {assertEquals("Poster",model.settings.libraryArtworkModes[library.key])}
    }

    /** The keyboard sits above the search bar, with 退格 / 清除 between letters and digits. */
    @Test fun searchKeyboardSitsAboveTheBarWithCorrectionsInTheMiddle() {
        mount {SearchScreen {_,_->}}
        rule.waitForIdle()
        assertTrue("keypad should sit above the search bar",bounds("key:A").bottom<bounds("搜索").top)
        assertTrue("退格 should stand between the letters and the digits",bounds("key:backspace").left>bounds("key:I").right)
        assertTrue("清除 should stand between the letters and the digits",bounds("key:clear").right<bounds("key:5").left)
    }

    /** A font file in the font folder is offered, can stay selected, and falls back when removed. */
    @Test fun fontFilesStaySelectableAndAFontFolderReloadKeepsTheChoice() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=File("/system/fonts/Roboto-Regular.ttf")
        assertTrue("font fixture missing",source.isFile)
        val folder=File(context.filesDir,"fonts").apply {mkdirs()}
        val copied=File(folder,"SunnyTVFixtureFont.ttf")
        source.copyTo(copied,overwrite=true)
        val id=FontLibrary.FILE_PREFIX+copied.name
        val store=ConfigStore(context)
        val original=store.settings()
        try {
            assertTrue("the font folder file should be listed",FontLibrary.catalog(context).any {it.id==id})
            assertEquals("SunnyTVFixtureFont",FontLibrary.label(copied.name))
            store.saveSettings(original.copy(fontChoice=id))
            assertEquals("a font that is on the device stays selected",id,ConfigStore(context).settings().fontChoice)
        } finally {
            store.saveSettings(original)
            copied.delete()
        }
        assertFalse("a removed font is no longer listed",FontLibrary.catalog(context).any {it.id==id})
        store.saveSettings(original.copy(fontChoice=id))
        assertEquals("a missing font falls back instead of breaking startup",FontCatalog.SYSTEM,
            ConfigStore(context).settings().fontChoice)
        store.saveSettings(original)
    }
}
