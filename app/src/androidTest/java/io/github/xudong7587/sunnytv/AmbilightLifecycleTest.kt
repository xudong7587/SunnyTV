package io.github.xudong7587.sunnytv

import android.os.Handler
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xudong7587.sunnytv.feature.ambilight.AmbilightController
import io.github.xudong7587.sunnytv.feature.ambilight.LightConfig
import io.github.xudong7587.sunnytv.feature.ambilight.LightPresence
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Exercises the Application callback registration, including background and return, without a radio. */
class AmbilightLifecycleTest {
    private fun idleColor(controller:AmbilightController):Int {
        val ready=CountDownLatch(1);var color=-1
        val worker=AmbilightController::class.java.getDeclaredField("worker").apply {isAccessible=true}.get(controller) as Handler
        worker.post {
            val presence=AmbilightController::class.java.getDeclaredField("presence").apply {isAccessible=true}.get(controller) as LightPresence
            color=presence.idleColor(LightConfig(fixedColor=0xff4d05));ready.countDown()
        }
        assertTrue("Lifecycle callback must reach the lamp worker",ready.await(3,TimeUnit.SECONDS))
        return color
    }
    @Test fun actualActivityStopAndRestartReachAppWideLampOwner() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val controller=(context.applicationContext as SunnyApp).ambilight
        assertFalse("Fixture must not connect to physical lights",controller.config.value.enabled)
        ActivityScenario.launch(ComponentActivity::class.java).use {scenario->
            assertEquals(0xff4d05,idleColor(controller))
            scenario.moveToState(Lifecycle.State.CREATED)
            assertEquals(0,idleColor(controller))
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(0xff4d05,idleColor(controller))
        }
        assertEquals(0,idleColor(controller))
    }
}
