package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.xudong7587.sunnytv.core.color.MonetColors
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.LocalTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.storage.loadFontTypeface

@Immutable
data class SunnyPalette(val background:Color,val surface:Color,val raised:Color,val accent:Color,
    val ink:Color,val text:Color,val secondary:Color,val border:Color,
    val focusBackground:Color,val focusContent:Color,
    val accent2:Color,val accent3:Color,val control:Color,val controlEdge:Color,
    val success:Color,val warning:Color,val danger:Color)
// CSS explicitly mixes in sRGB. Compose lerp uses Oklab, so don't silently substitute its curve.
internal fun mixSrgb(base:Color,accent:Color,weight:Float)=Color(
    base.red+(accent.red-base.red)*weight,base.green+(accent.green-base.green)*weight,
    base.blue+(accent.blue-base.blue)*weight)
fun palette(settings:AppSettings,systemMonetSeed:Long=MonetPolicy.FALLBACK_SEED):SunnyPalette {
    val colors=AppearancePolicy.color(settings.appearanceColor,settings.accentIndex,settings.customThemeColor)
    val material=AppearancePolicy.material(settings.appearanceMaterial)
    val dark=settings.darkTheme || material=="neon"
    val outline=AppearancePolicy.material(settings.appearanceMaterial)=="outline"
    val accent=Color(if(dark) colors.dark else colors.light)
    val neutral=Color(if(dark) 0xFF1A212B else 0xFFFFFFFF)
    val surface=when(material) {
        "paper"->if(dark) mixSrgb(neutral,Color(0xFFC5AA7C),.05f) else Color(0xFFFFFCF4)
        "linen"->if(dark) mixSrgb(neutral,Color(0xFFC5AA7C),.05f) else Color(0xFFF1EFE6)
        "clay"->if(dark) mixSrgb(neutral,Color(0xFFCB9D76),.07f) else Color(0xFFE9D6C5)
        "soft"->Color(if(dark) 0xFF202834 else 0xFFEDF0F5)
        "metal"->Color(if(dark) 0xFF242D39 else 0xFFE8EDF3)
        "neon"->Color(0xFF141B2A)
        else->neutral
    }
    val raised=Color(if(dark) 0xFF202834 else 0xFFF7F8FB)
    val line=Color(if(dark) 0xFF2B3542 else 0xFFE1E6EE)
    val text=Color(if(dark) 0xFFE9EEF6 else 0xFF18212E)
    // Resolve the approved CSS cascade once per theme selection; no runtime CSS or old ink algorithm.
    val base=SunnyPalette(
        Color(if(dark) 0xFF12171F else if(outline) 0xFFECEFF4 else 0xFFEFF1F5),
        surface,raised,accent,Color(if(dark) 0xFF0F1620 else 0xFFFFFFFF),text,
        Color(if(dark) 0xFFBCC7D6 else 0xFF465468),
        if(outline || material=="neon") mixSrgb(raised,accent,.62f) else line,
        // Native opaque equivalent of the original active tint, avoiding an extra compositing layer.
        mixSrgb(surface,accent,if(outline) .08f else .10f),text,
        Color(if(dark) colors.dark2 else colors.light2),Color(if(dark) colors.dark3 else colors.light3),
        surface,
        if(outline) mixSrgb(line,accent,.38f) else line,
        Color(if(dark) 0xFF7FCBAA else 0xFF2F8465),Color(if(dark) 0xFFDFB87C else 0xFF9A6A1D),
        Color(if(dark) 0xFFEF9BA6 else 0xFFBF4A56))
    if(!settings.monetEnabled) return base
    val seed=if(MonetPolicy.source(settings.monetSource)=="system") systemMonetSeed else colors.seed
    val scheme=MonetColors.scheme(seed,dark,settings.monetStyle)
    val panel=Color(scheme.surfaceContainer)
    val primary=Color(scheme.primary)
    return base.copy(background=Color(scheme.background),surface=panel,raised=Color(scheme.surfaceContainerHigh),
        accent=primary,ink=Color(scheme.onPrimary),
        text=Color(scheme.onSurface),secondary=Color(scheme.onSurfaceVariant),border=Color(scheme.outlineVariant),
        focusBackground=mixSrgb(panel,primary,.14f),focusContent=Color(scheme.onSurface),
        accent2=Color(scheme.secondary),accent3=Color(scheme.tertiary),
        control=Color(scheme.surfaceContainerHigh),controlEdge=Color(scheme.outlineVariant))
}
val LocalSunnyPalette=staticCompositionLocalOf {palette(AppSettings())}
object SunnyColors {
    val Accent2:Color @Composable get()=LocalSunnyPalette.current.accent2
    val Accent3:Color @Composable get()=LocalSunnyPalette.current.accent3
    val Control:Color @Composable get()=LocalSunnyPalette.current.control
    val ControlEdge:Color @Composable get()=LocalSunnyPalette.current.controlEdge
    val Success:Color @Composable get()=LocalSunnyPalette.current.success
    val Warning:Color @Composable get()=LocalSunnyPalette.current.warning
    val Danger:Color @Composable get()=LocalSunnyPalette.current.danger
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
val LocalPageActive=staticCompositionLocalOf {true}
@Composable fun SunnyTheme(settings:AppSettings=AppSettings(),content:@Composable ()->Unit) {
    val context=LocalContext.current
    val lowRam=(context.applicationContext as? io.github.xudong7587.sunnytv.SunnyApp)?.lowRamDevice ?: false
    val lean=PerformancePolicy.lean(settings.performanceMode,lowRam)
    val hardware=androidx.compose.ui.platform.LocalView.current.isHardwareAccelerated
    val material=AppearancePolicy.effectiveMaterial(settings.appearanceMaterial,settings.highPerformanceThemes,
        lean,android.os.Build.VERSION.SDK_INT,hardware)
    val colorId=AppearancePolicy.color(settings.appearanceColor,settings.accentIndex,settings.customThemeColor).id
    val systemMonet=rememberSystemMonet(settings.monetEnabled && MonetPolicy.source(settings.monetSource)=="system",
        settings.darkTheme || material=="neon")
    val p=remember(settings.darkTheme,colorId,settings.customThemeColor,material,settings.monetEnabled,settings.monetSource,settings.monetStyle,systemMonet.seed) {
        palette(settings.copy(appearanceMaterial=material),systemMonet.seed)
    }
    val motion=remember(settings.animationSpeed,settings.reduceMotion) {MotionTokens(MotionPolicy.speed(settings))}
    val appearance=remember(material,settings.appearanceCorner,settings.shadowsEnabled,lean) {
        NativeAppearance(material,AppearancePolicy.cornerScale(settings.appearanceCorner),settings.shadowsEnabled && !lean)
    }
    // 外观 · 字体: the platform font, an imported document, or any font file in the font folder.
    val font by produceState<FontFamily>(FontFamily.SansSerif,settings.fontChoice,settings.customFontFile) {
        value=withContext(Dispatchers.IO) {
            loadFontTypeface(context,settings.fontChoice,settings.customFontFile)?.let {FontFamily(it)} ?: FontFamily.SansSerif
        }
    }
    CompositionLocalProvider(LocalSunnyPalette provides p,LocalMotion provides motion,LocalNativeAppearance provides appearance,LocalSystemMonet provides systemMonet) {
        val scheme=if(p.background.luminance()<.3f) darkColorScheme(primary=p.accent,onPrimary=p.ink,
            background=p.background,onBackground=p.text,surface=p.surface,onSurface=p.text)
        else lightColorScheme(primary=p.accent,onPrimary=p.ink,background=p.background,onBackground=p.text,
            surface=p.surface,onSurface=p.text)
        MaterialTheme(colorScheme=scheme) {
            ProvideTextStyle(LocalTextStyle.current.copy(fontFamily=font),content=content)
        }
    }
}
