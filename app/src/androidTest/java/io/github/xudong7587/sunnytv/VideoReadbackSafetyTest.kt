package io.github.xudong7587.sunnytv

import android.os.Handler
import android.view.PixelCopy
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xudong7587.sunnytv.feature.ambilight.AmbilightController
import io.github.xudong7587.sunnytv.feature.ambilight.LightConfig
import io.github.xudong7587.sunnytv.feature.ambilight.VideoEdgeSampler
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Slow driver completion must stop recurring reads without changing saved user preferences. */
class VideoReadbackSafetyTest {
    @Suppress("UNCHECKED_CAST")
    private fun <T> state(controller:AmbilightController,name:String)=
        AmbilightController::class.java.getDeclaredField(name).apply {isAccessible=true}.get(controller) as MutableStateFlow<T>

    @Test fun slowReadStopsUntilExplicitRetryAndCloseIgnoresLateCompletion() {
        val input=InstrumentationRegistry.getInstrumentation()
        val context=input.targetContext
        assertFalse("Fixture must not use a physical radio",(context.applicationContext as SunnyApp).ambilight.config.value.enabled)
        val lights=AmbilightController(context)
        val config=state<LightConfig>(lights,"_config")
        val connected=state<Boolean>(lights,"_connected")
        val configBefore=context.getSharedPreferences("ambilight",0).all.toMap()
        val ready=CountDownLatch(1)
        val copies=AtomicInteger()
        val callbacks=AtomicInteger()
        val closed=AtomicBoolean()
        lateinit var view:SurfaceView
        var sampler:VideoEdgeSampler?=null
        fun waitFor(message:String,condition:()->Boolean) {
            val deadline=SystemClock.elapsedRealtime()+5000
            while(!condition() && SystemClock.elapsedRealtime()<deadline)Thread.sleep(25)
            assertTrue(message,condition())
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {scenario->
                scenario.onActivity {a->
                    view=SurfaceView(a)
                    view.holder.addCallback(object:SurfaceHolder.Callback {
                        override fun surfaceCreated(holder:SurfaceHolder) {ready.countDown()}
                        override fun surfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int) {}
                        override fun surfaceDestroyed(holder:SurfaceHolder) {}
                    })
                    a.setContentView(view,android.view.ViewGroup.LayoutParams(640,360))
                }
                assertTrue(ready.await(5,TimeUnit.SECONDS))
                input.waitForIdleSync()
                // This controller is not registered with Activity presence. Changing its in-memory
                // flows cannot start BLE; the sampler's real Handler scheduling is under test.
                config.value=LightConfig(enabled=true,sync=true,fps=15)
                connected.value=true
                input.runOnMainSync {
                    sampler=VideoEdgeSampler(lights,{view to view},{true},readPixels={_,_,_,worker,callback->
                        copies.incrementAndGet()
                        worker.postDelayed({callback(PixelCopy.SUCCESS);callbacks.incrementAndGet()},300)
                    }).also {it.start()}
                }
                waitFor("Slow copy must suspend this playback") {lights.metrics.value.contains("已暂停画面同步")}
                Thread.sleep(1200)
                assertEquals("A stalled driver must not be read in a loop",1,copies.get())
                assertTrue("Protection must not turn off the saved preference",config.value.sync)
                assertEquals(configBefore,context.getSharedPreferences("ambilight",0).all)
                config.value=config.value.copy(sync=false)
                waitFor("Disabled sync must reach the sampler") {
                    var active=true
                    input.runOnMainSync {active=VideoEdgeSampler::class.java.getDeclaredField("syncActive").apply {isAccessible=true}.getBoolean(sampler)}
                    !active
                }
                config.value=config.value.copy(sync=true)
                waitFor("Explicit retry must allow one new read") {copies.get()==2}
                lights.note("closed playback")
                input.runOnMainSync {sampler!!.stop();closed.set(true)}
                waitFor("Outstanding completion must drain safely") {callbacks.get()==2}
                Thread.sleep(300)
                assertEquals(2,copies.get())
                assertEquals("Old completion must not overwrite a new status","closed playback",lights.metrics.value)
            }
        } finally {
            if(!closed.get())input.runOnMainSync {sampler?.stop()}
            val worker=AmbilightController::class.java.getDeclaredField("worker").apply {isAccessible=true}.get(lights) as Handler
            worker.looper.quitSafely()
        }
    }
}
