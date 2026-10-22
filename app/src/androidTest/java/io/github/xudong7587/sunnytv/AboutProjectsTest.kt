package io.github.xudong7587.sunnytv

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class AboutProjectsTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun brandedProjectsHaveAdjacentWorkingLinksOnCompactAndTvLayouts() {
        val opened=mutableListOf<String>()
        for(compact in listOf(false,true)) for(dark in listOf(true,false)) {
            rule.activityRule.scenario.onActivity {activity ->
                val model=AppModel(activity.application,false,emptyList())
                model.saveSettings(model.settings.copy(darkTheme=dark))
                activity.setContent {CompositionLocalProvider(LocalAppModel provides model,
                    LocalCompact provides compact,LocalUriHandler provides object:UriHandler {
                        override fun openUri(uri:String) {opened+=uri}
                    }) {SunnyTheme(model.settings) {
                    Column(Modifier.width(if(compact) 360.dp else 820.dp).fillMaxHeight()
                        .background(SunnyColors.Background).verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement=Arrangement.spacedBy(20.dp)) {SunnyAbout();MediaIndexAbout()}
                }}}
            }
            rule.onNodeWithContentDescription("SunnyTV 项目标志").assertExists()
            rule.onNodeWithText("精致入眼，流畅入戏。").assertExists()
            rule.onNodeWithTag("about:sunnytv").performClick()
            rule.onNodeWithTag("about:mediaindex").performScrollTo().performClick()
            rule.onNodeWithContentDescription("MediaIndex 项目标志").assertExists()
            val title=rule.onNodeWithText("MediaIndex").fetchSemanticsNode().boundsInRoot
            val link=rule.onNodeWithTag("about:mediaindex").fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertTrue(link.left>=title.right)
            rule.onRoot().captureToImage().asAndroidBitmap().let {bitmap ->
                File(rule.activity.getExternalFilesDir(null),"about-$compact-$dark.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                }
            }
        }
        assertEquals(List(4) {listOf("https://github.com/xudong7587/SunnyTV","https://github.com/xudong7587/media-index")}.flatten(),opened)
    }
}
