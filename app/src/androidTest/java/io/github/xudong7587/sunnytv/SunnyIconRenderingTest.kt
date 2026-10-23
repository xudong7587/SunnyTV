package io.github.xudong7587.sunnytv

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
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun everyOriginalVectorRendersAtToolbarAndPlayerSizesWithoutClipping() {
        var selected by mutableStateOf("home")
        var extent by mutableStateOf(20)
        rule.activityRule.scenario.onActivity {activity->activity.setContent {
            // Capture a gutter too: antialiasing may legitimately reach the first viewport pixel at
            // 160dpi. Ink outside the viewport proves overflow; ink inside its edge does not.
            Box(Modifier.size((extent+8).dp).background(Color.White).testTag("icon:viewport").padding(4.dp)) {
                LineIcon(selected,Color.Black,Modifier.fillMaxSize())
            }
        }}
        val failures=mutableListOf<String>()
        for(size in listOf(20,40)) for(name in SunnyIconCatalog.icons.keys) {
            rule.runOnIdle {selected=name;extent=size}
            val image=rule.onNodeWithTag("icon:viewport").captureToImage().asAndroidBitmap()
            val gutter=4f*image.width/(size+8)
            var ink=0
            var clipped=false
            for(y in 0 until image.height) for(x in 0 until image.width) {
                val value=image.getPixel(x,y) and 255
                if(value<200) ink++
                if(x+1<=gutter || y+1<=gutter || x>=image.width-gutter || y>=image.height-gutter)
                    if(value<=220) clipped=true
            }
            if(clipped) failures.add("$name at $size dp clips its viewport")
            if(ink<=20) failures.add("$name at $size dp has no visible glyph")
        }
        assertTrue(failures.joinToString("; "),failures.isEmpty())
    }
}
