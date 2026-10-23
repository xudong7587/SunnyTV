package io.github.xudong7587.sunnytv

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

@SdkSuppress(minSdkVersion=30)
class LegacyGlassTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun childLayerScrollUpdatesStationaryLens() {
        var scroll by mutableFloatStateOf(0f)
        rule.activityRule.scenario.onActivity {a->
            a.setContent {
                val configuration=android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current).apply {
                    uiMode=(uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK.inv()) or android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
                }
                CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides configuration) {SunnyTheme(AppSettings()) {
                val source=rememberGlassBackdrop(true)!!
                Box(Modifier.size(320.dp,180.dp)) {
                    Box(Modifier.fillMaxSize().captureGlassBackdrop(source)) {
                        Canvas(Modifier.fillMaxSize().graphicsLayer {translationX=scroll}) {
                            drawRect(Color.Red)
                            drawRect(Color.Blue,Offset(size.width/2,0f),Size(size.width/2,size.height))
                        }
                    }
                    Box(Modifier.fillMaxSize().background(Color.Gray))
                    Box(Modifier.offset(24.dp,24.dp).size(100.dp,100.dp)
                        .liquidGlass(source,RoundedCornerShape(12.dp),12.dp,"glass").testTag("scroll:lens"))
                }
            }}}
        }
        fun center():Int {
            val bitmap=rule.onNodeWithTag("scroll:lens").captureToImage().asAndroidBitmap()
            return bitmap.getPixel(bitmap.width/2,bitmap.height/2)
        }
        rule.waitUntil(10_000) {val c=center();(c shr 16 and 255)>180 && (c and 255)<80}
        // Only a child's layer property changes; the capture parent draw need not run again.
        rule.runOnIdle {scroll=-160*rule.activity.resources.displayMetrics.density}
        rule.waitUntil(10_000) {val c=center();(c and 255)>180 && (c shr 16 and 255)<80}
    }
    @Test fun realArtworkPassesThroughLensAndUpdatesWithoutTouchingForegroundGlyphs() {
        var shifted by mutableStateOf(false)
        rule.activityRule.scenario.onActivity {a->
            val model=AppModel(a.application,false)
            model.saveSettings(AppSettings(appearanceMaterial="glass",highPerformanceThemes=true,performanceMode="standard",reduceMotion=true))
            a.setContent {CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {
                val source=rememberGlassBackdrop(true)!!
                Box(Modifier.size(480.dp,240.dp)) {
                    Canvas(Modifier.fillMaxSize().captureGlassBackdrop(source)) {
                        drawRect(if(shifted)Color(0xFF306DB0) else Color(0xFFB44365))
                        drawRect(Color(0xFF9ACA62),Offset(320.dp.toPx(),0f),Size(160.dp.toPx(),size.height))
                    }
                    Box(Modifier.fillMaxSize().background(Color(0xFF777777)))
                    Box(Modifier.padding(24.dp).size(400.dp,160.dp)
                        .liquidGlass(source,RoundedCornerShape(24.dp),24.dp,"glass").testTag("legacy:lens")) {
                        Box(Modifier.padding(20.dp).size(36.dp).background(Color.White))
                    }
                }
            }}}
        }
        fun image()=rule.onNodeWithTag("legacy:lens").captureToImage().asAndroidBitmap()
        var first:Bitmap?=null
        rule.waitUntil(10_000) {first=image();val c=first!!.getPixel(first!!.width/2,first!!.height/2);(c shr 16 and 255)>130 && (c shr 8 and 255)<120}
        rule.runOnIdle {shifted=true}
        var changed:Bitmap?=null
        rule.waitUntil(10_000) {changed=image();val c=changed!!.getPixel(changed!!.width/2,changed!!.height/2);(c and 255)>130 && (c shr 16 and 255)<100}
        for(bitmap in listOf(first!!,changed!!)) {
            val scale=bitmap.width/400f
            val glyph=bitmap.getPixel((30*scale).toInt(),(30*scale).toInt())
            assertTrue("Foreground glyph must remain crisp white",(glyph and 255)>245 && (glyph shr 16 and 255)>245)
            val side=bitmap.getPixel((360*scale).toInt(),(80*scale).toInt())
            assertTrue("Lens must use corresponding page coordinates",(side shr 8 and 255)>120 && (side shr 16 and 255)<200)
        }
        File(rule.activity.getExternalFilesDir(null),"legacy-glass.png").outputStream().use {changed!!.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
