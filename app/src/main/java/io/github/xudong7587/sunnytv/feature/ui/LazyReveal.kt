package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.withFrameNanos
import io.github.xudong7587.sunnytv.core.model.ViewportPolicy
import kotlin.math.abs

/** Explicit D-pad reveal. The bound also makes stale indices/empty data safe. */
suspend fun LazyListState.revealItem(index: Int, motion: MotionTokens, top: Float = 0f,
                                    alignTop: Boolean = false) {
    if (index !in 0 until layoutInfo.totalItemsCount) return
    repeat(24) {
        val info = layoutInfo
        val target = info.visibleItemsInfo.firstOrNull { it.index == index }
        val delta = if (target != null) {
            if (alignTop) target.offset - top else ViewportPolicy.reveal(
                target.offset.toFloat(), target.size.toFloat(), top, info.viewportEndOffset.toFloat())
        } else {
            val visible = info.visibleItemsInfo
            val direction = if (index < (visible.firstOrNull()?.index ?: 0)) -1 else 1
            direction * (info.viewportEndOffset - maxOf(info.viewportStartOffset, top.toInt())).coerceAtLeast(1) * .7f
        }
        if (abs(delta) < .5f) return
        val consumed = if (motion.enabled) animateScrollBy(delta, motion.fade(360)) else scrollBy(delta)
        if (abs(consumed) < .5f) return
        withFrameNanos { }
        // A measured reveal is exact; no second correction which could reverse oversized rows.
        if (target != null) return
    }
}

suspend fun LazyGridState.revealItem(index: Int, motion: MotionTokens, top: Float = 0f,
                                    alignTop: Boolean = false) {
    if (index !in 0 until layoutInfo.totalItemsCount) return
    repeat(24) {
        val info = layoutInfo
        val target = info.visibleItemsInfo.firstOrNull { it.index == index }
        val delta = if (target != null) {
            val step = target.size.height + info.mainAxisItemSpacing
            if (alignTop) target.offset.y - top else ViewportPolicy.reveal(
                target.offset.y.toFloat(), target.size.height.toFloat(), top,
                info.viewportEndOffset.toFloat(), step.toFloat())
        } else {
            val visible = info.visibleItemsInfo
            val direction = if (index < (visible.firstOrNull()?.index ?: 0)) -1 else 1
            val row = if (direction > 0) visible.lastOrNull() else visible.firstOrNull()
            direction * ((row?.size?.height ?: 160) + info.mainAxisItemSpacing).toFloat()
        }
        if (abs(delta) < .5f) return
        val consumed = if (motion.enabled) animateScrollBy(delta, motion.fade(360)) else scrollBy(delta)
        if (abs(consumed) < .5f) return
        withFrameNanos { }
        if (target != null) return
    }
}
