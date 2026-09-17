package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import io.github.xudong7587.sunnytv.feature.AppModel

object SunnyColors {
    val Background=Color(0xFF090D0D)
    val Surface=Color(0xFF17201C)
    val SurfaceRaised=Color(0xFF222D26)
    val Accent=Color(0xFFD6E6A5)
    val Text=Color(0xFFF3F5EF)
    val Secondary=Color(0xFFABB7AD)
    val Border=Color(0xFF344138)
}
val LocalAppModel=staticCompositionLocalOf<AppModel> { error("SunnyTV AppModel missing") }
val LocalPageKey=staticCompositionLocalOf { "home" }
@Composable fun SunnyTheme(content:@Composable ()->Unit) {
    MaterialTheme(colorScheme=darkColorScheme(primary=SunnyColors.Accent,onPrimary=SunnyColors.Background,
        background=SunnyColors.Background,onBackground=SunnyColors.Text,
        surface=SunnyColors.Surface,onSurface=SunnyColors.Text),content=content)
}
