package io.github.xudong7587.sunnytv

import androidx.activity.ComponentActivity
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
            rule.setContent {UpdateSettings()}
            rule.onNodeWithTag("update:github").assertIsDisplayed()
            rule.onNodeWithTag("update:github-download").assertIsDisplayed()
            rule.onNodeWithTag("update:github-auto").assertIsOff().performClick().assertIsOn()
            assertTrue(store.githubAutomaticUpdates())
            for(tag in listOf("update:webdav","update:save","update:auto","update:check","WebDAV 用户名"))
                rule.onNodeWithTag(tag).assertDoesNotExist()
            assertEquals("fixture-user",store.updateConfig().second)
            assertTrue(store.webDavUpdatesEnabled());assertTrue(store.automaticUpdates())
        } finally {
            store.saveUpdateConfig(saved.first,saved.second,saved.third)
            store.setWebDavUpdatesEnabled(enabled);store.setAutomaticUpdates(auto);store.setGithubAutomaticUpdates(githubAuto)
        }
    }
}
