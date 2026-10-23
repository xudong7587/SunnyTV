package io.github.xudong7587.sunnytv.feature.player

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import io.github.xudong7587.sunnytv.feature.ambilight.copyVideoPixels

/** Low-rate full-buffer inspection only in crop mode; three matching samples prevent scene flashes
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
    private val tick=object:Runnable {
        override fun run() {
            if(closed)return
            main.postDelayed(this,750)
            val view=surface() ?: return
            if(inFlight || !view.holder.surface.isValid)return
            val rect=Rect(view.holder.surfaceFrame)
            if(rect.isEmpty)return
            inFlight=true
            copyVideoPixels(view.holder.surface,rect,bitmap,worker) {result->
                val next=if(!closed && result==PixelCopy.SUCCESS) {
                    bitmap.getPixels(pixels,0,160,0,0,160,90);VideoGeometry.picture(pixels,160,90)
                } else null
                main.post {
                    inFlight=false
                    if(closed)dispose() else if(next!=null) {
                        if(next==candidate)repeats++ else {candidate=next;repeats=1}
                        if(repeats>=3 && applied!=candidate) {applied=candidate;changed(applied)}
                    }
                }
            }
        }
    }
    fun start() {main.post(tick)}
    fun close() {if(closed)return;closed=true;main.removeCallbacks(tick);if(!inFlight)dispose()}
    private fun dispose() {if(!bitmap.isRecycled)bitmap.recycle();thread.quitSafely()}
}
