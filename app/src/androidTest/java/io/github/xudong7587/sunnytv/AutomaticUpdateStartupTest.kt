package io.github.xudong7587.sunnytv

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xudong7587.sunnytv.core.storage.ConfigStore
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class AutomaticUpdateStartupTest {
    @Test fun hiddenDavDoesNotContactSavedServerEvenWhenPreviouslyEnabled() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=ConfigStore(context);val previous=store.updateConfig();val auto=store.automaticUpdates();val enabled=store.webDavUpdatesEnabled()
        ServerSocket(0,2,InetAddress.getByName("127.0.0.1")).use {server->
            server.soTimeout=1000
            try {
                store.saveUpdateConfig("http://127.0.0.1:${server.localPort}/dav/","fixture-user","fixture-password")
                store.setAutomaticUpdates(true);store.setWebDavUpdatesEnabled(true)
                ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use {scenario->
                    scenario.moveToState(Lifecycle.State.CREATED);scenario.moveToState(Lifecycle.State.RESUMED)
                    assertTrue(runCatching {server.accept().close()}.exceptionOrNull() is SocketTimeoutException)
                }
                assertTrue(store.automaticUpdates())
            } finally {store.saveUpdateConfig(previous.first,previous.second,previous.third);store.setAutomaticUpdates(auto);store.setWebDavUpdatesEnabled(enabled)}
        }
    }
}

