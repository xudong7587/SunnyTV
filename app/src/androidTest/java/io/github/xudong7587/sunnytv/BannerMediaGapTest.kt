package io.github.xudong7587.sunnytv

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.Route
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The banner -> media-area gap on phones/tablets. The tool row used to carry the pinned bar's full
 * 82dp inset on every device, which is dead space when the page simply scrolls.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class BannerMediaGapTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val shelf=(0..29).map {MediaEntry("shelf-$it","fixture","条目 $it","Movie")}
    private fun bounds(tag:String)=rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun shoot(name:String) {
        val bitmap=rule.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(rule.activity.cacheDir,name).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun mount(compact:Boolean,handset:Boolean,size:Pair<Int,Int>) {
        val library=MediaEntry("library","fixture","地球知识局","CollectionFolder")
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            model.saveSettings(model.settings.copy(darkTheme=true,shadowsEnabled=true,reduceMotion=true))
            model.pages[library.key]=MediaPage(shelf,shelf.size)
            model.recommendations[library.key]=shelf.take(7)
            model.navigate(Route.Library(library))
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                CompositionLocalProvider(LocalAppModel provides model,LocalHandset provides handset,
                    LocalTouchFirst provides handset,LocalCompact provides compact,LocalTopInset provides 0.dp) {
                    SunnyTheme(model.settings) {
                        Box(Modifier.size(size.first.dp,size.second.dp).background(Color.Black)) {
                            LibraryScreen(library) {_,_->}
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }
    /** The spacer between the banner copy and the tool row: hero bottom padding + row top inset. */
    private fun gapDp():Float {
        rule.onNodeWithTag("library:grid").performScrollToIndex(1)
        rule.waitForIdle()
        val card=bounds("library-carousel:featured")
        val tools=bounds("library:pinned-tools")
        return tools.top.value-card.bottom.value
    }
    @Test fun phoneAndTabletKeepTheBannerCloseToTheMediaAreaWhileTelevisionIsUnchanged() {
        mount(compact=false,handset=true,size=1024 to 465)
        val landscape=gapDp()
        shoot("dev25-gap-phone-landscape.png")
        mount(compact=true,handset=true,size=465 to 1024)
        val portrait=gapDp()
        shoot("dev25-gap-phone-portrait.png")
        mount(compact=false,handset=false,size=960 to 540)
        val tv=gapDp()
        shoot("dev25-gap-tv.png")
        java.io.File(rule.activity.cacheDir,"dev25-gap.txt").writeText(
            "phoneLandscape=${landscape}dp phonePortrait=${portrait}dp tv=${tv}dp\n")
        // Touch: hero bottom margin (10dp) + grid spacing (22dp) + tool row inset (8dp).
        assertTrue("phone landscape gap still too large: $landscape",landscape<=45f)
        assertTrue("phone portrait gap still too large: $portrait",portrait<=45f)
        // Television keeps the exact geometry it was accepted with: 18 + 22 + 82.
        assertEquals("television geometry changed",122f,tv,0.5f)
    }
}
