package io.github.xudong7587.sunnytv

import android.view.View
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import io.github.xudong7587.sunnytv.core.model.PlaybackRequest
import io.github.xudong7587.sunnytv.feature.player.PlayerActivity
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalTestApi::class)
class PlayerResizeTest {
 @get:Rule val rule=createEmptyComposeRule()
 private fun player(a:PlayerActivity)=PlayerActivity::class.java.getDeclaredMethod("getPlayer").apply {isAccessible=true}.invoke(a) as ExoPlayer
 @Test fun remoteResizeUpdatesRealVideoAndSurvivesActivityRecreation() {
  val instrument=InstrumentationRegistry.getInstrumentation()
  val bytes=instrument.context.assets.open("quality/original.mp4").use {it.readBytes()}
  val server=MockWebServer()
  server.dispatcher=object:Dispatcher() {
   override fun dispatch(request:RecordedRequest):MockResponse {
    val start=request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
    val from=start.coerceIn(0,bytes.size)
    return MockResponse().setResponseCode(if(from>0) 206 else 200).setHeader("Content-Type","video/mp4")
     .apply {if(from>0) setHeader("Content-Range","bytes $from-${bytes.size-1}/${bytes.size}")}
     .setBody(Buffer().write(bytes.copyOfRange(from,bytes.size)))
   }
  }
  server.start()
  try {
   val context=instrument.targetContext
   ActivityScenario.launch<PlayerActivity>(PlayerActivity.intent(context,PlaybackRequest("",server.url("fixture.mp4").toString(),"画面比例测试"))).use {scenario->
    rule.waitUntil(20_000) {var ready=false;scenario.onActivity {a->ready=player(a).videoSize.width>0 && PlayerActivity::class.java.getDeclaredField("rendered").apply {isAccessible=true}.getBoolean(a)};ready}
    var original:ExoPlayer?=null
    var aspect=0f
    scenario.onActivity {a->original=player(a);original!!.pause();val size=original!!.videoSize;aspect=size.width*size.pixelWidthHeightRatio/size.height}
    instrument.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
    rule.onNodeWithTag("player:frame").performSemanticsAction(SemanticsActions.RequestFocus)
    rule.onNodeWithTag("player:frame").performKeyInput {pressKey(Key.DirectionCenter)}
    for((mode,label) in listOf(AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "裁切",AspectRatioFrameLayout.RESIZE_MODE_FILL to "拉伸",AspectRatioFrameLayout.RESIZE_MODE_FIT to "适应")) {
     rule.waitUntil(5000) {
      var applied=false
      scenario.onActivity {a->
       val v=actualPlayerView(a)
       val frame=v.findViewById<View>(androidx.media3.ui.R.id.exo_content_frame)
       applied=v.resizeMode==mode && frame.width>0 && frame.height>0 && when(mode) {
        AspectRatioFrameLayout.RESIZE_MODE_FILL->abs(frame.width-v.width)<=2 && abs(frame.height-v.height)<=2
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM->frame.width>=v.width-2 && frame.height>=v.height-2 && abs(frame.width.toFloat()/frame.height-aspect)<.02f
        else->frame.width<=v.width+2 && frame.height<=v.height+2 && abs(frame.width.toFloat()/frame.height-aspect)<.02f
       }
       assertSame("Resize must keep the playing surface/player session",original,player(a))
      };applied
     }
     // Capture the actual window, including SurfaceView. Compose's capture alone cannot prove
     // that the separately composed video plane changed, even when the view bounds are correct.
     var screenshot:android.graphics.Bitmap?=null
     rule.waitUntil(5000) {
      screenshot?.recycle()
      screenshot=instrument.uiAutomation.takeScreenshot()
      val bitmap=screenshot!!
      val pixel=bitmap.getPixel((bitmap.width*.01f).toInt(),(bitmap.height*.4f).toInt())
      val channels=(pixel shr 16 and 255)+(pixel shr 8 and 255)+(pixel and 255)
      if(mode==AspectRatioFrameLayout.RESIZE_MODE_FIT) channels<30 else channels>90
     }
     File(instrument.targetContext.cacheDir,"resize-$mode.png").outputStream().use {screenshot!!.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
     screenshot?.recycle()
     rule.onNodeWithTag("player:frame").assertContentDescriptionEquals("画面：$label").performKeyInput {pressKey(Key.DirectionCenter)}
    }
    // The loop ends back on ZOOM. A display/configuration recreation must not silently reset FIT.
    rule.onNodeWithTag("player:frame").assertContentDescriptionEquals("画面：裁切")
    scenario.recreate()
    rule.onNodeWithTag("player:frame").assertContentDescriptionEquals("画面：裁切")
    rule.waitUntil(20_000) {var ready=false;scenario.onActivity {a->ready=actualPlayerView(a).resizeMode==AspectRatioFrameLayout.RESIZE_MODE_ZOOM && player(a).videoSize.width>0};ready}
   }
  } finally {server.shutdown()}
 }
}
