package io.github.xudong7587.sunnytv

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
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
class GlassMaterialRevisionTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 private fun save(bitmap:Bitmap,name:String) {File(rule.activity.cacheDir,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
 @Test fun liquidControlCenterAddsAtMostTenPercentTintWhileFocusStaysVisible() {
  assumeTrue(android.os.Build.VERSION.SDK_INT>=33)
  lateinit var model:AppModel
  val backdrop=Color(0xFF527BA5)
  rule.activityRule.scenario.onActivity {a->
   model=AppModel(a.application,false)
   model.saveSettings(AppSettings(appearanceMaterial="glass",highPerformanceThemes=true,performanceMode="standard",reduceMotion=true))
   a.setContent {CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {
    Column(Modifier.size(420.dp,220.dp).background(backdrop).padding(20.dp).testTag("opacity:fixture"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
     FocusTile("opacity:glass",Modifier.size(280.dp,72.dp),button=true,onClick={}) {}
     FocusTile("opacity:other",Modifier.size(280.dp,72.dp),button=true,onClick={}) {}
    }
   }}}
  }
  for(dark in listOf(false,true)) {
   rule.runOnIdle {model.saveSettings(model.settings.copy(darkTheme=dark))}
   rule.onNodeWithTag("opacity:other").performSemanticsAction(SemanticsActions.RequestFocus)
   val idle=rule.onNodeWithTag("opacity:glass").captureToImage().asAndroidBitmap()
   rule.onNodeWithTag("opacity:glass").performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
   val focused=rule.onNodeWithTag("opacity:glass").captureToImage().asAndroidBitmap()
   val original=backdrop.toArgb()
   val center=focused.getPixel(focused.width/2,focused.height/2)
   val idleCenter=idle.getPixel(idle.width/2,idle.height/2)
   for(shift in listOf(0,8,16)) {
    assertTrue("Focused centre must stay within 10% surface coverage",abs((center shr shift and 255)-(original shr shift and 255))<=27)
   }
   assertNotEquals("Focus must still change the transparent face",idleCenter,center)
   save(rule.onNodeWithTag("opacity:fixture").captureToImage().asAndroidBitmap(),"glass-opacity-$dark.png")
  }
 }
 @Test fun revisedGlassMaterialsRenderControlsAndPanelsWithClearLabels() {
  assumeTrue(android.os.Build.VERSION.SDK_INT>=33)
  lateinit var model:AppModel
  rule.activityRule.scenario.onActivity {a->
   model=AppModel(a.application,false)
   model.saveSettings(AppSettings(appearanceMaterial="glass",appearanceColor="rose",highPerformanceThemes=true,performanceMode="standard",reduceMotion=true))
   a.setContent {CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {
    val source=rememberGlassBackdrop(true)!!
    Box(Modifier.size(520.dp,360.dp).testTag("revision:fixture")) {
     Canvas(Modifier.fillMaxSize().captureGlassBackdrop(source)) {
      drawRect(Color(0xFF93A8BA))
      for(i in 0..20) drawRect(if(i%2==0) Color(0xFFC5B48F) else Color(0xFF477C95),Offset(i*28.dp.toPx(),0f),Size(12.dp.toPx(),size.height))
     }
     CompositionLocalProvider(LocalGlassBackdrop provides source) {
      Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
       Box(Modifier.size(440.dp,128.dp).sunnyPanel(22.dp)) {Text("${AppearancePolicy.spec(model.settings.appearanceMaterial).name} · 面板",color=SunnyColors.Text,modifier=Modifier.padding(24.dp).testTag("revision:label"))}
       Action("操作按钮",id="revision:action",alwaysShowLabel=true) {}
       Field("输入框","示例文字",{})
      }
     }
    }
   }}}
  }
  for(dark in listOf(false,true)) for(material in listOf("glass","frosted","aurora")) {
   rule.runOnIdle {model.saveSettings(model.settings.copy(darkTheme=dark,appearanceMaterial=material))}
   rule.waitForIdle()
   rule.onNodeWithTag("revision:label").assertIsDisplayed()
   rule.onNodeWithTag("revision:action").assertIsDisplayed()
   save(rule.onNodeWithTag("revision:fixture").captureToImage().asAndroidBitmap(),"revised-$material-$dark.png")
  }
 }
}
