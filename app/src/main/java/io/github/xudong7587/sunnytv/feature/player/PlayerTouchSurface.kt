package io.github.xudong7587.sunnytv.feature.player

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

internal object PlayerGesturePolicy {
    fun region(x: Float, width: Int): Int = (x / width.coerceAtLeast(1) * 3).toInt().coerceIn(0, 2)
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
            var region = 1
            var dx = 0f
            var dy = 0f
            var accepted: Boolean? = null
            detectDragGestures(
                onDragStart = {
                    region = PlayerGesturePolicy.region(it.x, size.width)
                    dx = 0f; dy = 0f; accepted = null
                    start.value()
                },
                onDrag = { change, amount ->
                    dx += amount.x; dy += amount.y
                    if (accepted == null) accepted = if (region == 1) abs(dx) > abs(dy) else abs(dy) > abs(dx)
                    if (accepted == true) {
                        change.consume()
                        drag.value(region, dx / size.width.coerceAtLeast(1), -dy / size.height.coerceAtLeast(1))
                    }
                },
                onDragEnd = { end.value(accepted == true) },
                onDragCancel = { end.value(false) },
            )
        })
}
