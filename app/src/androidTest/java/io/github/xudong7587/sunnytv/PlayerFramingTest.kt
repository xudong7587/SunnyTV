package io.github.xudong7587.sunnytv

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.exoplayer.ExoPlayer
import io.github.xudong7587.sunnytv.core.model.PlaybackRequest
import io.github.xudong7587.sunnytv.feature.player.PlayerActivity
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalTestApi::class)
class PlayerFramingTest {
    @get:Rule val rule=createEmptyComposeRule()
    @Test fun encodedLetterboxCropsSideMarkersAndKeepsCircleRoundWhileStretchDeformsIt() {
        val input=InstrumentationRegistry.getInstrumentation()
        val bytes=input.context.assets.open("quality/framing.mp4").use {it.readBytes()}
        val server=MockWebServer()
        server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse {
            val start=request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
            return MockResponse().setResponseCode(if(start>0)206 else 200).setHeader("Content-Type","video/mp4")
                .apply {if(start>0)setHeader("Content-Range","bytes $start-${bytes.size-1}/${bytes.size}")}
                .setBody(Buffer().write(bytes.copyOfRange(start.coerceIn(0,bytes.size),bytes.size)))
        }}
        server.start()
        fun circle(bitmap:Bitmap):Pair<Int,Int> {
            fun white(x:Int,y:Int):Boolean {val c=bitmap.getPixel(x,y);return (c shr 16 and 255)>210 && (c shr 8 and 255)>210 && (c and 255)>210}
            val cx=bitmap.width/2;val cy=bitmap.height/2
            var l=cx;var r=cx;var t=cy;var b=cy
            while(l>0 && white(l-1,cy))l--;while(r<bitmap.width-1 && white(r+1,cy))r++
            while(t>0 && white(cx,t-1))t--;while(b<bitmap.height-1 && white(cx,b+1))b++
            return r-l+1 to b-t+1
        }
        try {
            ActivityScenario.launch<PlayerActivity>(PlayerActivity.intent(input.targetContext,PlaybackRequest("",server.url("framing.mp4").toString(),"圆形裁切夹具"))).use {scenario->
                rule.waitUntil(20_000) {var ready=false;scenario.onActivity {a->ready=PlayerActivity::class.java.getDeclaredField("rendered").apply {isAccessible=true}.getBoolean(a)};ready}
                var session:ExoPlayer?=null
                scenario.onActivity {a->session=actualPlayerView(a).player as ExoPlayer;session!!.pause()}
                rule.onNodeWithTag("player:frame").performSemanticsAction(SemanticsActions.OnClick)
                for(mode in listOf("裁切","拉伸","适应")) {
                    rule.onNodeWithTag("player:frame").assertContentDescriptionEquals("画面：$mode")
                    var screenshot:Bitmap?=null
                    var lastLog=0L
                    rule.waitUntil(12_000) {
                        screenshot=input.uiAutomation.takeScreenshot()
                        val (w,h)=circle(screenshot!!)
                        val edge=screenshot!!.getPixel(screenshot!!.width/2,10)
                        val visible=(edge and 255)>80
                        if(android.os.SystemClock.elapsedRealtime()-lastLog>1000) {
                            lastLog=android.os.SystemClock.elapsedRealtime()
                            scenario.onActivity {a->
                                val v=actualPlayerView(a);val frame=v.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_content_frame)
                                val area=PlayerActivity::class.java.getDeclaredField("pictureArea").apply {isAccessible=true}.get(a)
                                android.util.Log.i("FramingQA","$mode circle=$w,$h edge=$edge frame=${frame.width},${frame.height} area=$area")
                            }
                            File(input.targetContext.getExternalFilesDir(null),"framing-latest.png").outputStream().use {screenshot!!.compress(Bitmap.CompressFormat.PNG,100,it)}
                        }
                        w>30 && h>30 && when(mode) {
                            "裁切"->visible && abs(w.toFloat()/h-1)<.04f
                            "拉伸"->visible && w.toFloat()/h<.80f
                            else->!visible && abs(w.toFloat()/h-1)<.04f
                        }
                    }
                    val bitmap=screenshot!!
                    if(mode=="裁切") {
                        val edge=bitmap.getPixel(10,bitmap.height/2)
                        assertTrue("Side marker must be cropped, not squeezed into frame",(edge and 255)>100 && (edge shr 16 and 255)<100)
                    }
                    scenario.onActivity {a->assertSame(session,actualPlayerView(a).player)}
                    File(input.targetContext.getExternalFilesDir(null),"framing-$mode.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                    if(mode!="适应")rule.onNodeWithTag("player:frame").performSemanticsAction(SemanticsActions.OnClick)
                }
            }
        } finally {server.shutdown()}
    }
}
