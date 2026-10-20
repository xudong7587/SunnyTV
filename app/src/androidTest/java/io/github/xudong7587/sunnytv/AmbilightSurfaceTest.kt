package io.github.xudong7587.sunnytv

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xudong7587.sunnytv.core.model.PlaybackRequest
import io.github.xudong7587.sunnytv.feature.ambilight.EdgeColors
import io.github.xudong7587.sunnytv.feature.player.PlayerActivity
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Real Media3 decode + SurfaceView PixelCopy, with a synthetic public test pattern only. */
@SdkSuppress(minSdkVersion=26)
class AmbilightSurfaceTest {
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun playingVideoSurfaceYieldsFourDifferentEdgeColors() {
        val input=InstrumentationRegistry.getInstrumentation()
        val data=input.context.assets.open("ambilight.mp4").use {it.readBytes()}
        val server=ServerSocket(0,8,InetAddress.getByName("127.0.0.1"))
        val acceptor=Thread {
            while(!server.isClosed) {
                val socket=runCatching {server.accept()}.getOrNull()?:break
                Thread {socket.use {runCatching {
                    val reader=it.getInputStream().bufferedReader();reader.readLine()?:return@runCatching
                    var start=0
                    while(true) {
                        val line=reader.readLine()?:break;if(line.isEmpty())break
                        if(line.startsWith("Range: bytes=",true))start=line.substringAfter('=').substringBefore('-').toInt()
                    }
                    val offset=start.coerceIn(0,data.size-1)
                    val header="HTTP/1.1 ${if(start>0) "206 Partial Content" else "200 OK"}\r\nContent-Type: video/mp4\r\nContent-Length: ${data.size-offset}\r\nAccept-Ranges: bytes\r\n"+
                        (if(start>0) "Content-Range: bytes $offset-${data.size-1}/${data.size}\r\n" else "")+"Connection: close\r\n\r\n"
                    it.getOutputStream().apply {write(header.toByteArray());write(data,offset,data.size-offset);flush()}
                }}}.apply {isDaemon=true;start()}
            }
        }.apply {isDaemon=true;start()}
        val thread=HandlerThread("edge-test").apply {start()}
        val bitmap=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888)
        val ready=CountDownLatch(1)
        var copyResult=-99
        try {
            val request=PlaybackRequest("","http://127.0.0.1:${server.localPort}/pattern.mp4","Four-edge fixture")
            ActivityScenario.launch<PlayerActivity>(PlayerActivity.intent(input.targetContext,request)).use {scenario->
                val deadline=System.currentTimeMillis()+15000
                var rendered=false
                while(!rendered && System.currentTimeMillis()<deadline) {
                    scenario.onActivity {activity->rendered=PlayerActivity::class.java.getDeclaredField("rendered").apply {isAccessible=true}.getBoolean(activity)}
                    if(!rendered)Thread.sleep(100)
                }
                assertTrue("Actual Media3 first frame required",rendered)
                scenario.onActivity {activity->
                    val view=PlayerActivity::class.java.getDeclaredField("lightPlayerView").apply {isAccessible=true}.get(activity) as PlayerView
                    val surface=view.videoSurfaceView as SurfaceView
                    PixelCopy.request(surface,bitmap,{result->copyResult=result;ready.countDown()},Handler(thread.looper))
                }
                assertTrue("PixelCopy timed out",ready.await(5,TimeUnit.SECONDS))
                assertEquals(PixelCopy.SUCCESS,copyResult)
                val pixels=IntArray(160*90);bitmap.getPixels(pixels,0,160,0,0,160,90)
                val colors=EdgeColors.sample(pixels,160,90)
                assertTrue("Red top",(colors[0] shr 16 and 255)>180 && (colors[0] and 255)<40)
                assertTrue("Green right",(colors[1] shr 8 and 255)>80 && (colors[1] and 255)<80)
                assertTrue("Blue bottom",(colors[2] and 255)>180 && (colors[2] shr 16 and 255)<40)
                assertTrue("Yellow left",(colors[3] shr 16 and 255)>180 && (colors[3] shr 8 and 255)>180)
            }
        } finally {server.close();acceptor.join(1000);thread.quitSafely();bitmap.recycle()}
    }
}
