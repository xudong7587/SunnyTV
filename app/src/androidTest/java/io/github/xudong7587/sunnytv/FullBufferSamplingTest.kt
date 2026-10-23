package io.github.xudong7587.sunnytv

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xudong7587.sunnytv.feature.ambilight.EdgeColors
import io.github.xudong7587.sunnytv.feature.ambilight.copyVideoPixels
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class FullBufferSamplingTest {
    @Test fun fourKBufferKeepsBottomAndRightEdgesInSmallerView() {
        val instrument=InstrumentationRegistry.getInstrumentation()
        val ready=CountDownLatch(1)
        lateinit var view:SurfaceView
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {activity->
                view=SurfaceView(activity)
                view.holder.setFixedSize(3840,2160)
                view.holder.addCallback(object:SurfaceHolder.Callback {
                    override fun surfaceCreated(holder:SurfaceHolder) {}
                    override fun surfaceDestroyed(holder:SurfaceHolder) {}
                    override fun surfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int) {
                        val canvas=holder.lockCanvas() ?: return
                        try {
                            val paint=Paint()
                            canvas.drawColor(Color.GRAY)
                            paint.color=Color.RED;canvas.drawRect(0f,0f,3840f,216f,paint)
                            paint.color=Color.BLUE;canvas.drawRect(0f,1944f,3840f,2160f,paint)
                            paint.color=Color.YELLOW;canvas.drawRect(0f,216f,384f,1944f,paint)
                            paint.color=Color.GREEN;canvas.drawRect(3456f,216f,3840f,1944f,paint)
                        } finally {holder.unlockCanvasAndPost(canvas)}
                        ready.countDown()
                    }
                })
                activity.setContentView(view,android.view.ViewGroup.LayoutParams(960,540))
            }
            assertTrue("4K buffer must be drawn",ready.await(10,TimeUnit.SECONDS))
            instrument.waitForIdleSync()
            val thread=HandlerThread("full-buffer-test").apply {start()}
            val image=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888)
            val copied=CountDownLatch(1);val result=AtomicInteger(-99)
            try {
                copyVideoPixels(view.holder.surface,null,image,Handler(thread.looper)) {code->result.set(code);copied.countDown()}
                assertTrue(copied.await(10,TimeUnit.SECONDS));assertEquals(PixelCopy.SUCCESS,result.get())
                val pixels=IntArray(160*90);image.getPixels(pixels,0,160,0,0,160,90)
                val colors=EdgeColors.sample(pixels,160,90)
                assertTrue("bottom blue must not be lost",(colors[2] and 255)>220 && (colors[2] shr 16 and 255)<30)
                assertTrue("right green must not sample the centre",(colors[1] shr 8 and 255)>180 && (colors[1] shr 16 and 255)<50)
            } finally {thread.quitSafely();thread.join(2000);image.recycle()}
        }
    }
}
