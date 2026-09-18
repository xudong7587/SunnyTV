package io.github.xudong7587.sunnytv

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
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

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class TvMotionTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val entries=(0..9).map {MediaEntry("$it","fixture","测试影片 $it","Movie",year=2000+it)}
    private fun focus(tag:String)=rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
    private fun key(tag:String,key:Key)=rule.onNodeWithTag(tag).performKeyInput {pressKey(key)}
    private fun bounds(tag:String)=rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun width(tag:String)=bounds(tag).let {(it.right-it.left).value}
    private fun mount(content:@Composable (AppModel)->Unit) {
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                val axis=remember {TvFocusMotion()}
                CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides false,LocalTvFocusMotion provides axis) {
                    SunnyTheme(model.settings) {Box(Modifier.fillMaxSize().onPreviewKeyEvent {axis.record(it);false}) {content(model)}}
                }
            }
        }
    }
    private fun shelf()=mount {
        var selected by remember {mutableIntStateOf(0)}
        AccordionCards(entries,selected,{selected=it},Modifier.width(840.dp),id="motion-shelf",onMore={})
    }
    @Test fun idleShelfCollapsesThenReturnsToFirstCardOnVerticalReentry() {
        mount {
            var selected by remember {mutableIntStateOf(0)}
            Column {
                Action("行外按钮",id="outside",onClick={})
                AccordionCards(entries,selected,{selected=it},Modifier.width(840.dp),id="idle",onMore={})
            }
        }
        focus("outside")
        assertEquals(110f,width("idle:fixture:0"),1f)
        key("outside",Key.DirectionDown)
        rule.onNodeWithTag("idle:fixture:0").assertIsFocused()
        assertTrue(width("idle:fixture:0")>290f)
        repeat(7) {key("idle:fixture:$it",Key.DirectionRight)}
        key("idle:fixture:7",Key.DirectionUp)
        rule.onNodeWithTag("outside").assertIsFocused()
        rule.waitForIdle()
        assertEquals(110f,width("idle:fixture:0"),1f)
        assertEquals(14f,bounds("idle:fixture:0").left.value,1f)
        key("outside",Key.DirectionDown)
        rule.onNodeWithTag("idle:fixture:0").assertIsFocused()
        assertTrue(width("idle:fixture:0")>290f)
    }
    @Test fun halfSpeedExpansionHasContinuousIntermediateWidthsAndStableVerticalPosition() {
        mount {
            var selected by remember {mutableIntStateOf(0)}
            CompositionLocalProvider(LocalMotion provides MotionTokens(.5f)) {
                AccordionCards(entries,selected,{selected=it},Modifier.width(840.dp),id="slow",onMore={})
            }
        }
        focus("slow:fixture:0")
        val y=bounds("slow:fixture:0").top.value
        rule.mainClock.autoAdvance=false
        key("slow:fixture:0",Key.DirectionRight)
        val widths=mutableListOf<Float>()
        repeat(30) {
            rule.mainClock.advanceTimeBy(16)
            widths+=width("slow:fixture:1")
            assertEquals(y,bounds("slow:fixture:1").top.value,.1f)
        }
        assertTrue("Slow motion must include intermediate layout widths",widths.distinct().size>12)
        assertTrue(widths.zipWithNext().all {(a,b)->b>=a-1f && b-a<20f})
        rule.mainClock.autoAdvance=true
        rule.onNodeWithTag("slow:fixture:1").assertIsFocused()
    }
    @Test fun playerLoadingUsesSunnyBrandWithoutLegacyMessageCard() {
        mount {
            io.github.xudong7587.sunnytv.feature.player.SunnyLoadingOverlay(true)
        }
        rule.onNodeWithTag("player:loading").assertIsDisplayed()
        rule.onNodeWithText("L O A D I N G").assertIsDisplayed()
    }

    @Test fun skipAndNextPromptsHaveRemoteConfirmActions() {
        var skipped=false
        var next=false
        mount {
            Column {
                io.github.xudong7587.sunnytv.feature.player.SkipSegmentPrompt(
                    SkipSegment("intro:0","intro",0,60_000),onSkip={skipped=true},onDismiss={})
                io.github.xudong7587.sunnytv.feature.player.NextEpisodePrompt(
                    "下一集",45,onPlay={next=true},onDismiss={})
            }
        }
        rule.onNodeWithTag("player:skip-prompt").assertIsDisplayed()
        rule.onNodeWithTag("player:skip").performClick()
        rule.runOnIdle {assertTrue(skipped)}
        rule.onNodeWithTag("player:next-prompt").assertIsDisplayed()
        rule.onNodeWithTag("player:next").performClick()
        rule.runOnIdle {assertTrue(next)}
    }

    @Test fun playerCircularControlsSupportDpadFocusLabelsAndActivation() {
        var clicks=0
        mount {
            Row(Modifier.padding(top=50.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                io.github.xudong7587.sunnytv.feature.player.PlayerControl("后退 10 秒","rewind",initial=true) {}
                io.github.xudong7587.sunnytv.feature.player.PlayerControl("播放","play") {clicks++}
                io.github.xudong7587.sunnytv.feature.player.PlayerControl("字幕","subtitle") {}
            }
        }
        rule.onNodeWithTag("player:rewind").assertIsFocused()
        key("player:rewind",Key.DirectionRight)
        rule.onNodeWithTag("player:play").assertIsFocused().assertContentDescriptionEquals("播放")
        key("player:play",Key.DirectionCenter)
        rule.runOnIdle {assertEquals(1,clicks)}
        key("player:play",Key.DirectionRight)
        rule.onNodeWithTag("player:subtitle").assertIsFocused()
    }
    @Test fun latestHeaderFocusRevealsItsShelfAndBottomSpace() {
        val library=MediaEntry("lib","fixture","测试媒体库","CollectionFolder",isFolder=true)
        mount {model->
            model.libraryLatest[library.key]=entries
            StableVerticalViewport {
                LazyColumn(Modifier.height(400.dp).width(840.dp).testTag("header-viewport")) {
                    item {Box(Modifier.height(330.dp)) {Action("上方",id="above",onClick={})}}
                    item {LibraryLatestRow(library)}
                    item {Spacer(Modifier.height(400.dp))}
                }
            }
        }
        focus("above")
        key("above",Key.DirectionDown)
        rule.onNodeWithTag("more:fixture:lib").assertIsFocused()
        val viewport=bounds("header-viewport")
        val shelf=bounds("latest:fixture:lib:viewport")
        assertTrue(shelf.bottom.value+13f<=viewport.bottom.value)
        assertTrue(bounds("more:fixture:lib").top>=viewport.top)
    }
    @Test fun adjacentExpansionKeepsOtherCardsAndAllVerticalEdgesFixedDuringEveryFrame() {
        shelf();focus("motion-shelf:fixture:0")
        val top=bounds("motion-shelf:fixture:0").top.value
        val thirdLeft=bounds("motion-shelf:fixture:2").left.value
        val sum=width("motion-shelf:fixture:0")+width("motion-shelf:fixture:1")
        rule.mainClock.autoAdvance=false
        key("motion-shelf:fixture:0",Key.DirectionRight)
        repeat(18) {
            rule.mainClock.advanceTimeBy(32)
            val a=bounds("motion-shelf:fixture:0");val b=bounds("motion-shelf:fixture:1")
            assertEquals(top,a.top.value,.1f);assertEquals(top,b.top.value,.1f)
            assertEquals(sum,(a.right-a.left).value+(b.right-b.left).value,1f)
            assertEquals(thirdLeft,bounds("motion-shelf:fixture:2").left.value,1f)
        }
        rule.mainClock.autoAdvance=true
        rule.onNodeWithTag("motion-shelf:fixture:1").assertIsFocused()
    }
    @Test fun edgeSlotStaysFixedAndViewAllPreservesTheFinalWideCardInBothDirections() {
        shelf();focus("motion-shelf:fixture:0")
        repeat(4) {key("motion-shelf:fixture:$it",Key.DirectionRight)}
        val edge=bounds("motion-shelf:fixture:4")
        for(index in 4..8) {
            key("motion-shelf:fixture:$index",Key.DirectionRight)
            rule.onNodeWithTag("motion-shelf:fixture:${index+1}").assertIsFocused()
            val next=bounds("motion-shelf:fixture:${index+1}")
            assertEquals(edge.left.value,next.left.value,1f)
            assertEquals(edge.top.value,next.top.value,.1f)
        }
        key("motion-shelf:fixture:9",Key.DirectionRight)
        rule.onNodeWithTag("motion-shelf:all").assertIsFocused().assertIsDisplayed()
        val last=bounds("motion-shelf:fixture:9")
        assertEquals((edge.right-edge.left).value,(last.right-last.left).value,1f)
        assertTrue(last.left<edge.left)
        key("motion-shelf:all",Key.DirectionLeft)
        rule.onNodeWithTag("motion-shelf:fixture:9").assertIsFocused()
        assertEquals(edge.left.value,bounds("motion-shelf:fixture:9").left.value,1f)
        for(index in 9 downTo 1) key("motion-shelf:fixture:$index",Key.DirectionLeft)
        rule.onNodeWithTag("motion-shelf:fixture:0").assertIsFocused()
        assertEquals(14f,bounds("motion-shelf:fixture:0").left.value,1f)
    }
    @Test fun anUnfinishedWidthAnimationCanReverseWithoutLosingFocusOrMovingY() {
        shelf();focus("motion-shelf:fixture:0")
        val top=bounds("motion-shelf:fixture:0").top.value
        rule.mainClock.autoAdvance=false
        key("motion-shelf:fixture:0",Key.DirectionRight)
        rule.mainClock.advanceTimeBy(80)
        key("motion-shelf:fixture:1",Key.DirectionLeft)
        rule.mainClock.advanceTimeBy(80)
        assertEquals(top,bounds("motion-shelf:fixture:0").top.value,.1f)
        rule.mainClock.autoAdvance=true
        rule.onNodeWithTag("motion-shelf:fixture:0").assertIsFocused()
    }
    @Test fun disabledAnimationsMoveDirectlyToTheFinalWidths() {
        mount {
            var selected by remember {mutableIntStateOf(0)}
            CompositionLocalProvider(LocalMotion provides MotionTokens(0f)) {
                AccordionCards(entries,selected,{selected=it},Modifier.width(840.dp),id="instant",onMore={})
            }
        }
        focus("instant:fixture:0")
        val wide=width("instant:fixture:0")
        rule.mainClock.autoAdvance=false
        key("instant:fixture:0",Key.DirectionRight)
        rule.mainClock.advanceTimeBy(48)
        assertEquals(wide,width("instant:fixture:1"),1f)
        assertEquals(110f,width("instant:fixture:0"),1f)
        rule.mainClock.autoAdvance=true
    }
    @Test fun pinnedHomeEntersAStationaryHeroThenMovesDownToStableLibraryRow() {
        rule.activityRule.scenario.onActivity {activity->
            val config=SourceConfig("fixture",SourceKind.EMBY,"测试来源","https://example.invalid","user")
            val model=AppModel(activity.application,false,listOf(config))
            model.saveSettings(model.settings.copy(heroMode="latest",showNextUp=false))
            val libraries=(0..3).map {MediaEntry("lib$it","fixture","测试媒体库 $it","CollectionFolder",isFolder=true)}
            model.feeds[config.id]=HomeFeed(libraries=libraries,resume=entries.take(2),latest=entries,warnings=listOf("接着看 暂时不可用","最新入库 暂时不可用"))
            libraries.forEach {model.libraryLatest[it.key]=entries}
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {SunnyRoot {_,_->}}}
            }
        }
        focus("nav:首页")
        val hero=bounds("home:hero")
        key("nav:首页",Key.DirectionDown)
        rule.onNodeWithTag("home-play").assertIsFocused()
        assertEquals(hero.top.value,bounds("home:hero").top.value,.1f)
        key("home-play",Key.DirectionRight)
        rule.onNodeWithTag("home-carousel:featured").assertIsFocused()
        key("home-carousel:featured",Key.DirectionRight)
        assertEquals(hero.top.value,bounds("home:hero").top.value,.1f)
        saveScreenshot("dev12-home-hero.png")
        repeat(7) {key("home-carousel:featured",Key.DirectionLeft)}
        rule.onNodeWithTag("home-carousel:featured").assertIsFocused()
        key("home-carousel:featured",Key.DirectionLeft)
        rule.onNodeWithTag("home-play").assertIsFocused()
        key("home-play",Key.DirectionDown)
        rule.onNodeWithTag("quick-resume:fixture:0").assertIsFocused()
        assertEquals(hero.top.value,bounds("home:hero").top.value,.1f)
        key("quick-resume:fixture:0",Key.DirectionDown)
        rule.onNodeWithTag("library:fixture:lib0").assertIsFocused()
        val y=bounds("library:fixture:lib0").top.value
        key("library:fixture:lib0",Key.DirectionRight)
        key("library:fixture:lib1",Key.DirectionRight)
        assertEquals(y,bounds("library:fixture:lib0").top.value,.1f)
        key("library:fixture:lib2",Key.DirectionLeft)
        assertEquals(y,bounds("library:fixture:lib0").top.value,.1f)
        rule.onNodeWithText("接着看 暂时不可用").assertDoesNotExist()
        rule.onNodeWithText("最新入库 暂时不可用").assertDoesNotExist()
        saveScreenshot("dev12-home-libraries.png")
    }
    @Test fun seriesOffersBothPlaybackActionsBeforeFavorite() {
        var played:Pair<String,Boolean>?=null
        mount {
            MediaDetailContent(MediaEntry("show","fixture","测试剧集","Series")) {entry,start->played=entry.id to start}
        }
        rule.onNodeWithTag("detail-start").performClick()
        rule.runOnIdle {assertEquals("show" to true,played)}
        rule.onNodeWithTag("detail-resume").performClick()
        rule.runOnIdle {assertEquals("show" to false,played)}
        assertTrue(bounds("detail-start").left<bounds("detail-favorite").left)
        assertTrue(bounds("detail-resume").left<bounds("detail-favorite").left)
        rule.onNodeWithTag("detail-played").assertDoesNotExist()
    }
    @Test fun seasonPlaybackActionsKeepTheSelectedSeason() {
        var played:Pair<MediaEntry,Boolean>?=null
        val season=MediaEntry("season2","fixture","第二季","Season",seriesId="show",season=2)
        mount {MediaDetailContent(season) {entry,start->played=entry to start}}
        rule.onNodeWithTag("detail-start").performClick()
        rule.runOnIdle {assertEquals(season to true,played)}
        rule.onNodeWithTag("detail-resume").performClick()
        rule.runOnIdle {assertEquals(season to false,played)}
        assertTrue(bounds("detail-resume").left<bounds("detail-favorite").left)
    }
    @Test fun rapidPageTransitionReversalDoesNotKeepOutgoingButtonsFocusable() {
        lateinit var model:AppModel
        mount {app->model=app;SunnyRoot {_,_->}}
        focus("nav:首页")
        rule.mainClock.autoAdvance=false
        rule.runOnIdle {model.navigate(Route.Detail(entries.first()))}
        rule.mainClock.advanceTimeBy(80)
        rule.onNodeWithTag("nav:首页").assertIsNotEnabled().assert(isFocused().not())
        rule.runOnIdle {model.navigate(Route.Detail(entries[1]));model.back();model.back()}
        rule.mainClock.advanceTimeBy(900)
        rule.mainClock.autoAdvance=true
        rule.runOnIdle {assertEquals(Route.Home,model.route)}
        rule.onNodeWithTag("nav:首页").assertExists()
        rule.onNodeWithTag("detail-play").assertDoesNotExist()
    }
    private fun saveScreenshot(name:String) {
        val bitmap=rule.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(rule.activity.cacheDir,name).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
}
