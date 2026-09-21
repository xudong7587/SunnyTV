package io.github.xudong7587.sunnytv.feature.ui

import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * True on phones/handhelds in any orientation. Layout width and input device are separate
 * concerns: a landscape phone is wide (not [LocalCompact]) but it is still a handset, so it keeps
 * touch scrolling and must never be given the TV-only two-layer hero transition.
 */
val LocalHandset = staticCompositionLocalOf { false }

/** Non-zero on handset only: keeps pinned chrome and logo below phone camera cutouts. */
val LocalTopInset = staticCompositionLocalOf { 0.dp }

/**
 * True on touch-first devices (phones and tablets, in any orientation). Tablets are wide enough to
 * miss [LocalCompact], so page flow and pull-to-refresh must not depend on width alone.
 */
val LocalTouchFirst = staticCompositionLocalOf { false }

fun isTouchFirst(configuration: Configuration): Boolean =
    configuration.touchscreen != Configuration.TOUCHSCREEN_NOTOUCH

fun isPortrait(configuration: Configuration): Boolean =
    configuration.orientation == Configuration.ORIENTATION_PORTRAIT

fun isHandset(configuration: Configuration): Boolean {
    val type = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    if (type == Configuration.UI_MODE_TYPE_TELEVISION) return false
    return configuration.smallestScreenWidthDp < 600
}

/**
 * Extra top padding only for a handset in portrait, where the camera cutout sits above the pinned
 * chrome. Landscape phones and televisions keep their original height.
 */
fun handsetTopInset(cutoutTop: Dp, handset: Boolean, portrait: Boolean): Dp =
    if (handset && portrait) maxOf(cutoutTop, 26.dp) else cutoutTop
