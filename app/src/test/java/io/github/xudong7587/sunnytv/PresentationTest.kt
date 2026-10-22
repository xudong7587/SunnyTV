package io.github.xudong7587.sunnytv

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.ui.palette
import org.junit.Assert.*
import org.junit.Test

class PresentationTest {
    @Test fun liquidGlassPreservesDayNightAndTheArtworkAccent() {
        for(dark in listOf(false,true)) for(monet in listOf(false,true)) {
            val settings=AppSettings(appearanceColor="rose",appearanceMaterial="glass",darkTheme=dark,monetEnabled=monet)
            val glass=palette(settings)
            val ordinary=palette(settings.copy(appearanceMaterial="clear"))
            assertEquals(ordinary.accent,glass.accent)
            assertEquals(ordinary.background,glass.background)
            assertEquals(dark,glass.background.luminance()<.3f)
            assertEquals("rose",settings.appearanceColor)
            assertEquals(dark,settings.darkTheme)
        }
    }

    @Test fun heroWrapsButLibraryEndDoesNot() {
        assertEquals(0,Presentation.next(5,1,6,true))
        assertEquals(5,Presentation.next(0,-1,6,true))
        assertEquals(10,Presentation.next(10,1,11,false))
        assertEquals(0,Presentation.next(0,-1,11,false))
        assertEquals(0,Presentation.next(0,1,0,true))
    }
    @Test fun autoplayStopsForEveryInteractionAndVisibilityGate() {
        assertTrue(Presentation.canRotate(false,false,true,true,false,false,false,6))
        assertFalse(Presentation.canRotate(true,false,true,true,false,false,false,6))
        assertFalse(Presentation.canRotate(false,true,true,true,false,false,false,6))
        assertFalse(Presentation.canRotate(false,false,false,true,false,false,false,6))
        assertFalse(Presentation.canRotate(false,false,true,false,false,false,false,6))
        assertFalse(Presentation.canRotate(false,false,true,true,true,false,false,6))
        assertFalse(Presentation.canRotate(false,false,true,true,false,true,false,6))
        assertFalse(Presentation.canRotate(false,false,true,true,false,false,true,6))
        assertFalse(Presentation.canRotate(false,false,true,true,false,false,false,1))
    }
    @Test fun allNativeThemeControlsKeepReadableTextInBothThemes() {
        fun contrast(a:Color,b:Color):Float {
            val x=a.luminance();val y=b.luminance()
            return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
        }
        assertEquals(7,AppearanceColors.all.size)
        for(dark in listOf(true,false)) for(material in AppearancePolicy.materials) for(color in AppearanceColors.all) {
            val p=palette(AppSettings(darkTheme=dark,appearanceColor=color.id,appearanceMaterial=material.first))
            // Native primary/selected controls now use tonal fill + normal reading text. The CSS
            // accent-ink pair is retained as source data but is not used for these small labels.
            assertTrue("primary ${color.id} dark=$dark",contrast(p.focusContent,p.focusBackground)>=4.5f)
            assertTrue("control ${color.id} dark=$dark",contrast(p.text,p.control)>=4.5f)
            assertTrue("body ${color.id} dark=$dark",contrast(p.text,p.background)>=7f)
            assertTrue("secondary ${color.id} dark=$dark",contrast(p.secondary,p.background)>=4.5f)
            assertTrue("focus ${color.id} dark=$dark",contrast(p.accent,p.surface)>=3f)
        }
    }
    @Test fun simplifiedPreferenceDoesNotSilentlyChooseTraditional() {
        assertTrue(Presentation.languageMatches("zh-Hans","chi","简体中文"))
        assertTrue(Presentation.languageMatches("zh-Hans","zh-CN"))
        assertFalse(Presentation.languageMatches("zh-Hans","chi","繁体中文"))
        assertFalse(Presentation.languageMatches("zh-Hans","zh-Hant"))
        assertTrue(Presentation.languageMatches("zh","zho"))
        assertTrue(Presentation.languageMatches("en","eng"))
        assertFalse(Presentation.languageMatches("none","eng"))
    }
}
