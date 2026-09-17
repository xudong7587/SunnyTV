package io.github.xudong7587.sunnytv

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.ui.palette
import org.junit.Assert.*
import org.junit.Test

class PresentationTest {
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
    @Test fun allAccentsKeepReadableTextInBothThemes() {
        fun contrast(a:Color,b:Color):Float {
            val x=a.luminance();val y=b.luminance()
            return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
        }
        assertEquals(10,Presentation.accents.size)
        for(dark in listOf(true,false)) for(index in 0..9) {
            val p=palette(AppSettings(darkTheme=dark,accentIndex=index))
            assertTrue("accent ink $index dark=$dark",contrast(p.accent,p.ink)>=4.5f)
            assertTrue("body $index dark=$dark",contrast(p.text,p.background)>=7f)
            assertTrue("secondary $index dark=$dark",contrast(p.secondary,p.background)>=4.5f)
            assertTrue("focus $index dark=$dark",contrast(p.accent,p.surface)>=3f)
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
