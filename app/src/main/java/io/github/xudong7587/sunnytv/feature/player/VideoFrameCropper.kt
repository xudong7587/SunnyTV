package io.github.xudong7587.sunnytv.feature.player

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import io.github.xudong7587.sunnytv.feature.ambilight.VideoReadbackPacing
import android.view.PixelCopy
import android.view.SurfaceView
import io.github.xudong7587.sunnytv.feature.ambilight.copyVideoPixels

/** Bounded full-buffer inspection in crop/stretch modes; three matching samples prevent scene flashes
 * from changing framing. Closing waits for PixelCopy before releasing its reusable buffer. */
internal class VideoFrameCropper(private val surface:()->SurfaceView?,private val changed:(PictureArea)->Unit) {
    private val main=Handler(Looper.getMainLooper())
    private val thread=HandlerThread("sunnytv-framing").apply {start()}
    private val worker=Handler(thread.looper)
    private val bitmap=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888)
    private val pixels=IntArray(160*90)
    @Volatile private var closed=false
    private var inFlight=false
    private var candidate=PictureArea()
    private var repeats=0
    private var applied=PictureArea()
    private var samples=0
    private var settled=false
    private var nextCaptureAt=0L
    private val tick=object:Runnable {
        override fun run() {
            if(closed || settled)return
            main.postDelayed(this,750)
            if(SystemClock.elapsedRealtime()<nextCaptureAt)return
            val view=surface() ?: return
            if(inFlight || !view.holder.surface.isValid)return
            inFlight=true
            val began=SystemClock.elapsedRealtime()
            copyVideoPixels(view.holder.surface,null,bitmap,worker) {result->
                val next=if(!closed && result==PixelCopy.SUCCESS) {
                    bitmap.getPixels(pixels,0,160,0,0,160,90);VideoGeometry.picture(pixels,160,90)
                } else null
                main.post {
                    inFlight=false
                    nextCaptureAt=SystemClock.elapsedRealtime()+VideoReadbackPacing.cooldown(SystemClock.elapsedRealtime()-began,1)
                    if(closed)dispose() else if(next!=null) {
                        samples++
                        if(next==candidate)repeats++ else {candidate=next;repeats=1}
                        if(repeats>=3 && applied!=candidate) {applied=candidate;changed(applied)}
                        // Once bars are measured, framing need not keep reading the 4K decoder.
                        // A fresh mode selection or player recreates this detector.
                        if((repeats>=3 && applied!=PictureArea()) || samples>=12)settled=true
                    }
                }
            }
        }
    }
    fun start() {main.post(tick)}
    fun close() {if(closed)return;closed=true;main.removeCallbacks(tick);if(!inFlight)dispose()}
    private fun dispose() {if(!bitmap.isRecycled)bitmap.recycle();thread.quitSafely()}
}
