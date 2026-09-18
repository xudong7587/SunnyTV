package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Neutral warm-grey contact + ambient shadow. No accent-colored glow or large offscreen blur. */
fun Modifier.softFocusShadow(shape: Shape, enabled: Boolean): Modifier = if (!enabled) this else
    shadow(14.dp, shape, clip = false, ambientColor = Color(0x17433F38), spotColor = Color(0x20433F38))
        .shadow(4.dp, shape, clip = false, ambientColor = Color(0x12433F38), spotColor = Color(0x18433F38))

/** Transform only the focused tile, never the surrounding row or the static glass panel. */
@Composable
fun Modifier.focusElevation(shape: Shape, elevated: Boolean): Modifier {
    val motion = LocalMotion.current
    val lift by animateFloatAsState(if (elevated && motion.enabled) 1f else 0f,
        motion.fade(160), label = "focus-elevation")
    return graphicsLayer {
        scaleX = 1f + .02f * lift
        scaleY = 1f + .02f * lift
        translationY = -2.dp.toPx() * lift
        clip = false
    }.softFocusShadow(shape, elevated)
}
