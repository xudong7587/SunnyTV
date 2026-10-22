package io.github.xudong7587.sunnytv

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.storage.ConfigStore
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class AppearanceUiTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private fun focus(tag:String)=rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
    private fun key(tag:String,key:Key)=rule.onNodeWithTag(tag).performKeyInput {pressKey(key)}

    @Test fun preferencesMigratePersistAndRecoverIndependently() {
        val context=object:ContextWrapper(rule.activity) {
            override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("appearance-test-$name",mode)
        }
        val prefs=context.getSharedPreferences("sunny-config",Context.MODE_PRIVATE)
        prefs.edit().clear().putInt("accent",8).putBoolean("dark",false).putString("performanceMode","low").commit()
        try {
            val store=ConfigStore(context)
            val migrated=store.settings()
            assertEquals("indigo",migrated.appearanceColor)
            assertEquals("clear",migrated.appearanceMaterial)
            assertFalse(migrated.highPerformanceThemes)
            store.saveSettings(migrated.copy(appearanceColor="custom",customThemeColor=0xFF26A0BC,appearanceMaterial="outline",appearanceCorner="round",highPerformanceThemes=true))
            val restored=ConfigStore(context).settings()
            assertEquals("custom",restored.appearanceColor)
            assertEquals(0xFF26A0BCL,restored.customThemeColor)
            assertEquals("outline",restored.appearanceMaterial)
            assertEquals("round",restored.appearanceCorner)
            assertFalse(restored.darkTheme)
            assertEquals("low",restored.performanceMode)
            assertTrue(restored.highPerformanceThemes)
            assertFalse(AppearancePolicy.allowsHighCostThemes(restored.highPerformanceThemes,PerformancePolicy.lean(restored.performanceMode,false)))
            prefs.edit().putString("appearanceColor","unknown").putString("appearanceMaterial","unknown").putString("appearanceCorner","invalid").commit()
            val recovered=ConfigStore(context).settings()
            assertEquals("celadon",recovered.appearanceColor)
            assertEquals("clear",recovered.appearanceMaterial)
            assertEquals("default",recovered.appearanceCorner)
            assertEquals("low",recovered.performanceMode)
        } finally {prefs.edit().clear().commit()}
    }

    @Test fun themeSwitchesKeepFocusGeometryAndProduceNativePreviews() {
        lateinit var model:AppModel
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false)
            model.saveSettings(AppSettings(reduceMotion=true,appearanceColor="celadon"))
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides false) {
                    SunnyTheme(model.settings) {
                        Column(Modifier.fillMaxSize().background(SunnyColors.Background).padding(32.dp).testTag("appearance:preview"),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                            Text("Sunny UI · 原生轻量主题",color=SunnyColors.Text)
                            Column(Modifier.width(560.dp).sunnyPanel().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                Text("材质与圆角",color=SunnyColors.Text)
                                Text("沿用原生控件、焦点与布局尺寸",color=SunnyColors.Secondary)
                                Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                                    Action("主操作",id="appearance:primary",primary=true,alwaysShowLabel=true) {}
                                    Action("次操作",id="appearance:secondary",alwaysShowLabel=true) {}
                                }
                                Field("输入框","示例内容",{})
                                FocusTile("appearance:selected",Modifier.width(260.dp).height(48.dp),active=true,onClick={}) {Text("已选中",color=SunnyColors.Text)}
                            }
                        }
                    }
                }
            }
        }
        focus("appearance:primary")
        val initial=rule.onNodeWithTag("appearance:primary").getUnclippedBoundsInRoot()
        for(dark in listOf(false,true)) for(material in listOf("clear","outline")) for(corner in listOf("tight","default","round")) {
            rule.runOnIdle {model.saveSettings(model.settings.copy(darkTheme=dark,appearanceMaterial=material,appearanceCorner=corner))}
            rule.waitForIdle()
            rule.onNodeWithTag("appearance:primary").assertIsFocused()
            assertEquals(initial,rule.onNodeWithTag("appearance:primary").getUnclippedBoundsInRoot())
            if(corner=="default") {
                val bitmap=rule.onNodeWithTag("appearance:preview").captureToImage().asAndroidBitmap()
                File(rule.activity.cacheDir,"appearance-$material-$dark.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
        }
        key("appearance:primary",Key.DirectionRight)
        rule.onNodeWithTag("appearance:secondary").assertIsFocused()
        key("appearance:secondary",Key.DirectionLeft)
        rule.onNodeWithTag("appearance:primary").assertIsFocused()
    }

    @Test fun expandedPaletteGridReachesLastRowAndMaterialSelectionPreservesColor() {
        lateinit var model:AppModel
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false)
            model.saveSettings(AppSettings(reduceMotion=true,appearanceColor="celadon"))
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides false) {
                    SunnyTheme(model.settings) {Box(Modifier.fillMaxSize().background(SunnyColors.Background)) {SettingsScreen()}}
                }
            }
        }
        rule.onNodeWithTag("settings:主题").performClick()
        focus("accent:0")
        for(index in listOf(0)) {
            key("accent:$index",Key.DirectionDown)
            rule.waitForIdle()
        }
        rule.onNodeWithTag("accent:5").assertIsFocused().performClick()
        rule.runOnIdle {assertEquals("amber",model.settings.appearanceColor)}
        rule.onNodeWithTag("settings:viewport").performScrollToNode(hasTestTag("material:outline"))
        rule.onNodeWithTag("material:outline").performClick()
        rule.runOnIdle {
            assertEquals("outline",model.settings.appearanceMaterial)
            assertEquals("amber",model.settings.appearanceColor)
            assertTrue(model.settings.darkTheme)
        }
        rule.onNodeWithTag("settings:viewport").performScrollToNode(hasTestTag("material:soft"))
        focus("material:soft")
        assertTrue(rule.onNodeWithTag("material:soft").getUnclippedBoundsInRoot().let {(it.bottom-it.top)<=72.dp})
        val preview=rule.onRoot().captureToImage().asAndroidBitmap()
        File(rule.activity.cacheDir,"material-settings-grid.png").outputStream().use {preview.compress(Bitmap.CompressFormat.PNG,100,it)}
        for(id in listOf("soft","paper","duotone","outline","clay")) {
            key("material:$id",Key.DirectionDown);rule.waitForIdle()
        }
        rule.onNodeWithTag("material:cutout").assertIsFocused()
        listOf("glass","frosted","aurora","neon").forEach {rule.onNodeWithTag("material:$it").assertDoesNotExist()}
    }

    @Test fun compactPaletteAndHighCostPermissionRemainReachableWithoutChangingPerformanceMode() {
        lateinit var model:AppModel
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false)
            model.saveSettings(AppSettings(reduceMotion=true,performanceMode="low",appearanceColor="celadon"))
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides true) {
                    SunnyTheme(model.settings) {Box(Modifier.width(390.dp).fillMaxHeight().background(SunnyColors.Background)) {SettingsScreen()}}
                }
            }
        }
        rule.onNodeWithTag("settings:主题").performClick()
        val bitmap=rule.onRoot().captureToImage().asAndroidBitmap()
        File(rule.activity.cacheDir,"appearance-compact-settings.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        focus("accent:0")
        for(index in listOf(0,3)) {
            key("accent:$index",Key.DirectionDown)
            rule.waitForIdle()
        }
        rule.onNodeWithTag("accent:6").assertIsFocused().performClick()
        rule.onNodeWithTag("settings:viewport").performScrollToNode(hasTestTag("toggle:允许高开销主题"))
        rule.onNodeWithTag("toggle:允许高开销主题").performClick()
        rule.runOnIdle {
            assertTrue(model.settings.highPerformanceThemes)
            assertEquals("low",model.settings.performanceMode)
            assertEquals("indigo",model.settings.appearanceColor)
            assertEquals("clear",model.settings.appearanceMaterial)
        }
        // Low-load mode keeps high-cost options out even with permission enabled.
        rule.onNodeWithTag("settings:viewport").performScrollToNode(hasTestTag("material:soft"))
        rule.onNodeWithTag("material:glass").assertDoesNotExist()
        rule.runOnIdle {model.saveSettings(model.settings.copy(performanceMode="standard"))}
        if(android.os.Build.VERSION.SDK_INT>=33) {
            rule.onNodeWithTag("settings:viewport").performScrollToNode(hasTestTag("material:glass"))
            rule.onNodeWithTag("material:glass").performClick()
            rule.runOnIdle {assertEquals("glass",model.settings.appearanceMaterial)}
        }
        rule.onNodeWithTag("settings:viewport").performScrollToNode(hasTestTag("toggle:允许高开销主题"))
        focus("toggle:允许高开销主题")
        rule.onNodeWithTag("toggle:允许高开销主题").performClick().assertIsFocused()
        rule.onNodeWithTag("settings:viewport").performScrollToNode(hasTestTag("material:clear"))
        rule.onNodeWithTag("material:glass").assertDoesNotExist()
        rule.onNodeWithText("✓ 清晰默认").assertIsDisplayed()
    }

    @Test fun customColorCanBeEditedByRemoteAppliedAndCancelled() {
        lateinit var model:AppModel
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false)
            model.saveSettings(AppSettings(reduceMotion=true,appearanceColor="blue"))
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides true) {
                    SunnyTheme(model.settings) {SettingsScreen()}
                }
            }
        }
        rule.onNodeWithTag("settings:主题").performClick()
        rule.onNodeWithTag("settings:viewport").performScrollToNode(hasTestTag("accent:7"))
        rule.onNodeWithTag("accent:7").performClick()
        focus("color:wheel")
        val picker=rule.onNode(isDialog()).captureToImage().asAndroidBitmap()
        File(rule.activity.cacheDir,"custom-color-picker.png").outputStream().use {picker.compress(Bitmap.CompressFormat.PNG,100,it)}
        key("color:wheel",Key.DirectionCenter)
        key("color:wheel",Key.DirectionRight)
        key("color:wheel",Key.DirectionUp)
        key("color:wheel",Key.DirectionCenter)
        key("color:wheel",Key.DirectionDown)
        rule.onNodeWithTag("color:brightness").assertIsFocused()
        key("color:brightness",Key.DirectionLeft)
        rule.onNodeWithTag("color:hex").performTextReplacement("#1AB7A9")
        rule.onNodeWithTag("dialog:color:apply").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals("custom",model.settings.appearanceColor)
            assertEquals(0xFF1AB7A9L,model.settings.customThemeColor)
        }
        rule.onNodeWithTag("accent:7").performClick()
        rule.onNodeWithTag("color:hex").performTextReplacement("#FFFFFF")
        rule.onNodeWithTag("dialog:color:cancel").performScrollTo().performClick()
        rule.runOnIdle {assertEquals(0xFF1AB7A9L,model.settings.customThemeColor)}
    }
}
