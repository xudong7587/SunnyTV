package io.github.xudong7587.sunnytv.core.model

import kotlin.math.ceil

/** Pixel-based policy shared by lazy grids and shelves. No TV pivot/centering. */
object ViewportPolicy {
    fun reveal(offset: Float, size: Float, top: Float, bottom: Float, rowStep: Float = 0f): Float {
        if (!offset.isFinite() || !size.isFinite() || bottom <= top || size <= 0f) return 0f
        // An oversized row cannot fit: expose its leading edge, without oscillating forever.
        if (size > bottom - top) return if (offset < top || offset >= bottom) offset - top else 0f
        val delta = when {
            offset < top - .5f -> offset - top
            offset + size > bottom + .5f -> offset + size - bottom
            else -> 0f
        }
        if (delta == 0f || rowStep <= 0f || !rowStep.isFinite()) return delta
        // Normal D-pad moves reveal precisely one row, not a fraction of a poster.
        val rows = ceil(kotlin.math.abs(delta) / rowStep).coerceAtLeast(1f)
        return (if (delta < 0) -1 else 1) * rows * rowStep
    }
    fun skipRightControl(centerX: Float, viewportWidth: Float): Boolean =
        viewportWidth <= 0f || centerX < viewportWidth * (2f / 3f)
}

object PerformancePolicy {
    val cacheSizesMiB = listOf(128, 256, 512, 1024)
    val modes = listOf("auto", "balanced", "low")
    fun cacheSize(value: Int) = value.takeIf { it in cacheSizesMiB } ?: 512
    fun lean(mode: String, lowRam: Boolean) = mode == "low" || (mode == "auto" && lowRam)
    fun memoryBytes(heap: Long, lowRam: Boolean): Int =
        minOf(if (lowRam) 32L * 1024 * 1024 else 64L * 1024 * 1024, (heap / 10).coerceAtLeast(1)).toInt()
    fun imageWidth(request: Int, lean: Boolean) = request.coerceIn(64, if (lean) 1280 else 3840)
}
