package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.core.model.*

data class SunnyPalette(val background:Color,val surface:Color,val raised:Color,val accent:Color,
    val ink:Color,val text:Color,val secondary:Color,val border:Color)
fun palette(settings:AppSettings):SunnyPalette {
    val pair=Presentation.accents[settings.accentIndex.coerceIn(0,9)]
    val colors=listOf(Color(pair.second),Color(pair.third)).sortedBy {it.luminance()}
    // Keep the stored swatches exact; derive a slightly darker ink only when the pair needs it.
    var darker=colors[0]
    while((colors[1].luminance()+.05f)/(darker.luminance()+.05f)<4.5f) darker=lerp(darker,Color.Black,.04f)
    val dark=settings.darkTheme
    return SunnyPalette(if(dark) Color(0xFF08090B) else lerp(colors[1],Color.White,.90f),
        if(dark) Color(0xFF181A1D) else lerp(colors[1],Color.White,.82f),
        if(dark) Color(0xFF2A2D32) else lerp(colors[1],Color.White,.70f),
        if(dark) colors[1] else darker, if(dark) darker else colors[1],
        if(dark) Color(0xFFF5F5F2) else Color(0xFF17191C),
        if(dark) Color(0xFFB9BDBE) else Color(0xFF535A5D),
        if(dark) Color(0xFF53585C) else Color(0xFF9A9F9C))
}
val LocalSunnyPalette=staticCompositionLocalOf {palette(AppSettings())}
object SunnyColors {
    val Background:Color @Composable get()=LocalSunnyPalette.current.background
    val Surface:Color @Composable get()=LocalSunnyPalette.current.surface
    val SurfaceRaised:Color @Composable get()=LocalSunnyPalette.current.raised
    val Accent:Color @Composable get()=LocalSunnyPalette.current.accent
    val AccentInk:Color @Composable get()=LocalSunnyPalette.current.ink
    val Text:Color @Composable get()=LocalSunnyPalette.current.text
    val Secondary:Color @Composable get()=LocalSunnyPalette.current.secondary
    val Border:Color @Composable get()=LocalSunnyPalette.current.border
}
val LocalAppModel=staticCompositionLocalOf<AppModel> { error("SunnyTV AppModel missing") }
val LocalPageKey=staticCompositionLocalOf { "home" }
@Composable fun SunnyTheme(settings:AppSettings=AppSettings(),content:@Composable ()->Unit) {
    val p=remember(settings.darkTheme,settings.accentIndex) {palette(settings)}
    CompositionLocalProvider(LocalSunnyPalette provides p) {
        val scheme=if(settings.darkTheme) darkColorScheme(primary=p.accent,onPrimary=p.ink,
            background=p.background,onBackground=p.text,surface=p.surface,onSurface=p.text)
        else lightColorScheme(primary=p.accent,onPrimary=p.ink,background=p.background,onBackground=p.text,
            surface=p.surface,onSurface=p.text)
        MaterialTheme(colorScheme=scheme,content=content)
    }
}
