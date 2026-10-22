package io.github.xudong7587.sunnytv

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class MaterialRenderingTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private fun save(bitmap:Bitmap,name:String) {
        File(rule.activity.cacheDir,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun liquidGlassUsesRealBackdropAndUpdatesWithoutBlurringLabels() {
        assumeTrue(Build.VERSION.SDK_INT>=33)
        var shift by mutableIntStateOf(0)
        var showGlass by mutableStateOf(false)
        lateinit var model:AppModel
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false)
            model.saveSettings(AppSettings(darkTheme=false,appearanceColor="rose",appearanceMaterial="glass",highPerformanceThemes=true,performanceMode="standard",reduceMotion=true))
            activity.setContent {
                CompositionLocalProvider(LocalAppModel provides model) {
                    SunnyTheme(model.settings) {
                        val source=rememberGlassBackdrop(true)!!
                        Box(Modifier.padding(20.dp).size(320.dp,230.dp).testTag("glass:fixture")) {
                            Canvas(Modifier.fillMaxSize().captureGlassBackdrop(source)) {
                                drawRect(Color(0xFFF3E1C8))
                                for(i in -2..15) drawRect(if(i%2==0) Color(0xFFAA4063) else Color(0xFF377C88),
                                    Offset((i*32+shift).dp.toPx(),0f),Size(16.dp.toPx(),size.height))
                                drawCircle(Color(0xFFF7CE75),45.dp.toPx(),Offset(235.dp.toPx(),145.dp.toPx()))
                            }
                            if(showGlass) CompositionLocalProvider(LocalGlassBackdrop provides source) {
                                Box(Modifier.offset(20.dp,64.dp).size(280.dp,96.dp).sunnyPanel(28.dp).testTag("glass:panel")) {
                                    Text("液态玻璃",color=SunnyColors.Text,fontSize=20.sp,modifier=Modifier.padding(28.dp).testTag("glass:label"))
                                }
                            }
                        }
                    }
                }
            }
        }
        val raw=rule.onNodeWithTag("glass:fixture").captureToImage().asAndroidBitmap()
        rule.runOnIdle {showGlass=true}
        rule.waitForIdle()
        rule.onNodeWithTag("glass:label").assertIsDisplayed()
        val before=rule.onNodeWithTag("glass:fixture").captureToImage().asAndroidBitmap()
        save(before,"liquid-glass-before.png")
        fun changes(a:Bitmap,b:Bitmap):Int {
            var changed=0
            // Interior crop avoids the label, shadow and clip rim; genuine background samples must differ.
            for(y in (a.height*.55).toInt() until (a.height*.63).toInt() step 3)
                for(x in (a.width*.15).toInt() until (a.width*.75).toInt() step 3)
                    if(a.getPixel(x,y)!=b.getPixel(x,y)) changed++
            return changed
        }
        assertTrue("Glass must transform the sampled background",changes(raw,before)>100)
        rule.runOnIdle {shift=15}
        rule.waitForIdle()
        val after=rule.onNodeWithTag("glass:fixture").captureToImage().asAndroidBitmap()
        save(after,"liquid-glass-after.png")
        assertTrue("Backdrop changes must reach the glass without a timer",changes(before,after)>100)
        val labelBefore=rule.onNodeWithTag("glass:label").getUnclippedBoundsInRoot()
        for(corner in listOf("tight","round")) {
            rule.runOnIdle {model.saveSettings(model.settings.copy(appearanceCorner=corner))}
            rule.waitForIdle()
            assertEquals(labelBefore,rule.onNodeWithTag("glass:label").getUnclippedBoundsInRoot())
        }
    }
    @Test fun disabledAndLowLoadGlassNeverAllocateBackdrop() {
        lateinit var model:AppModel
        var observed=""
        var allocated=true
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false)
            model.saveSettings(AppSettings(appearanceMaterial="glass",highPerformanceThemes=false,performanceMode="standard"))
            activity.setContent {CompositionLocalProvider(LocalAppModel provides model) {
                SunnyTheme(model.settings) {
                    val material=LocalNativeAppearance.current.material
                    val source=rememberGlassBackdrop(AppearancePolicy.spec(material).glass)
                    SideEffect {observed=material;allocated=source!=null}
                    Text("Fallback",color=SunnyColors.Text)
                }
            }}
        }
        rule.runOnIdle {assertEquals("clear",observed);assertFalse(allocated)
            model.saveSettings(model.settings.copy(highPerformanceThemes=true,performanceMode="low"))}
        rule.runOnIdle {assertEquals("clear",observed);assertFalse(allocated);assertEquals("glass",model.settings.appearanceMaterial)}
    }
    @Test fun everyMaterialRendersInBothThemesAndProducesReviewImages() {
        lateinit var model:AppModel
        var material by mutableStateOf("clear")
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false)
            model.saveSettings(AppSettings(darkTheme=false,appearanceColor="rose",highPerformanceThemes=true,performanceMode="standard",reduceMotion=true))
            activity.setContent {CompositionLocalProvider(LocalAppModel provides model) {
                SunnyTheme(model.settings) {
                    Column(Modifier.fillMaxWidth().background(SunnyColors.Background).padding(24.dp).testTag("material:sample"),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                        Text(AppearancePolicy.spec(material).name,color=SunnyColors.Text,fontSize=24.sp)
                        MaterialSample(AppearancePolicy.spec(material),model.settings,true,Modifier.size(280.dp,130.dp))
                        Text(AppearancePolicy.spec(material).note,color=SunnyColors.Secondary,fontSize=14.sp)
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Action("主操作",id="sample:primary",primary=true,alwaysShowLabel=true) {}
                            Action("次操作",id="sample:secondary",alwaysShowLabel=true) {}
                        }
                        Field("输入框","示例文字",{})
                    }
                }
            }}
        }
        for(dark in listOf(false,true)) for(spec in AppearancePolicy.catalog) {
            rule.runOnIdle {material=spec.id;model.saveSettings(model.settings.copy(darkTheme=dark,appearanceMaterial=spec.id))}
            rule.waitForIdle()
            rule.onNodeWithTag("sample:primary").assertIsDisplayed()
            save(rule.onNodeWithTag("material:sample").captureToImage().asAndroidBitmap(),"material-${spec.id}-$dark.png")
        }
    }

    @Test fun realNavigationUsesConcentricCornersAndKeepsFocusWhenGlassIsDisabled() {
        lateinit var model:AppModel
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false)
            model.saveSettings(AppSettings(darkTheme=false,appearanceColor="rose",appearanceMaterial="glass",highPerformanceThemes=true,performanceMode="standard",reduceMotion=true))
            activity.setContent {CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides false) {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                SunnyTheme(model.settings) {
                    val source=rememberGlassBackdrop(AppearancePolicy.spec(LocalNativeAppearance.current.material).glass)
                    val bridge=remember {NavigationBridge()}
                    Box(Modifier.width(760.dp).height(160.dp).testTag("navigation:fixture")) {
                        Canvas(Modifier.fillMaxSize().captureGlassBackdrop(source)) {
                            drawRect(Color(0xFFF1EADF))
                            for(i in 0..15) drawCircle(if(i%2==0) Color(0xFFAF6681) else Color(0xFF50949E),45.dp.toPx(),Offset((i*60).dp.toPx(),48.dp.toPx()))
                        }
                        CompositionLocalProvider(LocalGlassBackdrop provides source,LocalGlassControls provides (source!=null)) {TopNav(bridge)}
                    }
                }
            }}
        }
        rule.onNodeWithTag("nav:首页").performSemanticsAction(SemanticsActions.RequestFocus)
        for(corner in listOf("tight","default","round")) {
            rule.runOnIdle {model.saveSettings(model.settings.copy(appearanceCorner=corner))}
            rule.waitForIdle()
            rule.onNodeWithTag("nav:首页").assertIsFocused()
            save(rule.onNodeWithTag("navigation:fixture").captureToImage().asAndroidBitmap(),"navigation-glass-$corner.png")
        }
        rule.runOnIdle {model.saveSettings(model.settings.copy(highPerformanceThemes=false))}
        rule.onNodeWithTag("nav:首页").assertIsFocused().performKeyInput {pressKey(Key.DirectionRight)}
        rule.onNodeWithTag("nav:媒体库").assertIsFocused()
        save(rule.onNodeWithTag("navigation:fixture").captureToImage().asAndroidBitmap(),"navigation-fallback.png")
    }

    @Test fun globalGlassControlsKeepTargetsAndPlayerLabelsOutsideTheirButtons() {
        assumeTrue(Build.VERSION.SDK_INT>=33)
        lateinit var model:AppModel
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false)
            model.saveSettings(AppSettings(darkTheme=true,appearanceMaterial="glass",highPerformanceThemes=true,performanceMode="standard",reduceMotion=true))
            activity.setContent {CompositionLocalProvider(LocalAppModel provides model) {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                SunnyTheme(model.settings) {
                    Column(Modifier.width(640.dp).background(SunnyColors.Background).padding(24.dp).testTag("glass:global"),verticalArrangement=Arrangement.spacedBy(24.dp)) {
                        Text("全局玻璃控件",color=SunnyColors.Text,fontSize=23.sp)
                        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            Action("操作按钮",id="global:action",alwaysShowLabel=true) {}
                            FocusTile("global:choice",Modifier.width(130.dp).height(44.dp),active=true,onClick={}) {Text("普通选项",color=SunnyColors.Text)}
                        }
                        Field("输入框","示例文字",{})
                        Box(Modifier.padding(top=46.dp)) {
                            io.github.xudong7587.sunnytv.feature.player.PlayerControl("音轨","audio") {}
                        }
                    }
                }
            }}
        }
        rule.onNodeWithTag("player:audio").performSemanticsAction(SemanticsActions.RequestFocus)
        rule.onNodeWithText("音轨").assertIsDisplayed()
        val bounds=rule.onNodeWithTag("player:audio").fetchSemanticsNode().boundsInRoot
        val layoutSize=rule.onNodeWithTag("player:audio").fetchSemanticsNode().layoutInfo.coordinates.size
        save(rule.onNodeWithTag("glass:global").captureToImage().asAndroidBitmap(),"glass-global-controls.png")
        rule.runOnIdle {model.saveSettings(model.settings.copy(highPerformanceThemes=false))}
        rule.onNodeWithTag("player:audio").assertIsFocused()
        rule.onNodeWithText("音轨").assertIsDisplayed()
        val restored=rule.onNodeWithTag("player:audio").fetchSemanticsNode()
        assertEquals(layoutSize,restored.layoutInfo.coordinates.size)
        assertEquals(bounds.center.x,restored.boundsInRoot.center.x,.05f)
        assertEquals(bounds.center.y,restored.boundsInRoot.center.y,.05f)
    }
}
