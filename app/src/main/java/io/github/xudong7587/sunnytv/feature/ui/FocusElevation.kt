package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.animation.core.animateFloatAsState
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
 * 40 tiny drawing records are cached per size: no bitmap, software View, live blur,
 * offscreen full-screen buffer, or image decoding on the UI thread.
 * Draw outside the casting shape, BEFORE the inner content clip, so translucent
 * buttons keep their original fill. The two layers have a real downward offset.
 */
fun Modifier.softFocusShadow(shape: Shape, enabled: Boolean): Modifier = if (!enabled) this else
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
            layer(sigmaDp = 7.2f, extentDp = 22.4f, opacity = .28f, dyDp = 6f, steps = 28)
            layer(sigmaDp = 2f, extentDp = 6f, opacity = .22f, dyDp = 2f, steps = 12)
        }
        onDrawBehind {
            clipPath(caster, clipOp = ClipOp.Difference) {
                bands.forEach { band ->
                    translate(top = band.offset) { drawPath(caster, band.color, style = band.stroke) }
                }
            }
        }
    }

/** Hero/accordion rows opt out of lift so their common top and bottom never drift. */
@Composable
fun Modifier.focusElevation(shape: Shape, elevated: Boolean, liftEnabled: Boolean = true): Modifier {
    val motion = LocalMotion.current
    val lift = animateFloatAsState(if (elevated && liftEnabled && motion.enabled) 1f else 0f,
        motion.fade(160), label = "focus-elevation")
    return graphicsLayer {
        scaleX = 1f + .02f * lift.value
        scaleY = 1f + .02f * lift.value
        translationY = -2.dp.toPx() * lift.value
        clip = false
    }.softFocusShadow(shape, elevated)
}
