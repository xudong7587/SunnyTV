package io.github.xudong7587.sunnytv

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.storage.ConfigStore
import io.github.xudong7587.sunnytv.core.storage.FontStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FontSettingsTest {
    @Test fun selectedFontIsCopiedPrivatelyAndPreferencesSurviveAStoreReload() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=File("/system/fonts/Roboto-Regular.ttf")
        assertTrue("TV system font fixture missing",source.isFile)
        val (file,name)=FontStore.import(context,Uri.fromFile(source))
        val imported=File(File(context.filesDir,"fonts"),file)
        assertTrue(imported.isFile)
        assertEquals(source.length(),imported.length())
        val store=ConfigStore(context);val original=store.settings()
        try {
            for(speed in MotionPolicy.speeds) {
                store.saveSettings(original.copy(animationSpeed=speed,reduceMotion=false,fontScaleLevel=4,customFontFile=file,customFontName=name))
                val restored=ConfigStore(context).settings()
                assertEquals(speed,restored.animationSpeed)
                assertEquals(4,restored.fontScaleLevel)
                assertEquals(file,restored.customFontFile)
                assertEquals(name,restored.customFontName)
            }
        } finally {store.saveSettings(original);imported.delete()}
    }
    @Test fun invalidFontLeavesNoImportedFileAndDoesNotChangePreferences() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.filesDir,"fonts")
        val before=directory.list()?.toSet().orEmpty()
        val original=ConfigStore(context).settings()
        val bad=File(context.cacheDir,"invalid-test.ttf").apply {writeText("This is not a font")}
        try {
            try {FontStore.import(context,Uri.fromFile(bad));fail("Invalid font accepted")}
            catch(_:IllegalArgumentException) {}
            assertEquals(before,directory.list()?.toSet().orEmpty())
            assertEquals(original,ConfigStore(context).settings())
        } finally {bad.delete()}
    }
}
