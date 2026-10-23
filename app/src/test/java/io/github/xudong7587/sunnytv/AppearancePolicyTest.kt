package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.*
import org.junit.Assert.*
import org.junit.Test

class AppearancePolicyTest {
    @Test fun removedColorsMigrateToRetainedFamiliesWithoutReappearing() {
        assertEquals(listOf("blue","celadon","violet","rose","brick","amber","indigo"),AppearanceColors.all.map {it.id})
        assertEquals("celadon",AppearancePolicy.color("green").id)
        assertEquals("amber",AppearancePolicy.color("mustard").id)
        assertEquals("blue",AppearancePolicy.color("slate").id)
        assertEquals("violet",AppearancePolicy.color("nightviolet").id)
        assertEquals("rose",AppearancePolicy.color("ruby").id)
        assertEquals("indigo",AppearancePolicy.color("",8).id)
        assertEquals("celadon",AppearancePolicy.color("unknown").id)
        assertEquals(0xFF12B689,AppearancePolicy.color("custom",custom=0x12B689).seed)
    }
    @Test fun colorWheelRoundTripsAndStrictHexEntrySupportsExtremeColors() {
        for(rgb in listOf(0xFF000000L,0xFFFFFFFFL,0xFF808080L,0xFF376BD8L,0xFFFF0000L,0xFF00FF00L,0xFF0000FFL)) {
            val hsv=MonoColor.toHsv(rgb)
            assertEquals(rgb,MonoColor.hsv(hsv[0],hsv[1],hsv[2]))
            assertEquals(rgb,MonoColor.parseHex(MonoColor.hex(rgb)))
        }
        for(bad in listOf("#12","##123456","#GGGGGG","#12345678","red","1234567")) assertNull(MonoColor.parseHex(bad))
    }
    @Test fun sameSeedProducesReadableTonesForAllMaterialsAndCustomExtremes() {
        fun contrast(a:Color,b:Color):Float {val x=a.luminance();val y=b.luminance();return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)}
        for(seed in listOf(0xFF000000L,0xFFFFFFFFL,0xFF808080L,0xFFFFFF00L,0xFF00FFFFL,0xFFFF00FFL)+AppearanceColors.all.map {it.seed}) {
            for(dark in listOf(false,true)) for(material in AppearancePolicy.catalog) {
                val p=palette(AppSettings(darkTheme=dark,appearanceColor="custom",customThemeColor=seed,appearanceMaterial=material.id))
                assertTrue("accent $seed $dark ${material.id}",contrast(p.accent,p.surface)>=3f)
                assertTrue(contrast(p.text,p.focusBackground)>=4.5f)
                for(stop in materialStops(material.id,p)) assertTrue("material $seed $dark ${material.id}",contrast(p.text,stop)>=4.5f)
            }
        }
        val p=palette(AppSettings(appearanceColor="custom",customThemeColor=0xFF336699,darkTheme=true))
        assertNotEquals(p.accent,p.accent2)
        assertNotEquals(p.accent,p.accent3)
    }
    @Test fun expensiveMaterialGatesApplyAgainAtRenderTimeAndKeepPreference() {
        assertEquals(16,AppearancePolicy.catalog.size)
        assertEquals("液态玻璃",AppearancePolicy.spec("glass").name)
        for(id in listOf("glass","aurora","frosted","neon")) {
            assertEquals("clear",AppearancePolicy.effectiveMaterial(id,false,false,36))
            assertEquals("clear",AppearancePolicy.effectiveMaterial(id,true,true,36))
            assertEquals(id,AppearancePolicy.effectiveMaterial(id,true,false,36))
        }
        for(id in listOf("glass","aurora","frosted")) {
            assertEquals(id,AppearancePolicy.effectiveMaterial(id,true,false,30))
            assertEquals("clear",AppearancePolicy.effectiveMaterial(id,true,false,29))
            assertEquals("clear",AppearancePolicy.effectiveMaterial(id,true,false,36,false))
        }
        assertEquals("clear",AppearancePolicy.material("unknown"))
        assertEquals("default",AppearancePolicy.corner("unknown"))
    }
    @Test fun innerNavigationCornersStayConcentricAfterPillClamping() {
        val density=Density(1f)
        for(scale in listOf(.42f,1f,1.9f)) for(width in listOf(44f,100f)) {
            val shape=InsetPanelShape(28.dp*scale,4.dp)
            val outline=shape.createOutline(Size(width,44f),LayoutDirection.Ltr,density) as Outline.Rounded
            val expected=(minOf(28*scale,26f)-4).coerceAtLeast(0f)
            assertEquals(expected,outline.roundRect.topLeftCornerRadius.x,.001f)
            assertEquals(expected,outline.roundRect.bottomRightCornerRadius.x,.001f)
        }
    }
    @Test fun changingOnePreferenceKeepsTheOthers() {
        val original=AppSettings(appearanceColor="custom",customThemeColor=0xFF127DA8,appearanceMaterial="glass",appearanceCorner="round",highPerformanceThemes=true)
        val changed=original.copy(highPerformanceThemes=false)
        assertEquals(original.appearanceColor,changed.appearanceColor)
        assertEquals(original.customThemeColor,changed.customThemeColor)
        assertEquals(original.appearanceMaterial,changed.appearanceMaterial)
        assertEquals(original.appearanceCorner,changed.appearanceCorner)
    }
}
