package io.github.xudong7587.sunnytv.feature.player

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal object PlayerGesturePolicy {
    const val BRIGHTNESS = 0
    const val SEEK = 1
    const val VOLUME = 2
    /** Double-tap zones stay three-way: left rewinds, middle pauses, right fast-forwards. */
    fun region(x: Float, width: Int): Int = (x / width.coerceAtLeast(1) * 3).toInt().coerceIn(0, 2)
    /** Screen edges belong to the system back gesture, so a handset drag there is not ours. */
    fun isSystemEdge(x: Float, width: Int, edgePx: Float): Boolean = x < edgePx || x > width - edgePx
    /** Horizontal drags seek anywhere; vertical drags keep brightness/volume on their own half. */
    fun mode(horizontal: Boolean, startX: Float, width: Int): Int =
        if (horizontal) SEEK else if (startX < width / 2f) BRIGHTNESS else VOLUME
    fun seekTarget(start: Long, fraction: Float, duration: Long): Long =
        (start + (fraction * 120_000).toLong()).coerceIn(0, duration.coerceAtLeast(0))
}

/** Below the OSD: buttons and track menus keep their own touch targets. */
@Composable internal fun PlayerTouchSurface(
    onTap: () -> Unit,
    onDoubleTap: (Int) -> Unit,
    onStart: () -> Unit,
    onDrag: (Int, Float, Float) -> Unit,
    onEnd: (Boolean) -> Unit,
) {
    val tap = rememberUpdatedState(onTap)
    val doubleTap = rememberUpdatedState(onDoubleTap)
    val start = rememberUpdatedState(onStart)
    val drag = rememberUpdatedState(onDrag)
    val end = rememberUpdatedState(onEnd)
    Box(Modifier.fillMaxSize()
        .pointerInput(Unit) {
            detectTapGestures(onTap = { tap.value() },
                onDoubleTap = { doubleTap.value(PlayerGesturePolicy.region(it.x, size.width)) })
        }
        .pointerInput(Unit) {
            val edgePx = 24.dp.toPx()
            var startX = 0f
            var mode = PlayerGesturePolicy.SEEK
            var dx = 0f
            var dy = 0f
            var accepted: Boolean? = null
            var blocked = false
            detectDragGestures(
                onDragStart = {
                    startX = it.x
                    blocked = PlayerGesturePolicy.isSystemEdge(it.x, size.width, edgePx)
                    mode = PlayerGesturePolicy.mode(false, startX, size.width)
                    dx = 0f; dy = 0f; accepted = null
                    if (!blocked) start.value()
                },
                onDrag = { change, amount ->
                    if (!blocked) {
                        dx += amount.x; dy += amount.y
                        if (accepted == null) accepted = abs(dx) >= abs(dy)
                        if (accepted == true) {
                            change.consume()
                            mode = PlayerGesturePolicy.SEEK
                            drag.value(mode, dx / size.width.coerceAtLeast(1), -dy / size.height.coerceAtLeast(1))
                        } else if (accepted == false) {
                            change.consume()
                            mode = PlayerGesturePolicy.mode(false, startX, size.width)
                            drag.value(mode, dx / size.width.coerceAtLeast(1), -dy / size.height.coerceAtLeast(1))
                        }
                    }
                },
                onDragEnd = { if (!blocked) end.value(accepted == true) },
                onDragCancel = { if (!blocked) end.value(false) },
            )
        })
}
