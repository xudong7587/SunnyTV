package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import io.github.xudong7587.sunnytv.core.model.ViewportPolicy
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import io.github.xudong7587.sunnytv.core.model.HomeFocusPlan
import io.github.xudong7587.sunnytv.core.model.HomeFocusSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Registered focus targets are composed targets, never requesters for off-screen lazy items. */
@Stable
class HomeFocusNavigator(private val list: LazyListState, private val scope: CoroutineScope,
                         private val axis: TvFocusMotion, private val lazyIndexOffset: Int = 0) {
    private data class Target(val section: String, val requester: FocusRequester)
    private val targets = linkedMapOf<String, Target>()
    private val returnSections = mutableMapOf<String, String>()
    var sections: List<HomeFocusSection> = emptyList()
    var moving by mutableStateOf(false)
        private set
    private var focusedId: String? = null
    private var libraryId: String? = null
    private val bounds = mutableMapOf<String, Rect>()
    private val regions = mutableMapOf<String, Rect>()
    var viewport = Rect.Zero
    fun placed(id: String, rect: Rect) { bounds[id] = rect }
    fun region(key: String, rect: Rect?) { if (rect == null) regions.remove(key) else regions[key] = rect }
    private fun skipControl(): Boolean = ViewportPolicy.skipRightControl(
        (bounds[focusedId]?.center?.x ?: viewport.left) - viewport.left, viewport.width)
    var topInsetPx: Int = 0
    var motion: MotionTokens = MotionTokens()
    var revealLibraries: suspend () -> Unit = {}
    var revealHero: suspend () -> Unit = {}

    fun register(section: String, id: String, requester: FocusRequester) {
        targets[id] = Target(section, requester)
    }
    fun unregister(id: String, requester: FocusRequester) {
        if (targets[id]?.requester === requester) { targets.remove(id); bounds.remove(id) }
    }
    fun focused(id: String) {
        focusedId = id
        if (id.startsWith("library:")) libraryId = id
    }
    private fun candidate(section: String, exact: String?, mediaOnly: Boolean): String? {
        val spec = sections.firstOrNull { it.key == section } ?: return null
        if (exact != null && targets[exact]?.section == section) return exact
        if (mediaOnly) {
            val candidates = targets.keys.filter { targets[it]?.section == section && it.startsWith(spec.mediaPrefix) }
            // Preserve library selection where possible; never route an empty shelf into a missing node.
            return candidates.firstOrNull() ?: targets.keys.firstOrNull { targets[it]?.section == section && it != spec.headingId }
                ?: spec.headingId?.takeIf { targets[it]?.section == section }
        }
        return spec.headingId?.takeIf { targets[it]?.section == section }
            ?: targets.keys.firstOrNull { targets[it]?.section == section }
    }
    private fun move(from: String, to: String, exact: String? = null,
                     mediaOnly: Boolean = false, rememberReturn: Boolean = true) {
        if (moving) return
        moving = true
        axis.horizontal = false
        scope.launch {
            try {
                if (to == HomeFocusPlan.HERO) { revealHero(); return@launch }
                val destination = sections.firstOrNull { it.key == to } ?: return@launch
                var eagerId: String? = null
                if (from != to) {
                    if (rememberReturn) returnSections[to] = from
                    val region = regions[to]
                    // Adjacent home shelves are normally already composed. Give the destination
                    // focus first so its card expansion begins in the very same frame as the
                    // vertical movement instead of waiting for the scroll animation to finish.
                    eagerId = candidate(to, exact, mediaOnly)
                    val eagerTarget = eagerId?.let { targets[it] }
                    if (region != null && eagerTarget != null) {
                        runCatching { eagerTarget.requester.requestFocus() }
                    }
                    if (to == HomeFocusPlan.LIBRARIES) {
                        // The libraries row is the first item of the media area: snap the whole page
                        // to it so half a banner never stays on screen. No extra revealItem here: in
                        // the touch layout index 0 is the banner itself, which used to pull it back.
                        revealLibraries()
                    } else if (region != null && viewport.height > 0f) {
                        val delta = ViewportPolicy.reveal(region.top, region.height,
                            viewport.top + topInsetPx, viewport.bottom, region.height + 6f)
                        if (motion.enabled) list.animateScrollBy(delta, motion.fade(280)) else list.scrollBy(delta)
                    } else if (to == HomeFocusPlan.LIBRARIES) revealLibraries()
                    else list.revealItem((destination.lazyIndex-lazyIndexOffset).coerceAtLeast(0), motion, topInsetPx.toFloat())
                }
                if (eagerId != null && focusedId == eagerId) return@launch
                // Wait for lazy composition/placement only when the target was not already available.
                repeat(20) {
                    withFrameNanos { }
                    val id = candidate(to, exact, mediaOnly) ?: return@repeat
                    val target = targets[id] ?: return@repeat
                    runCatching { target.requester.requestFocus() }
                    if (focusedId == id) return@launch
                }
            } finally { moving = false }
        }
    }
    fun key(section: String, event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || event.key !in listOf(Key.DirectionUp, Key.DirectionDown)) return false
        if (moving) return true // Coalesce held-key repeats; never queue many scroll/focus jobs.
        val spec = sections.firstOrNull { it.key == section } ?: return false
        val id = focusedId
        val down = event.key == Key.DirectionDown
        val heading = id == spec.headingId || id?.startsWith("shelf-sort:") == true
        if (down && heading && candidate(section, null, true) != null) {
            move(section, section, mediaOnly = true)
        } else if (!down && spec.headingId != null && !heading && !skipControl()) {
            move(section, section, exact = spec.headingId)
        } else if (down) {
            val destination = HomeFocusPlan.down(sections, section,
                if (section == HomeFocusPlan.NEXT_UP) libraryId else id)
            if (destination != null) move(section, destination, mediaOnly = skipControl())
            else return false // Leave trailing service-error/retry actions reachable by normal focus search.
        } else {
            val destination = returnSections[section]?.takeIf { back ->
                back != section && (back == HomeFocusPlan.HERO || sections.any { it.key == back })
            } ?: HomeFocusPlan.up(sections, section)
            if (destination != null) move(section, destination,
                exact = if (destination == HomeFocusPlan.LIBRARIES) libraryId else null,
                mediaOnly = skipControl(),
                rememberReturn = false)
        }
        return true
    }
}

val LocalHomeFocusNavigator = staticCompositionLocalOf<HomeFocusNavigator?> { null }
val LocalHomeFocusSection = staticCompositionLocalOf<String?> { null }

/** The region gets no focus of its own; all registered buttons/posters remain keyboard targets. */
@Composable
fun HomeFocusRegion(key: String, content: @Composable () -> Unit) {
    val navigator = LocalHomeFocusNavigator.current
    val compact = LocalCompact.current
    DisposableEffect(navigator,key) { onDispose { navigator?.region(key,null) } }
    CompositionLocalProvider(LocalHomeFocusSection provides key) {
        Box(Modifier.onGloballyPositioned {
            val p=it.positionInRoot()
            navigator?.region(key,Rect(p.x,p.y,p.x+it.size.width,p.y+it.size.height))
        }.onPreviewKeyEvent { !compact && navigator?.key(key, it) == true }.focusGroup()) {
            content()
        }
    }
}
