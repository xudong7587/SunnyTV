package io.github.xudong7587.sunnytv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.ui.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.xudong7587.sunnytv.core.storage.ConfigStore
import io.github.xudong7587.sunnytv.feature.update.UpdateSettings
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class ExperimentalUpdateUiTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun githubActionsAreVisibleWhileDavIsHiddenWithoutLosingConfiguration() {
        val store=ConfigStore(rule.activity)
        val saved=store.updateConfig();val enabled=store.webDavUpdatesEnabled();val auto=store.automaticUpdates();val githubAuto=store.githubAutomaticUpdates()
        try {
            store.saveUpdateConfig("https://fixture.invalid/dav/","fixture-user","fixture-password")
            store.setWebDavUpdatesEnabled(true);store.setAutomaticUpdates(true)
            store.setGithubAutomaticUpdates(false)
            val model=AppModel(rule.activity.application,false,emptyList())
            rule.setContent {CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {SettingsScreen()}}}
            rule.onNodeWithTag("settings:categories").performScrollToNode(hasTestTag("settings:关于"))
            rule.onNodeWithTag("settings:关于").performClick()
            rule.onNodeWithTag("settings:软件升级").assertDoesNotExist()
            rule.onNodeWithTag("update:github").assertIsDisplayed()
            val title=rule.onAllNodesWithText("SunnyTV").fetchSemanticsNodes().first {it.boundsInRoot.top>=0}
            assertTrue(title.boundsInRoot.top < rule.onNodeWithTag("update:github").fetchSemanticsNode().boundsInRoot.top)
            rule.onNodeWithTag("update:github-download").assertIsDisplayed()
            rule.onNodeWithTag("update:github-auto").assertIsOff().performClick().assertIsOn()
            assertTrue(store.githubAutomaticUpdates())
            for(tag in listOf("update:webdav","update:save","update:auto","update:check","WebDAV 用户名"))
                rule.onNodeWithTag(tag).assertDoesNotExist()
            rule.onNodeWithTag("settings:viewport").performScrollToNode(hasTestTag("about:mediaindex"))
            rule.onNodeWithTag("about:mediaindex").assertIsDisplayed()
            assertEquals("fixture-user",store.updateConfig().second)
            assertTrue(store.webDavUpdatesEnabled());assertTrue(store.automaticUpdates())
        } finally {
            store.saveUpdateConfig(saved.first,saved.second,saved.third)
            store.setWebDavUpdatesEnabled(enabled);store.setAutomaticUpdates(auto);store.setGithubAutomaticUpdates(githubAuto)
        }
    }
}
