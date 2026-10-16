package io.github.xudong7587.sunnytv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.xudong7587.sunnytv.core.storage.ConfigStore
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class ExperimentalUpdateUiTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun updateCategoryFormAndActionsRemainReachableOnTv() {
        val store=ConfigStore(rule.activity);val previous=store.updateConfig();val auto=store.automaticUpdates();val enabled=store.webDavUpdatesEnabled()
        store.setWebDavUpdatesEnabled(false)
        try {
            rule.activityRule.scenario.onActivity {activity->
                val model=AppModel(activity.application,false,emptyList())
                activity.setContent {CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {Box(Modifier.fillMaxSize().background(SunnyColors.Background)) {SettingsScreen()}}}}
            }
            rule.onNodeWithTag("settings:categories").performScrollToNode(hasTestTag("settings:软件升级"))
            rule.onNodeWithTag("settings:软件升级").performClick()
            rule.onNodeWithTag("update:github").assertIsDisplayed()
            assertTrue(rule.onNodeWithTag("update:github").fetchSemanticsNode().boundsInRoot.top <
                rule.onNodeWithTag("update:webdav").fetchSemanticsNode().boundsInRoot.top)
            rule.onNodeWithTag("WebDAV 用户名").assertDoesNotExist()
            rule.onNodeWithTag("update:webdav").assertIsOff().performClick().assertIsOn()
            rule.onNodeWithTag("WebDAV 升级目录（包含 SunnyTV-updata 的完整 HTTP/HTTPS 地址）").performTextReplacement("https://fixture.invalid/dav/")
            rule.onNodeWithTag("WebDAV 用户名").performTextReplacement("fixture-user")
            rule.onNodeWithTag("WebDAV 密码").performScrollTo().performTextReplacement("fixture-password")
            rule.onNodeWithTag("update:save").performScrollTo().assertTextContains("保存设置").performClick()
            rule.onNodeWithText("设置已保存。下次打开 SunnyTV 会使用这份升级配置，密码已在本机加密保存。").assertIsDisplayed()
            rule.onNodeWithTag("dialog:ack").performClick()
            rule.runOnIdle {assertEquals(Triple("https://fixture.invalid/dav/","fixture-user","fixture-password"),store.updateConfig())}
            rule.onNodeWithTag("update:auto").performScrollTo().performClick()
            rule.runOnIdle {assertEquals(!auto,store.automaticUpdates())}
            rule.onNodeWithTag("update:check").performScrollTo().assertTextContains("检查更新").assertIsDisplayed()
            rule.onNodeWithTag("update:webdav").performScrollTo().performClick().assertIsOff()
            rule.onNodeWithTag("WebDAV 用户名").assertDoesNotExist()
            rule.onNodeWithTag("update:webdav").performScrollTo()
            val screenshot=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            java.io.File(rule.activity.cacheDir,"software-upgrade.png").outputStream().use {
                check(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
            }
            screenshot.recycle()
            rule.runOnIdle {assertEquals("fixture-user",store.updateConfig().second)}
            rule.onNodeWithTag("settings:categories").performScrollToNode(hasTestTag("settings:关于"))
            rule.onNodeWithTag("settings:关于").performClick()
            rule.onNodeWithTag("about:mediaindex").performScrollTo().assertTextContains("前往 GitHub 查看详情").assertIsDisplayed()
        } finally {store.setWebDavUpdatesEnabled(enabled);store.saveUpdateConfig(previous.first,previous.second,previous.third);store.setAutomaticUpdates(auto)}
    }
}
