package io.github.xudong7587.sunnytv

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HomeNavigationEntryTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val films=(0..6).map {MediaEntry("$it","fixture","测试影片 $it","Movie")}
    private fun mount(resume:Boolean=true,showResume:Boolean=true) {
        rule.activityRule.scenario.onActivity {activity->
            val source=SourceConfig("fixture",SourceKind.EMBY,"Fixture","https://example.invalid/","user")
            val model=AppModel(activity.application,false,listOf(source))
            model.saveSettings(model.settings.copy(heroMode="latest",showResume=showResume,showNextUp=false,reduceMotion=true))
            val library=MediaEntry("lib","fixture","测试媒体库","CollectionFolder",isFolder=true)
            model.feeds[source.id]=HomeFeed(libraries=listOf(library),latest=films,resume=if(resume) films.take(2) else emptyList())
            model.libraryLatest[library.key]=films
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides false,LocalHandset provides false,LocalTouchFirst provides false) {
                    SunnyTheme(model.settings) {SunnyRoot {_,_->}}
                }
            }
        }
    }
    private fun focus(tag:String)=rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
    private fun key(tag:String,key:Key)=rule.onNodeWithTag(tag).performKeyInput {pressKey(key)}
    private fun await(tag:String) {
        rule.waitUntil(8_000) {rule.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag(tag).assertIsFocused().assertIsDisplayed()
    }
    @Test fun topBarDownPrefersFirstContinueShortcutFromEveryTopButton() {
        mount()
        for(label in listOf("首页","媒体库","搜索","设置")) {
            focus("nav:$label");key("nav:$label",Key.DirectionDown)
            await("quick-resume:fixture:0")
        }
    }
    @Test fun emptyResumeKeepsTheCarouselFallback() {
        mount(resume=false)
        focus("nav:首页");key("nav:首页",Key.DirectionDown)
        await("home-carousel:featured")
    }
    @Test fun hiddenResumeKeepsTheCarouselFallback() {
        mount(showResume=false)
        focus("nav:首页");key("nav:首页",Key.DirectionDown)
        await("home-carousel:featured")
        rule.onAllNodes(hasTestTag("quick-resume:fixture:0")).assertCountEquals(0)
    }
    @Test fun carouselHorizontalCycleAndMediaAreaReturnStayUnchanged() {
        mount()
        focus("nav:首页");key("nav:首页",Key.DirectionDown);await("quick-resume:fixture:0")
        key("quick-resume:fixture:0",Key.DirectionRight);await("quick-resume:fixture:1")
        key("quick-resume:fixture:1",Key.DirectionRight);await("home-carousel:featured")
        repeat(films.size) {
            key("home-carousel:featured",Key.DirectionLeft);await("home-carousel:featured")
        }
        key("home-carousel:featured",Key.DirectionLeft);await("quick-resume:fixture:0")
        key("quick-resume:fixture:0",Key.DirectionDown);await("library:fixture:lib")
        key("library:fixture:lib",Key.DirectionUp);await("home-carousel:featured")
    }
}
