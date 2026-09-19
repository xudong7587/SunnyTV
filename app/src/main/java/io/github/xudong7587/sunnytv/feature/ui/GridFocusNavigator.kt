package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One in-flight move, no queued key-repeat animations and no unconditional top alignment. */
class GridFocusNavigator(private val state: LazyGridState, private val scope: CoroutineScope) {
    private val targets = mutableMapOf<Int, FocusRequester>()
    var moving by mutableStateOf(false)
        private set
    var motion = MotionTokens()
    var topInset: () -> Float = { 0f }
    var beforeMove: (suspend (Int) -> Boolean)? = null
    fun register(index: Int, requester: FocusRequester) { targets[index] = requester }
    fun unregister(index: Int, requester: FocusRequester) {
        if (targets[index] === requester) targets.remove(index)
    }
    fun move(index: Int) {
        if (moving || index < 0) return
        moving = true
        scope.launch {
            try {
                if (beforeMove?.invoke(index) != true) state.revealItem(index, motion, topInset())
                repeat(20) {
                    withFrameNanos { }
                    val target = targets[index] ?: return@repeat
                    if (runCatching { target.requestFocus() }.getOrDefault(false)) return@launch
                }
            } finally { moving = false }
        }
    }
}

@Composable fun rememberGridFocusNavigator(state: LazyGridState): GridFocusNavigator {
    val scope = rememberCoroutineScope()
    val motion = LocalMotion.current
    val result = remember(state, scope) { GridFocusNavigator(state, scope) }
    SideEffect { result.motion = motion }
    return result
}

@Composable fun Modifier.gridFocusTarget(navigator: GridFocusNavigator, index: Int,
                                         up: Int?, down: Int?): Modifier {
    val requester = remember(navigator, index) { FocusRequester() }
    DisposableEffect(navigator, index, requester) {
        navigator.register(index, requester)
        onDispose { navigator.unregister(index, requester) }
    }
    return focusRequester(requester).onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false else {
            val destination = when (event.key) { Key.DirectionUp -> up; Key.DirectionDown -> down; else -> null }
            if (destination == null) false else { navigator.move(destination); true }
        }
    }
}
