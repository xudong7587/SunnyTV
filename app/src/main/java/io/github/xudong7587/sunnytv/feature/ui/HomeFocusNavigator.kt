package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import io.github.xudong7587.sunnytv.core.model.HomeFocusPlan
import io.github.xudong7587.sunnytv.core.model.HomeFocusSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Registered focus targets are composed targets, never requesters for off-screen lazy items. */
@Stable
class HomeFocusNavigator(private val list: LazyListState, private val scope: CoroutineScope,
                         private val axis: TvFocusMotion) {
    private data class Target(val section: String, val requester: FocusRequester)
    private val targets = linkedMapOf<String, Target>()
    private val returnSections = mutableMapOf<String, String>()
    var sections: List<HomeFocusSection> = emptyList()
    var moving by mutableStateOf(false)
        private set
    private var focusedId: String? = null
    private var libraryId: String? = null
    var topInsetPx: Int = 0
    var motion: MotionTokens = MotionTokens()
    var revealLibraries: suspend () -> Unit = {}
    var revealHero: suspend () -> Unit = {}

    fun register(section: String, id: String, requester: FocusRequester) {
        targets[id] = Target(section, requester)
    }
    fun unregister(id: String, requester: FocusRequester) {
        if (targets[id]?.requester === requester) targets.remove(id)
    }
    fun focused(id: String) {
        focusedId = id
        if (id.startsWith("library:")) libraryId = id
    }
    private fun candidate(section: String, exact: String?, mediaOnly: Boolean): String? {
        val spec = sections.firstOrNull { it.key == section } ?: return null
        if (exact != null && targets[exact]?.section == section) return exact
        if (mediaOnly) return targets.keys.firstOrNull {
            targets[it]?.section == section && it.startsWith(spec.mediaPrefix)
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
                if (from != to) {
                    if (rememberReturn) returnSections[to] = from
                    if (to == HomeFocusPlan.LIBRARIES) revealLibraries()
                    else if (motion.enabled) list.animateScrollToItem(destination.lazyIndex, -topInsetPx)
                    else list.scrollToItem(destination.lazyIndex, -topInsetPx)
                }
                // Wait for lazy composition/placement, not an arbitrary delay or an infinite retry.
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
        if (down && id == spec.headingId && candidate(section, null, true) != null) {
            move(section, section, mediaOnly = true)
        } else if (!down && spec.headingId != null && id != spec.headingId) {
            move(section, section, exact = spec.headingId)
        } else if (down) {
            val destination = HomeFocusPlan.down(sections, section,
                if (section == HomeFocusPlan.NEXT_UP) libraryId else id)
            if (destination != null) move(section, destination)
            else return false // Leave trailing service-error/retry actions reachable by normal focus search.
        } else {
            val destination = returnSections[section]?.takeIf { back ->
                back != section && (back == HomeFocusPlan.HERO || sections.any { it.key == back })
            } ?: HomeFocusPlan.up(sections, section)
            if (destination != null) move(section, destination,
                exact = if (destination == HomeFocusPlan.LIBRARIES) libraryId else null,
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
    CompositionLocalProvider(LocalHomeFocusSection provides key) {
        Box(Modifier.onPreviewKeyEvent { !compact && navigator?.key(key, it) == true }.focusGroup()) {
            content()
        }
    }
}
