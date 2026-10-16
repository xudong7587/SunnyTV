package io.github.xudong7587.sunnytv

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.ui.*
import io.github.xudong7587.sunnytv.feature.update.GithubUpdatePanel
import io.github.xudong7587.sunnytv.feature.update.GithubUpdates
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class GithubUpdatePanelTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun checksAutomaticallyShowsProgressAndInstallsWithoutAnotherMenu() {
        val finish=CompletableDeferred<Unit>()
        var installs=0
        val model=AppModel(rule.activity.application,false,emptyList())
        rule.setContent {CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {
            GithubUpdatePanel(latest={GithubUpdates.Release("9.0.0",9999,"","")},
                downloadOverride={_,progress->progress(50);finish.await();File("verified.apk")},
                installOverride={installs++})
        }}}
        rule.waitUntil(5000) {rule.onAllNodesWithText("已发现最新版 9.0.0",substring=true).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("update:github-download").performClick()
        rule.onNodeWithText("正在下载 50%",substring=true).assertExists()
        rule.onNodeWithTag("update:github-download").performClick()
        rule.runOnIdle {assertEquals(0,installs);finish.complete(Unit)}
        rule.waitUntil(5000) {installs==1}
        rule.onNodeWithTag("update:github").assertExists()
        rule.onNodeWithTag("update:github-download").assertExists()
    }
}
