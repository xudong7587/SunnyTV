package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.exp

private data class ShadowBand(val offset: Float, val color: Color, val stroke: Stroke)

/**
 * A deterministic soft drop shadow, not Android's elevation/light-source shadow.
 * Cached, antialiased contour bands approximate Gaussian falloff. Only the outline and
 * Compact contour bands are cached per size: no bitmap, software View, live blur,
 * offscreen full-screen buffer, or image decoding on the UI thread.
 * Draw outside the casting shape, BEFORE the inner content clip, so translucent
 * buttons keep their original fill. The two layers have a real downward offset.
 */
fun Modifier.softFocusShadow(shape: Shape, enabled: Boolean, lean: Boolean = false): Modifier = if (!enabled) this else
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val caster = when (outline) {
            is Outline.Generic -> outline.path
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        }
        val bands = buildList {
            fun layer(sigmaDp: Float, extentDp: Float, opacity: Float, dyDp: Float, steps: Int) {
                var previous = 0f
                for (i in steps downTo 1) {
                    val distance = extentDp * i / steps
                    val target = opacity * exp(-distance * distance / (2f * sigmaDp * sigmaDp))
                    val alpha = ((target - previous) / (1f - previous)).coerceIn(0f, 1f)
                    add(ShadowBand(dyDp.dp.toPx(), Color(0xFF3F3B36).copy(alpha = alpha),
                        Stroke(width = distance.dp.toPx() * 2f, join = StrokeJoin.Round)))
                    previous = target
                }
            }
            // Keep the lift compact: it suggests separation without forming a heavy second edge.
            layer(sigmaDp = 4.3f, extentDp = 13.4f, opacity = .22f, dyDp = 3.6f, steps = if(lean) 6 else 18)
            layer(sigmaDp = 1.2f, extentDp = 3.6f, opacity = .16f, dyDp = 1.2f, steps = if(lean) 3 else 8)
        }
        onDrawBehind {
            clipPath(caster, clipOp = ClipOp.Difference) {
                bands.forEach { band ->
                    translate(top = band.offset) { drawPath(caster, band.color, style = band.stroke) }
                }
            }
        }
    }

/** Focus never changes geometry, including inside clipped lazy viewports. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun Modifier.focusElevation(shape: Shape, elevated: Boolean, liftEnabled: Boolean = true): Modifier {
    val model=LocalAppModel.current
    return softFocusShadow(shape, elevated, model.app.lean(model.settings))
}
