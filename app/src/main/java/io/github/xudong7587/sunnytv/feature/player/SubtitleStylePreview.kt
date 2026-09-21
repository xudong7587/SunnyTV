package io.github.xudong7587.sunnytv.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import io.github.xudong7587.sunnytv.core.model.AppSettings
import io.github.xudong7587.sunnytv.feature.ui.SunnyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings preview for 外观 · 字幕外观. It renders through the very same Media3 [SubtitleView] and the
 * same style function the player uses, so what the user tunes here is what playback shows.
 */
// androidx.annotation.OptIn (not kotlin.OptIn) is the marker lint/AGP recognise for Media3's
// Java-side @UnstableApi requirement.
@androidx.annotation.OptIn(UnstableApi::class)
@Composable fun SubtitleStylePreview(settings:AppSettings,darkBackground:Boolean,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val ink=SunnyColors.Text.toArgb()
    val accent=SunnyColors.Accent.toArgb()
    val typeface by produceState<android.graphics.Typeface?>(null,settings.subtitleFontChoice,settings.customFontFile) {
        value=withContext(Dispatchers.IO) {subtitleTypeface(context,settings)}
    }
    // The sample surface follows the selected theme's brightness, so the previewed subtitle stays
    // readable while the colour itself keeps tracking the theme.
    val surface=if(darkBackground) listOf(Color(0xFF2A3A46),Color(0xFF10161B),Color(0xFF05080A))
        else listOf(Color(0xFFF7F9FA),Color(0xFFE6EBEF),Color(0xFFD5DCE2))
    Box(modifier.background(Brush.verticalGradient(surface)),
        contentAlignment=androidx.compose.ui.Alignment.BottomCenter) {
        AndroidView(factory={viewContext->SubtitleView(viewContext)},update={view->
            view.setCues(listOf(Cue.Builder().setText("让好内容回到大屏 · Subtitle Aa 123").build()))
            applySubtitleAppearance(view,settings,ink,accent,typeface)
        },modifier=Modifier.fillMaxSize())
    }
}
