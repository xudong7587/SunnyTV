package io.github.xudong7587.sunnytv.feature.ambilight

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View

/** Samples the app's own video surface; no MediaProjection and no full-screen recording. */
class VideoEdgeSampler(private val lights:AmbilightController,private val surface:()->Pair<SurfaceView,View>?,
    private val playing:()->Boolean) {
    private val main=Handler(Looper.getMainLooper())
    private val thread=HandlerThread("sunnytv-edge-colors").apply {start()}
    private val worker=Handler(thread.looper)
    private val owner=Any()
    private val bitmap=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888)
    private val pixels=IntArray(160*90)
    @Volatile private var closed=false
    private var inFlight=false
    private var failures=0
    private var lastPlayingAt=SystemClock.elapsedRealtime()
    private val tick=object:Runnable {
        override fun run() {
            if(closed)return
            val config=lights.config.value
            main.postDelayed(this,1000L/config.fps)
            if(!config.enabled || !config.sync || !lights.connected.value)return
            if(!playing()) {
                if(SystemClock.elapsedRealtime()-lastPlayingAt>700)lights.idle(owner)
                return
            }
            lastPlayingAt=SystemClock.elapsedRealtime()
            if(inFlight)return
            val (video,container)=surface()?:return
            if(!video.holder.surface.isValid || video.width<=0 || video.height<=0)return
            // Media3 FIT uses the video-sized surface; ZOOM extends it beyond the visible player.
            val a=IntArray(2);val b=IntArray(2);video.getLocationInWindow(a);container.getLocationInWindow(b)
            val rect=Rect(0,0,video.width,video.height)
            if(!rect.intersect(b[0]-a[0],b[1]-a[1],b[0]-a[0]+container.width,b[1]-a[1]+container.height))return
            val began=SystemClock.elapsedRealtime();inFlight=true
            val callback=PixelCopy.OnPixelCopyFinishedListener {result->
                if(!closed) {
                    if(result==PixelCopy.SUCCESS) {
                        bitmap.getPixels(pixels,0,160,0,0,160,90)
                        lights.frame(owner,EdgeColors.sample(pixels,160,90),SystemClock.elapsedRealtime()-began)
                        failures=0
                    } else if(++failures>=5) {lights.idle(owner);lights.note("视频层暂时无法取色（$result），等待画面恢复")}
                }
                main.post {inFlight=false;if(closed)dispose()}
            }
            try {
                if(Build.VERSION.SDK_INT>=26)PixelCopy.request(video,rect,bitmap,callback,worker)
                else PixelCopy.request(video,bitmap,callback,worker)
            } catch(_:IllegalArgumentException) {inFlight=false;lights.idle(owner)}
        }
    }
    fun start() {lights.startPlayback(owner);main.post(tick)}
    fun stop() {
        if(closed)return;closed=true;main.removeCallbacks(tick);lights.stopPlayback(owner)
        if(!inFlight)dispose()
    }
    private fun dispose() {if(!bitmap.isRecycled)bitmap.recycle();thread.quitSafely()}
}
