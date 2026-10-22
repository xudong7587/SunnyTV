package io.github.xudong7587.sunnytv

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppearanceLegibilityTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private fun save(bitmap:Bitmap,name:String) {
        File(rule.activity.cacheDir,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun similarPosterRatingHasLightBackingAndBlackInkInDayMode() {
        lateinit var model:AppModel
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false)
            model.saveSettings(AppSettings(darkTheme=false,reduceMotion=true))
            activity.setContent {CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {
                Column(Modifier.fillMaxSize().background(SunnyColors.Background).padding(24.dp).testTag("rating:fixture")) {
                    MediaShelf("相似推荐",listOf(MediaEntry("test","fixture","评分示例","Movie",rating=8.6)),onClick={})
                }
            }}}
        }
        for(dark in listOf(false,true)) {
            rule.runOnIdle {model.saveSettings(model.settings.copy(darkTheme=dark))}
            rule.waitForIdle()
            val badge=rule.onNodeWithTag("poster:rating",useUnmergedTree=true).assertIsDisplayed().captureToImage().asAndroidBitmap()
            save(badge,"rating-badge-$dark.png")
            fun light(pixel:Int):Int=((pixel shr 16 and 255)+(pixel shr 8 and 255)+(pixel and 255))/3
            val backing=light(badge.getPixel(badge.width/2,2))
            assertTrue("Rating backing must follow day/night contrast",if(dark) backing<100 else backing>200)
            val backingLuminance=Color(badge.getPixel(badge.width/2,2)).luminance()
            var ink=0
            for(y in 3 until badge.height-3) for(x in 5 until badge.width-5) {
                val l=Color(badge.getPixel(x,y)).luminance()
                val ratio=(maxOf(l,backingLuminance)+.05f)/(minOf(l,backingLuminance)+.05f)
                if(ratio>=4.5f && (if(dark) l>backingLuminance else l<backingLuminance)) ink++
            }
            assertTrue("Rating text must contrast with its translucent backing: dark=$dark inkPixels=$ink",ink>12)
            save(rule.onNodeWithTag("rating:fixture").captureToImage().asAndroidBitmap(),"similar-rating-$dark.png")
        }
    }
    @Test fun materialOptionsKeepCrispCardDiagramsAtTheirRealSizeInBothThemes() {
        lateinit var model:AppModel
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false)
            model.saveSettings(AppSettings(darkTheme=false,appearanceColor="rose",highPerformanceThemes=true,performanceMode="standard",reduceMotion=true))
            activity.setContent {CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {
                Column(Modifier.width(600.dp).background(SunnyColors.Background).padding(20.dp).testTag("swatch:fixture"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    AppearancePolicy.catalog.chunked(2).forEach {pair->
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            pair.forEach {spec->MaterialPreviewCard(spec,Modifier.weight(1f)) {model.saveSettings(model.settings.copy(appearanceMaterial=spec.id))}}
                        }
                    }
                }
            }}}
        }
        for(dark in listOf(false,true)) {
            rule.runOnIdle {model.saveSettings(model.settings.copy(darkTheme=dark))}
            rule.waitForIdle()
            AppearancePolicy.catalog.forEach {spec->
                val node=rule.onNodeWithTag("material-swatch:${spec.id}",useUnmergedTree=true).assertIsDisplayed()
                val bounds=node.getUnclippedBoundsInRoot()
                assertEquals(42f,(bounds.right-bounds.left).value,.1f)
                assertEquals(42f,(bounds.bottom-bounds.top).value,.1f)
            }
            save(rule.onNodeWithTag("swatch:fixture").captureToImage().asAndroidBitmap(),"material-options-$dark.png")
        }
    }
}
