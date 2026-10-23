package io.github.xudong7587.sunnytv

import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.tv.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.xudong7587.sunnytv.core.model.AppSettings
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class GlassCompositingTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 @Test fun scrollingLayerPreservesGlassTransparencyInBothThemes() {
  assumeTrue(Build.VERSION.SDK_INT>=33)
  lateinit var model:AppModel
  rule.activityRule.scenario.onActivity {activity->
   model=AppModel(activity.application,false)
   model.saveSettings(AppSettings(appearanceMaterial="glass",highPerformanceThemes=true,performanceMode="standard",reduceMotion=true))
   activity.setContent {CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {
    Row(Modifier.size(520.dp,120.dp).background(Color(0xFF9EB5C4)).testTag("glass:composite").padding(16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     Box(Modifier.size(240.dp,88.dp).materialSurface(sunnyShape(24.dp),control=true))
     Box(Modifier.size(240.dp,88.dp).graphicsLayer {compositingStrategy=CompositingStrategy.Offscreen}
      .materialSurface(sunnyShape(24.dp),control=true))
    }
   }}}
  }
  for(dark in listOf(true,false)) {
   rule.runOnIdle {model.saveSettings(model.settings.copy(darkTheme=dark))}
   rule.waitForIdle()
   val bitmap=rule.onNodeWithTag("glass:composite").captureToImage().asAndroidBitmap()
   File(rule.activity.cacheDir,"glass-compositing-$dark.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
   val scale=bitmap.width/520f
   var largest=0
   for(y in 3 until 85 step 2) for(x in 3 until 237 step 2) {
    val a=bitmap.getPixel(((16+x)*scale).roundToInt(),((16+y)*scale).roundToInt())
    val b=bitmap.getPixel(((264+x)*scale).roundToInt(),((16+y)*scale).roundToInt())
    for(shift in listOf(0,8,16)) largest=maxOf(largest,abs(((a shr shift) and 255)-((b shr shift) and 255)))
   }
   assertTrue("Transparent glass changed when a scrolling layer was introduced: dark=$dark maxDelta=$largest",largest<=3)
  }
 }
 @Test fun pageButtonsSampleRealBackgroundAndKeepArtworkAccentSeparate() {
  assumeTrue(Build.VERSION.SDK_INT>=33)
  lateinit var model:AppModel
  var shifted by mutableStateOf(false)
  var buttonInk=Color.Transparent
  var artworkAccent=Color.Transparent
  rule.activityRule.scenario.onActivity {activity->
   model=AppModel(activity.application,false)
   model.saveSettings(AppSettings(appearanceMaterial="glass",appearanceColor="rose",highPerformanceThemes=true,performanceMode="standard",reduceMotion=true))
   activity.setContent {CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {
    Box(Modifier.size(520.dp,280.dp)) {GlassButtonScene {
     Canvas(Modifier.fillMaxSize().captureButtonSceneBackdrop()) {
      drawRect(if(shifted) Color(0xFF8BA9CF) else Color(0xFFD4B79D))
      for(i in 0..20) drawRect(if(shifted) Color(0xFF406EB0) else Color(0xFFB44365),
       Offset(i*28.dp.toPx(),0f),Size(12.dp.toPx(),size.height))
     }
     // This fixed foreground hides the source. Alpha-only controls cannot see source changes.
     Box(Modifier.fillMaxSize().background(Color(0xFF779181)))
     Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
      FocusTile("optical:button",Modifier.size(220.dp,80.dp),button=true,onClick={}) {
       val ink=SunnyColors.Text
       SideEffect {buttonInk=ink}
       Text("按钮",color=ink)
      }
      FocusTile("optical:artwork",Modifier.size(220.dp,80.dp),artwork=true,onClick={}) {
       val accent=SunnyColors.Accent
       SideEffect {artworkAccent=accent}
       Box(Modifier.fillMaxSize().background(Color(0xFF536573)))
      }
     }
    }}
   }}}
  }
  for(dark in listOf(true,false)) {
   rule.runOnIdle {model.saveSettings(model.settings.copy(darkTheme=dark));shifted=false}
   rule.waitForIdle()
   rule.onNodeWithTag("optical:button").performSemanticsAction(SemanticsActions.RequestFocus)
   val before=rule.onNodeWithTag("optical:button").captureToImage().asAndroidBitmap()
   rule.runOnIdle {
    assertEquals(if(dark) Color.White else Color.Black,buttonInk)
    assertEquals(palette(model.settings.copy(appearanceMaterial="clear")).accent,artworkAccent)
    assertEquals(dark,model.settings.darkTheme)
    shifted=true
   }
   rule.waitForIdle()
   val after=rule.onNodeWithTag("optical:button").captureToImage().asAndroidBitmap()
   var changed=0
   for(y in 6 until before.height-6 step 4) for(x in 6 until before.width-6 step 4)
    if(before.getPixel(x,y)!=after.getPixel(x,y)) changed++
   assertTrue("Page button must sample the registered artwork rather than tint only",changed>100)
   rule.onNodeWithTag("optical:artwork").performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
   val idle=rule.onNodeWithTag("optical:button").captureToImage().asAndroidBitmap()
   assertNotEquals("Selection must change surface opacity",after.getPixel(after.width/2,8),idle.getPixel(idle.width/2,8))
   File(rule.activity.cacheDir,"glass-page-buttons-$dark.png").outputStream().use {rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  }
 }
}
