package io.github.xudong7587.sunnytv

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.Route
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Both halves of the banner <-> media-area switch have to be one controlled scroll. Going back to
 * the banner used to be revealed row by row, so the motion braked and re-accelerated halfway.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class BannerReturnMotionTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val shelf=(0..29).map {MediaEntry("shelf-$it","fixture","条目 $it","Movie")}
    private fun focus(tag:String)=rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
    private fun awaitFocused(tag:String)=rule.waitUntil(8_000) {
        rule.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
    }
    private fun mount() {
        val library=MediaEntry("library","fixture","纪录片","CollectionFolder")
        rule.activityRule.scenario.onActivity {activity->
            val model=AppModel(activity.application,false)
            model.saveSettings(model.settings.copy(darkTheme=true,shadowsEnabled=true,reduceMotion=false))
            model.pages[library.key]=MediaPage(shelf,shelf.size)
            model.navigate(Route.Library(library))
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {SunnyRoot {_,_->}}}
            }
        }
    }
    /** Frame-by-frame position of the tool row while travelling back up to the banner. */
    private fun sampleReturnTops():List<Float> {
        rule.mainClock.autoAdvance=false
        rule.onNodeWithTag("library-sort").performKeyInput {pressKey(Key.DirectionUp)}
        val tops=mutableListOf<Float>()
        repeat(44) {
            val nodes=rule.onAllNodes(hasTestTag("library:pinned-tools")).fetchSemanticsNodes()
            tops+=(if(nodes.isEmpty()) Float.NaN else nodes.first().boundsInRoot.top)
            rule.mainClock.advanceTimeBy(32)
        }
        rule.mainClock.autoAdvance=true
        return tops
    }
    @Test fun returningToTheBannerIsOneDeceleratingScroll() {
        mount()
        focus("library-carousel:featured")
        val heroTopBefore=rule.onNodeWithTag("library-carousel:featured").getUnclippedBoundsInRoot().top.value
        rule.onNodeWithTag("library-carousel:featured").performKeyInput {pressKey(Key.DirectionDown)}
        awaitFocused("library-sort")
        val tops=sampleReturnTops()
        rule.waitForIdle()
        val focusedNodes=rule.onAllNodes(isFocused()).fetchSemanticsNodes()
        val focusedTags=focusedNodes.mapNotNull {node->node.config.getOrElseNullable(
            androidx.compose.ui.semantics.SemanticsProperties.TestTag) {null}}
        // Per-frame travel: one decelerating animation never speeds up again after slowing down.
        val deltas=tops.zipWithNext {a,b->if(a.isNaN()||b.isNaN()) Float.NaN else b-a}.filter {!it.isNaN()}
        java.io.File(rule.activity.cacheDir,"dev23-banner-return.txt").writeText(
            "tops=${tops.map {if(it.isNaN()) -1f else it}}\ndeltas=$deltas\nfocused=$focusedTags\n")
        // One animation only: the travel must never come to a stop and then start again.
        val stoppedThenRestarted=deltas.withIndex().any {(index,delta)->
            delta<2f && deltas.drop(index+1).any {it>20f}
        }
        assertTrue("return scroll stops and restarts halfway: $deltas",!stoppedThenRestarted)
        rule.waitUntil(8_000) {!focusedTags.isEmpty()}
        assertTrue("focus stayed on the tool row: $focusedTags",
            focusedTags.none {it=="library-sort"})
        rule.onNodeWithTag("library:pinned-tools").assertIsNotDisplayed()
        val heroTopAfter=rule.onNodeWithTag("library-carousel:featured").getUnclippedBoundsInRoot().top.value
        assertEquals("banner is not back in its original position",heroTopBefore,heroTopAfter,1f)
    }
}
