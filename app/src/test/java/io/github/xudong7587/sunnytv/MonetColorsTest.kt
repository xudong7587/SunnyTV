package io.github.xudong7587.sunnytv

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.xudong7587.sunnytv.core.color.MonetColors
import io.github.xudong7587.sunnytv.core.color.mcu.hct.Hct
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.ui.palette
import org.junit.Assert.*
import org.junit.Test

class MonetColorsTest {
 private fun contrast(a:Color,b:Color):Float {
  val x=a.luminance();val y=b.luminance();return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
 }
 @Test fun allNineStylesKeepReadingAndFocusContrastAcrossExtremeSeeds() {
  assertEquals(9,MonetPolicy.styles.size)
  for(seed in listOf(0xFF6750A4L,0xFF00FF00L,0xFFFF0000L,0xFF0000FFL,0xFF000000L,0xFFFFFFFFL,0xFF808080L))
   for(dark in listOf(false,true)) for((style,_) in MonetPolicy.styles) for(material in listOf("clear","glass")) {
    val p=palette(AppSettings(darkTheme=dark,appearanceColor="custom",customThemeColor=seed,
     appearanceMaterial=material,monetEnabled=true,monetSource="palette",monetStyle=style))
    val case="seed=$seed dark=$dark style=$style material=$material"
    assertTrue("body $case",contrast(p.text,p.background)>=7f)
    assertTrue("control $case",contrast(p.text,p.control)>=4.5f)
    assertTrue("selected $case",contrast(p.focusContent,p.focusBackground)>=4.5f)
    assertTrue("secondary $case",contrast(p.secondary,p.background)>=4.5f)
    assertTrue("focus $case",contrast(p.accent,p.surface)>=3f)
    if(material=="glass") assertEquals(dark,p.background.luminance()<.3f)
   }
 }
 @Test fun systemSeedChangesPaletteButManualSeedDoesNotFollowSystem() {
  val settings=AppSettings(monetEnabled=true)
  assertNotEquals(palette(settings,0xFFAA2244).background,palette(settings,0xFF2277CC).background)
  val manual=settings.copy(monetSource="palette",appearanceColor="rose")
  assertEquals(palette(manual,0xFFAA2244),palette(manual,0xFF2277CC))
  assertEquals(palette(settings.copy(monetEnabled=false),0xFFAA2244),palette(settings.copy(monetEnabled=false),0xFF2277CC))
 }
 @Test fun stylesChangeThePaletteRatherThanOnlyTheLabel() {
  val s=AppSettings(monetEnabled=true,monetSource="palette",appearanceColor="rose")
  val variants=MonetPolicy.styles.map {palette(s.copy(monetStyle=it.first))}
  assertTrue(variants.distinct().size>=8)
  val mono=MonetColors.scheme(0xFFCC2244,true,"monochrome")
  assertTrue(Hct.fromInt(mono.primary).chroma<3)
  assertTrue(Hct.fromInt(mono.surfaceContainer).chroma<3)
 }
 @Test fun unknownOptionsRecoverToSafeDefaults() {
  assertEquals("system",MonetPolicy.source("unknown"))
  assertEquals("tonal_spot",MonetPolicy.style("unknown"))
  assertEquals(palette(AppSettings(monetEnabled=true,monetStyle="tonal_spot")),palette(AppSettings(monetEnabled=true,monetStyle="unknown")))
 }
}
