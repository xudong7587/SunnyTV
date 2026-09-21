package io.github.xudong7587.sunnytv.feature.player

import android.content.Context
import android.graphics.Typeface
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import io.github.xudong7587.sunnytv.core.model.AppSettings
import io.github.xudong7587.sunnytv.core.model.SubtitleAppearance
import io.github.xudong7587.sunnytv.core.storage.loadFontTypeface

/**
 * Applies 外观 · 字幕外观 to Media3's caption rendering. The font colour and the outline colour are
 * never stored: they follow the current theme ([ink] / [accent]), as requested for the self build.
 */
@UnstableApi
internal fun applySubtitleAppearance(subtitle:SubtitleView,settings:AppSettings,ink:Int,accent:Int,typeface:Typeface?) {
    val background=when(settings.subtitleBackground) {
        SubtitleAppearance.BACKGROUND_NONE -> android.graphics.Color.TRANSPARENT
        SubtitleAppearance.BACKGROUND_THEME -> withAlpha(accent,.55f)
        else -> withAlpha(android.graphics.Color.BLACK,.70f)
    }
    val edge=when(settings.subtitleEdge) {
        SubtitleAppearance.EDGE_NONE -> CaptionStyleCompat.EDGE_TYPE_NONE
        SubtitleAppearance.EDGE_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        else -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
    }
    // The shadow reads best as a light tint of the theme colour; the outline keeps the accent.
    val edgeColor=if(settings.subtitleEdge==SubtitleAppearance.EDGE_SHADOW) lighten(accent) else accent
    subtitle.setStyle(CaptionStyleCompat(ink,background,android.graphics.Color.TRANSPARENT,edge,edgeColor,typeface))
    // The user's look wins over the styles embedded in the subtitle file.
    subtitle.setApplyEmbeddedStyles(false)
    subtitle.setApplyEmbeddedFontSizes(false)
    subtitle.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION*SubtitleAppearance.scale(settings.subtitleScaleLevel))
    subtitle.setBottomPaddingFraction(SubtitleAppearance.bottomPaddingFraction(settings.subtitlePosition))
}

@UnstableApi
internal fun applySubtitleAppearance(view:PlayerView,settings:AppSettings,ink:Int,accent:Int,typeface:Typeface?) {
    view.subtitleView?.let {applySubtitleAppearance(it,settings,ink,accent,typeface)}
}

private fun withAlpha(color:Int,alpha:Float):Int =
    (color and 0x00FFFFFF) or (((alpha.coerceIn(0f,1f)*255f).toInt() and 0xFF) shl 24)

/** Blends a colour 50% towards white so a drop shadow stays a light halo, not a dark blob. */
private fun lighten(color:Int):Int {
    val r=((color shr 16) and 0xFF); val g=((color shr 8) and 0xFF); val b=(color and 0xFF)
    fun mix(v:Int)=(v+(255-v)*0.5f).toInt().coerceIn(0,255)
    return (0xFF shl 24) or (mix(r) shl 16) or (mix(g) shl 8) or mix(b)
}

/** Resolves the subtitle typeface off the UI thread; null means "system font". */
internal fun subtitleTypeface(context:Context,settings:AppSettings):Typeface? =
    loadFontTypeface(context,settings.subtitleFontChoice,settings.customFontFile)
