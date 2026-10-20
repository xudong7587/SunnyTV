package io.github.xudong7587.sunnytv

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xudong7587.sunnytv.core.model.AppSettings
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AmbilightSettingsTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun remotePalettePreviewsAndConfirmsColorWithoutLeakingFocus() {
        var selected:Int?=null
        var previews=0
        rule.setContent {SunnyTheme(AppSettings()) {
            LightPalette(0xff4d05,onPreview={previews++},onClose={},onSave={selected=it})
        }}
        rule.waitForIdle()
        val input=InstrumentationRegistry.getInstrumentation()
        input.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        input.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP)
        input.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        rule.waitForIdle()
        assertNotNull(selected);assertNotEquals(0xff4d05,selected);assertTrue(previews>=2)
    }
    @Test fun settingsExposeConnectionCalibrationFixedColorAndSavedLayout() {
        rule.setContent {
            val model=remember {AppModel(rule.activity.application,false,emptyList())}
            CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {
                androidx.compose.foundation.layout.Column {AmbilightSettings()}
            }}
        }
        rule.onNodeWithContentDescription("连接灯带").assertExists()
        rule.onNodeWithContentDescription("打开色板").assertExists()
        rule.onNodeWithContentDescription("设定末端与四角").assertExists()
        rule.onNodeWithText("上 / 右 / 下 / 左：31 / 15 / 34 / 16",substring=true).assertExists()
    }
}
