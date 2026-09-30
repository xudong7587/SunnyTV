package io.github.xudong7587.sunnytv

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xudong7587.sunnytv.core.storage.ConfigStore
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class AutomaticUpdateStartupTest {
    @Test fun enabledUpdateChecksAgainWhenAppIsStartedFromBackground() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=ConfigStore(context);val previous=store.updateConfig();val auto=store.automaticUpdates()
        val requests=LinkedBlockingQueue<String>()
        ServerSocket(0,2,InetAddress.getByName("127.0.0.1")).use {server->
            server.soTimeout=10000
            val worker=Thread {
                runCatching {repeat(2) {server.accept().use {socket->
                    socket.soTimeout=5000
                    val reader=socket.getInputStream().bufferedReader();val request=reader.readLine()
                    while(!reader.readLine().isNullOrEmpty()) {}
                    val body=JSONObject().put("applicationId",context.packageName).put("versionCode",BuildConfig.VERSION_CODE).toString().toByteArray()
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray()+body)
                    requests.put(request)
                }}}
            }.apply {isDaemon=true;start()}
            try {
                store.saveUpdateConfig("http://127.0.0.1:${server.localPort}/dav/","fixture-user","fixture-password")
                store.setAutomaticUpdates(true)
                ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use {scenario->
                    assertEquals("GET /dav/latest.json HTTP/1.1",requests.poll(8,TimeUnit.SECONDS))
                    scenario.moveToState(Lifecycle.State.CREATED)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    assertEquals("GET /dav/latest.json HTTP/1.1",requests.poll(8,TimeUnit.SECONDS))
                }
            } finally {store.saveUpdateConfig(previous.first,previous.second,previous.third);store.setAutomaticUpdates(auto);server.close();worker.join(1000)}
        }
    }
}
