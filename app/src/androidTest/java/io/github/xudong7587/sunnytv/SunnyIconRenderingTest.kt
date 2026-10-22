package io.github.xudong7587.sunnytv

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SunnyIconRenderingTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun everyOriginalVectorRendersAtToolbarAndPlayerSizesWithoutClipping() {
        var selected by mutableStateOf("home")
        var extent by mutableStateOf(20)
        rule.activityRule.scenario.onActivity {activity->activity.setContent {
            Box(Modifier.size(extent.dp).background(Color.White).testTag("icon:viewport")) {
                LineIcon(selected,Color.Black,Modifier.fillMaxSize())
            }
        }}
        val failures=mutableListOf<String>()
        for(size in listOf(20,40)) for(name in SunnyIconCatalog.icons.keys) {
            rule.runOnIdle {selected=name;extent=size}
            val image=rule.onNodeWithTag("icon:viewport").captureToImage().asAndroidBitmap()
            var ink=0
            var clipped=false
            for(y in 0 until image.height) for(x in 0 until image.width) {
                val value=image.getPixel(x,y) and 255
                if(value<200) ink++
                if(x==0 || y==0 || x==image.width-1 || y==image.height-1)
                    if(value<=220) clipped=true
            }
            if(clipped) failures.add("$name at $size dp clips its viewport")
            if(ink<=20) failures.add("$name at $size dp has no visible glyph")
        }
        assertTrue(failures.joinToString("; "),failures.isEmpty())
    }
}
