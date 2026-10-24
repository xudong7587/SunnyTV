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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** Samples the app's own video surface; no MediaProjection and no full-screen recording. */
class VideoEdgeSampler(private val lights:AmbilightController,private val surface:()->Pair<SurfaceView,View>?,
    private val playing:()->Boolean,private val visibleFraction:()->FloatArray?={null},
    private val readPixels:(Surface,Rect?,Bitmap,Handler,(Int)->Unit)->Unit=::copyVideoPixels) {
    private val main=Handler(Looper.getMainLooper())
    private val thread=HandlerThread("sunnytv-edge-colors").apply {start()}
    private val worker=Handler(thread.looper)
    private val owner=Any()
    private val configScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private class Sample(val width:Int,val height:Int) {
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val pixels=IntArray(width*height)
        val visiblePixels=IntArray(width*height)
    }
    private val coarse=Sample(VideoSampleGrid.WIDTH,VideoSampleGrid.HEIGHT)
    private val detailed=Sample(VideoSampleGrid.FINE_WIDTH,VideoSampleGrid.FINE_HEIGHT)
    private val quality=VideoReadbackQuality()
    private var generation=0L
    private var nextCaptureAt=0L
    private var diagnosticAt=0L
    @Volatile private var closed=false
    private var inFlight=false
    private var failures=0
    private var syncActive=false
    private var lastPlayingAt=SystemClock.elapsedRealtime()
    private val tick=object:Runnable {
        override fun run() {
            if(closed)return
            val config=lights.config.value
            try {
                val active=config.enabled && config.sync
                if(!active || !lights.connected.value)return
                if(!playing()) {
                    if(SystemClock.elapsedRealtime()-lastPlayingAt>700)lights.idle(owner)
                    nextCaptureAt=maxOf(nextCaptureAt,SystemClock.elapsedRealtime()+250)
                    return
                }
                lastPlayingAt=SystemClock.elapsedRealtime()
                if(quality.suspended || inFlight || SystemClock.elapsedRealtime()<nextCaptureAt)return
                val (video,container)=surface()?:return
                if(!video.holder.surface.isValid || video.width<=0 || video.height<=0)return
                // Media3 FIT uses the video-sized surface; ZOOM extends it beyond the visible player.
                val a=IntArray(2);val b=IntArray(2);video.getLocationInWindow(a);container.getLocationInWindow(b)
                val rect=Rect(0,0,video.width,video.height)
                if(!rect.intersect(b[0]-a[0],b[1]-a[1],b[0]-a[0]+container.width,b[1]-a[1]+container.height))return
                val wasFine=quality.fine
                val sample=if(wasFine)detailed else coarse
                val width=sample.width;val height=sample.height
                val bitmap=sample.bitmap;val pixels=sample.pixels;val visiblePixels=sample.visiblePixels
                val captureGeneration=generation
                // The holder's surfaceFrame is a layout size, not necessarily the decoder buffer.
                // Copy the whole buffer, then map the visible view region into our small bitmap.
                val bounds=visibleFraction()?.let {area->
                    intArrayOf(kotlin.math.floor(area[0]*width).toInt(),kotlin.math.floor(area[1]*height).toInt(),
                        kotlin.math.ceil(area[2]*width).toInt(),kotlin.math.ceil(area[3]*height).toInt())
                } ?: VideoCrop.bufferRect(video.width,video.height,rect.left,rect.top,rect.right,rect.bottom,width,height)
                val source=video.holder.surface
                val began=SystemClock.elapsedRealtime();inFlight=true
                val callback:(Int)->Unit={result->
                    // Measure on the worker, before waiting for the UI queue to handle completion.
                    val cost=SystemClock.elapsedRealtime()-began
                    val tooSlow=result!=VIDEO_COPY_BUSY && VideoReadbackPacing.blocksPlayback(cost)
                    if(!closed && !tooSlow) {
                        if(result==PixelCopy.SUCCESS) {
                            bitmap.getPixels(pixels,0,width,0,0,width,height)
                            VideoCrop.visiblePixels(pixels,visiblePixels,width,height,bounds)
                            val colors=EdgeColors.sample(visiblePixels,width,height,config.vivid)
                            val now=SystemClock.elapsedRealtime()
                            if(now-diagnosticAt>=5000) {
                                android.util.Log.i("SunnyAmbilight","grid=${width}x${height} full-buffer copy=${now-began}ms region=${bounds.joinToString()} edges=${colors.joinToString {"%06x".format(it)}}")
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
                        if(closed)dispose() else {
                            if((result==PixelCopy.SUCCESS || tooSlow) && captureGeneration==generation) {
                                quality.complete(cost,SystemClock.elapsedRealtime())
                            }
                            if(quality.suspended) {
                                lights.note("当前视频取色过慢（${cost}ms），已暂停画面同步以优先播放；重新开关画面同步可重试")
                                android.util.Log.w("SunnyAmbilight","readback suspended for this playback: copy=${cost}ms")
                            } else if(wasFine && !quality.fine && captureGeneration==generation) {
                                lights.note("当前视频取色较慢（${cost}ms），已切换轻量取色以优先播放")
                                android.util.Log.i("SunnyAmbilight","using coarse color grid: detailed copy=${cost}ms")
                            }
                            if(captureGeneration==generation)
                                nextCaptureAt=SystemClock.elapsedRealtime()+VideoReadbackPacing.cooldown(cost,config.fps)
                            scheduleTick()
                        }
                    }
                }
                readPixels(source,null,bitmap,worker,callback)
            } finally {if(!closed && !inFlight)scheduleTick()}
        }
    }
    private fun scheduleTick() {
        if(closed)return
        val config=lights.config.value
        val now=SystemClock.elapsedRealtime()
        val delay=when {
            !config.enabled || !config.sync || !lights.connected.value -> 500L
            quality.suspended -> 1000L
            nextCaptureAt>now -> (nextCaptureAt-now).coerceAtMost(1000L)
            else -> 1000L/config.fps
        }
        main.postDelayed(tick,delay)
    }
    fun start() {
        lights.startPlayback(owner)
        configScope.launch {
            lights.config.collect {config->
                val active=config.enabled && config.sync
                // Observe the switch itself, rather than miss a quick off/on between polling ticks.
                if(active && !syncActive) {quality.reset();generation++;nextCaptureAt=0L}
                syncActive=active
            }
        }
        main.post(tick)
    }
    fun stop() {
        if(closed)return;closed=true;configScope.cancel();main.removeCallbacks(tick);lights.stopPlayback(owner)
        if(!inFlight)dispose()
    }
    private fun dispose() {
        for(sample in listOf(coarse,detailed))if(!sample.bitmap.isRecycled)sample.bitmap.recycle()
        thread.quitSafely()
    }
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
