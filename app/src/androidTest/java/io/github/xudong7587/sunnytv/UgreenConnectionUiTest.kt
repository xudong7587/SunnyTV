package io.github.xudong7587.sunnytv

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.storage.ConfigStore
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.Route
import io.github.xudong7587.sunnytv.feature.ui.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class UgreenConnectionUiTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()

    @Test fun settingsOffersDedicatedConnectionAndExplainsHowToEnableIt() {
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false,emptyList())
            activity.setContent {CompositionLocalProvider(LocalAppModel provides model) {
                SunnyTheme(model.settings) {SettingsScreen()}
            }}
        }
        rule.onNodeWithTag("source:add-emby").performScrollTo().assertTextContains("添加 Emby 服务",substring=true)
        rule.onNodeWithTag("source:add-ugreen").performScrollTo().assertTextContains("添加绿联影视服务",substring=true)
        rule.onNodeWithTag("source:reset").performScrollTo().assertTextContains("清除本地服务配置",substring=true)
        val services=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"ugreen-services.png").outputStream().use {
            check(services.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
        services.recycle()
        rule.onNodeWithTag("source:add-ugreen").performScrollTo().performClick()
        rule.onNode(hasText("添加绿联影视服务") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        rule.onNodeWithTag("ugreen:setup-steps").performScrollTo()
            .assertTextContains("设置 → 服务管理 → 媒体服务器",substring=true)
            .assertTextContains("媒体库专用账号",substring=true)
        rule.onNodeWithTag("ugreen:setup-help").assertDoesNotExist()
        rule.onNodeWithTag("source:type:ugreen").assertDoesNotExist()
        rule.onNodeWithTag("source:type:emby").assertDoesNotExist()
        rule.onNodeWithTag("ugreen:address-help").performScrollTo().assertTextContains("外部端口",substring=true)
        rule.onNodeWithTag("source:username").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("媒体库专用账号").assertIsDisplayed()
        rule.onNodeWithTag("source:password").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("source:save").performScrollTo().assertHasClickAction()
    }

    @Test fun firstConnectionChoosesServiceBeforeShowingThePlainForm() {
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false,emptyList())
            activity.setContent {CompositionLocalProvider(LocalAppModel provides model) {
                SunnyTheme(model.settings) {FirstSourceDialog {}}
            }}
        }
        rule.onNodeWithTag("first-source:ugreen").performClick()
        rule.onNodeWithTag("ugreen:setup-title").assertExists()
        rule.onNodeWithTag("source:type:ugreen").assertDoesNotExist()
        rule.onNodeWithTag("source:cancel").performScrollTo().performClick()
        rule.onNodeWithTag("first-source:emby").performClick()
        rule.onNodeWithTag("ugreen:setup-steps").assertDoesNotExist()
        rule.onNodeWithTag("source:name").assertExists()
    }

    @Test fun remoteCanReachEveryFieldInThePlainForm() {
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false,emptyList())
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides false) {
                SunnyTheme(model.settings) {AddSourceDialog(SourceKind.EMBY,{},initialProfile=EmbyProfile.UGREEN)}
            }}
        }
        rule.waitUntil(5_000) {rule.onAllNodes(hasTestTag("ugreen:setup-steps") and isFocused()).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("ugreen:setup-steps").assertIsFocused().assertIsDisplayed()
        val screenshot=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"ugreen-connection.png").outputStream().use {
            check(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
        screenshot.recycle()
        rule.onNodeWithTag("ugreen:setup-steps").performKeyInput {pressKey(Key.DirectionDown)}
        for(tag in listOf("source:name","source:address","source:username","source:password")) {
            rule.onNodeWithTag(tag).assertIsFocused().performKeyInput {pressKey(Key.DirectionDown)}
            rule.onNodeWithTag("$tag:confirm").assertIsFocused().performKeyInput {pressKey(Key.DirectionDown)}
        }
        rule.onNodeWithTag("source:save").assertIsFocused().assertIsDisplayed()
        rule.onNodeWithTag("source:save").performKeyInput {pressKey(Key.DirectionUp)}
        rule.onNodeWithTag("source:password").assertIsFocused()
    }

    @Test fun profilesRoundTripEncryptedStorageAndOlderSourcesRemainStandard() {
        val store=ConfigStore(rule.activity)
        val before=store.sources()
        try {
            val green=SourceConfig("fixture-green",SourceKind.EMBY,"Green","https://nas.example/emby/",
                "fixture-user","media-account","fixture-token",EmbyProfile.UGREEN,"fixture-server")
            val standard=SourceConfig("fixture-standard",SourceKind.EMBY,"Emby","https://emby.example/","u","","fixture-token-2")
            store.saveSources(listOf(green,standard))
            assertEquals(listOf(green,standard),store.sources())
            val prefs=rule.activity.getSharedPreferences("sunny-config",0)
            val json=JSONArray(prefs.getString("sources","[]"))
            json.getJSONObject(0).remove("embyProfile");json.getJSONObject(0).remove("serverId")
            json.getJSONObject(1).put("embyProfile","future-profile")
            prefs.edit().putString("sources",json.toString()).commit()
            val old=store.sources()
            assertEquals(EmbyProfile.STANDARD,old[0].embyProfile);assertEquals("",old[0].serverId)
            assertEquals(EmbyProfile.STANDARD,old[1].embyProfile)
            assertEquals("fixture-token",old[0].secret)
            assertFalse(prefs.getString("sources","")!!.contains("fixture-token"))
        } finally {store.saveSources(before)}
    }

    @Test fun greenSortMenuOffersOnlySupportedLocalMetadataChoices() {
        rule.activityRule.scenario.onActivity {activity->
            val source=SourceConfig("green",SourceKind.EMBY,"Green","https://example.invalid/emby/",embyProfile=EmbyProfile.UGREEN)
            val model=AppModel(activity.application,false,listOf(source))
            val library=MediaEntry("media-lib-1","green","测试媒体库","CollectionFolder",isFolder=true)
            model.pages[library.key]=MediaPage(listOf(MediaEntry("1","green","影片","Movie")),1)
            activity.setContent {CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides false) {
                SunnyTheme(model.settings) {LibraryScreen(library) {_,_->}}
            }}
        }
        rule.onNodeWithTag("library:grid").performScrollToIndex(1)
        rule.onNodeWithTag("library-sort").performClick()
        for((key,_) in MediaLogic.ugreenSorts) rule.onNodeWithTag("dialog:排序:$key").assertIsDisplayed()
        rule.onNodeWithTag("dialog:排序:DateCreated").assertDoesNotExist()
        rule.onNodeWithTag("dialog:排序:cancel").performClick()
        rule.onNodeWithTag("library-sort").performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput {pressKey(Key.DirectionDown)}
        rule.waitUntil(5_000) {rule.onAllNodes(hasTestTag("library-slot:0") and isFocused()).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("library-slot:0").assertIsFocused().assertIsDisplayed()
    }

    @Test fun greenDetailActionsFollowCapabilitiesAndCollectionsOpenAsLibraries() {
        val green=SourceConfig("green",SourceKind.EMBY,"Green","https://nas.example/emby/",embyProfile=EmbyProfile.UGREEN)
        val standard=SourceConfig("standard",SourceKind.EMBY,"Emby","https://emby.example/")
        val entry=mutableStateOf(MediaEntry("show-episode-1","green","测试单集","Episode"))
        lateinit var model:AppModel
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false,listOf(green,standard))
            activity.setContent {CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides false) {
                SunnyTheme(model.settings) {MediaDetailContent(entry.value) {_,_->}}
            }}
        }
        rule.onNodeWithTag("detail-play").assertExists()
        rule.onNodeWithTag("detail-favorite").assertDoesNotExist()
        rule.onNodeWithTag("detail-played").assertDoesNotExist()
        rule.onNodeWithText("查找字幕").assertDoesNotExist()
        rule.runOnIdle {entry.value=MediaEntry("7100","green","测试电影","Movie")}
        rule.onNodeWithTag("detail-favorite").assertExists()
        rule.onNodeWithTag("detail-played").assertExists()
        rule.runOnIdle {entry.value=MediaEntry("standard-episode","standard","Emby 单集","Episode")}
        rule.onNodeWithTag("detail-favorite").assertExists()
        rule.onNodeWithTag("detail-played").assertExists()
        rule.runOnIdle {
            val collection=MediaEntry("collection-tmdb-1","green","测试合集","Folder",isFolder=true)
            model.pages[collection.key]=MediaPage(emptyList(),0)
            model.navigate(Route.Detail(collection))
            assertEquals(Route.Library(collection),model.route)
        }
    }
}
