package io.github.xudong7587.sunnytv

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.player.PlayerControl
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Real component captures: glass must not suppress poster/button rings or frost player chrome. */
class GlassFocusRepairTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun glassPosterAndButtonsHaveAccentFramesAndPlayerFaceStaysTransparent() {
        lateinit var model:AppModel
        var accent=0
        val background=Color(0xFF315577)
        rule.activityRule.scenario.onActivity {a->
            model=AppModel(a.application,false)
            model.saveSettings(AppSettings(appearanceMaterial="glass",appearanceColor="rose",
                highPerformanceThemes=true,performanceMode="standard",reduceMotion=true))
            a.setContent {CompositionLocalProvider(LocalAppModel provides model) {SunnyTheme(model.settings) {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                val palette=LocalSunnyPalette.current
                SideEffect {accent=palette.accent.toArgb()}
                Row(Modifier.fillMaxSize().background(background).padding(24.dp).testTag("repair:fixture"),
                    horizontalArrangement=Arrangement.spacedBy(28.dp)) {
                    Box(Modifier.width(132.dp)) {
                        PosterWallSlot(MediaEntry("poster","fixture","海报","Movie"),"Poster",focusId="repair:poster",
                            onNeedContent={},onClick={})
                    }
                    FocusTile("repair:button",Modifier.size(96.dp,48.dp),button=true,onClick={}) {}
                    PlayerControl("音频输出","audio-output",showFocusLabel=false) {}
                }
            }}}
        }
        fun focus(tag:String)=rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
        fun capture(tag:String)=rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        fun close(a:Int,b:Int)=listOf(0,8,16).all {abs((a shr it and 255)-(b shr it and 255))<=12}
        for(dark in listOf(true,false)) {
            rule.runOnIdle {model.saveSettings(model.settings.copy(darkTheme=dark))}
            focus("repair:button")
            val playerIdle=capture("player:audio-output")
            for(tag in listOf("repair:poster","repair:button","player:audio-output")) {
                focus(tag)
                val bitmap=capture(tag)
                File(rule.activity.cacheDir,"repair-${tag.replace(':','-')}-$dark.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
                }
                val mid=bitmap.width/2
                val rows=(1..with(rule.density){4.dp.toPx()}.toInt()).filter {it<bitmap.height}
                assertTrue("$tag must have a visible theme accent frame in dark=$dark: expected=${accent.toUInt().toString(16)} actual=${rows.map {bitmap.getPixel(mid,it).toUInt().toString(16)}}",
                    rows.count {y->close(bitmap.getPixel(mid,y),accent)}>=2)
                if(tag=="player:audio-output") {
                    // This point is inside the face but clear of the center glyph and outer ring.
                    val x=(bitmap.width*.18f).toInt();val y=bitmap.height/2
                    assertEquals("Focus must not add a grey/frosted face",playerIdle.getPixel(x,y),bitmap.getPixel(x,y))
                    assertTrue("Video backdrop stays visible",close(background.toArgb(),bitmap.getPixel(x,y)))
                }
            }
            File(rule.activity.cacheDir,"glass-focus-repair-$dark.png").outputStream().use {
                capture("repair:fixture").compress(Bitmap.CompressFormat.PNG,100,it)
            }
        }
    }
}
