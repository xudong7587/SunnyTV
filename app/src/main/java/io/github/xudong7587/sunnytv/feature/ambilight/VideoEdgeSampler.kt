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
import android.view.Surface
import android.view.View

/** Samples the app's own video surface; no MediaProjection and no full-screen recording. */
class VideoEdgeSampler(private val lights:AmbilightController,private val surface:()->Pair<SurfaceView,View>?,
    private val playing:()->Boolean,private val visibleFraction:()->FloatArray?={null}) {
    private val main=Handler(Looper.getMainLooper())
    private val thread=HandlerThread("sunnytv-edge-colors").apply {start()}
    private val worker=Handler(thread.looper)
    private val owner=Any()
    private val bitmap=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888)
    private val pixels=IntArray(160*90)
    private val visiblePixels=IntArray(160*90)
    private var nextCaptureAt=0L
    private var diagnosticAt=0L
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
            if(inFlight || SystemClock.elapsedRealtime()<nextCaptureAt)return
            val (video,container)=surface()?:return
            if(!video.holder.surface.isValid || video.width<=0 || video.height<=0)return
            // Media3 FIT uses the video-sized surface; ZOOM extends it beyond the visible player.
            val a=IntArray(2);val b=IntArray(2);video.getLocationInWindow(a);container.getLocationInWindow(b)
            val rect=Rect(0,0,video.width,video.height)
            if(!rect.intersect(b[0]-a[0],b[1]-a[1],b[0]-a[0]+container.width,b[1]-a[1]+container.height))return
            // The holder's surfaceFrame is a layout size, not necessarily the decoder buffer.
            // Copy the whole buffer, then map the visible view region into our small bitmap.
            val bounds=visibleFraction()?.let {area->
                intArrayOf(kotlin.math.floor(area[0]*160).toInt(),kotlin.math.floor(area[1]*90).toInt(),
                    kotlin.math.ceil(area[2]*160).toInt(),kotlin.math.ceil(area[3]*90).toInt())
            } ?: VideoCrop.bufferRect(video.width,video.height,rect.left,rect.top,rect.right,rect.bottom,160,90)
            val source=video.holder.surface
            val began=SystemClock.elapsedRealtime();inFlight=true
            val callback:(Int)->Unit={result->
                if(!closed) {
                    if(result==PixelCopy.SUCCESS) {
                        bitmap.getPixels(pixels,0,160,0,0,160,90)
                        VideoCrop.visiblePixels(pixels,visiblePixels,160,90,bounds)
                        val colors=EdgeColors.sample(visiblePixels,160,90,config.vivid)
                        val now=SystemClock.elapsedRealtime()
                        if(now-diagnosticAt>=5000) {
                            android.util.Log.i("SunnyAmbilight","full-buffer copy=${now-began}ms region=${bounds.joinToString()} edges=${colors.joinToString {"%06x".format(it)}}")
                            diagnosticAt=now
                        }
                        lights.frame(owner,colors,now-began)
                        failures=0
                    } else if(result!=VIDEO_COPY_BUSY && ++failures==5) {
                        // Keep the last stable color. Alternating PixelCopy failures/successes must
                        // never switch the strip between fixed color and video color repeatedly.
                        lights.note("视频层暂时无法取色（$result），保持当前颜色并等待画面恢复")
                    }
                }
                main.post {
                    inFlight=false
                    val cost=SystemClock.elapsedRealtime()-began
                    nextCaptureAt=SystemClock.elapsedRealtime()+VideoReadbackPacing.cooldown(cost,config.fps)
                    if(closed)dispose()
                }
            }
            copyVideoPixels(source,null,bitmap,worker,callback)
        }
    }
    fun start() {lights.startPlayback(owner);main.post(tick)}
    fun stop() {
        if(closed)return;closed=true;main.removeCallbacks(tick);lights.stopPlayback(owner)
        if(!inFlight)dispose()
    }
    private fun dispose() {if(!bitmap.isRecycled)bitmap.recycle();thread.quitSafely()}
}

/** Android 11 performs the native copy synchronously before posting its callback.
 * Both lamp sampling and framing share one readback budget; never submit them concurrently. */
internal const val VIDEO_COPY_BUSY=-1
private val videoCopyInFlight=java.util.concurrent.atomic.AtomicBoolean(false)
internal fun copyVideoPixels(surface:Surface,rect:Rect?,bitmap:Bitmap,worker:Handler,callback:(Int)->Unit) {
    worker.post {
        if(!videoCopyInFlight.compareAndSet(false,true)) {
            callback(VIDEO_COPY_BUSY)
            return@post
        }
        val completion=PixelCopy.OnPixelCopyFinishedListener {result->
            videoCopyInFlight.set(false)
            callback(result)
        }
        try {
            if(Build.VERSION.SDK_INT>=26)PixelCopy.request(surface,rect,bitmap,completion,worker)
            else PixelCopy.request(surface,bitmap,completion,worker)
        } catch(_:IllegalArgumentException) {completion.onPixelCopyFinished(PixelCopy.ERROR_SOURCE_INVALID)}
    }
}
