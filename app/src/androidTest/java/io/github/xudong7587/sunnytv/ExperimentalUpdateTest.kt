package io.github.xudong7587.sunnytv

import androidx.test.platform.app.InstrumentationRegistry
import io.github.xudong7587.sunnytv.core.storage.ConfigStore
import io.github.xudong7587.sunnytv.feature.update.ExperimentalUpdater
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject

class ExperimentalUpdateTest {
    private fun response(body:String,action:(String)->Unit) {
        val failure=AtomicReference<Throwable?>()
        ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use {server->
            server.soTimeout=5000
            val worker=Thread {
                try {server.accept().use {socket->
                    socket.soTimeout=5000
                    val reader=socket.getInputStream().bufferedReader()
                    while(!reader.readLine().isNullOrEmpty()) {}
                    val bytes=body.toByteArray()
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray()+bytes)
                }} catch(e:Throwable) {failure.set(e)}
            }.apply {start()}
            try {action("http://127.0.0.1:${server.localPort}/dav/")} finally {worker.join(6000)}
            failure.get()?.let {throw it}
        }
    }
    @Test fun encryptedDavConfigPersistsAndCurrentVersionDoesNotDownloadApk()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=ConfigStore(context);val previous=store.updateConfig()
        try {
            val body=JSONObject().put("applicationId",context.packageName).put("versionCode",BuildConfig.VERSION_CODE).toString()
            response(body) {address->
                store.saveUpdateConfig(address,"fixture-user","fixture-password")
                assertEquals(Triple(address,"fixture-user","fixture-password"),ConfigStore(context).updateConfig())
                val prefs=context.getSharedPreferences("sunny-config",0)
                assertFalse(prefs.getString("updateSecret","")!!.contains("fixture-password"))
                assertNull(runBlocking {ExperimentalUpdater(context).download()})
            }
        } finally {store.saveUpdateConfig(previous.first,previous.second,previous.third)}
    }
    @Test fun apkIdentityAcceptsInstalledSignerAndRejectsWrongVersionAndPackage() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val updater=ExperimentalUpdater(context)
        updater.verifyIdentity(java.io.File(context.applicationInfo.sourceDir),BuildConfig.VERSION_CODE.toLong())
        assertTrue(runCatching {updater.verifyIdentity(java.io.File(context.applicationInfo.sourceDir),999)}.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching {updater.verifyIdentity(java.io.File(instrumentation.context.applicationInfo.sourceDir),BuildConfig.VERSION_CODE.toLong())}.exceptionOrNull() is IllegalArgumentException)
    }
    @Test fun wrongApplicationManifestIsRejected()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=ConfigStore(context);val previous=store.updateConfig()
        try {response("{\"applicationId\":\"other.app\",\"versionCode\":999}") {address->
            store.saveUpdateConfig(address,"fixture-user","fixture-password")
            assertTrue(runCatching {runBlocking {ExperimentalUpdater(context).download()}}.exceptionOrNull() is IllegalArgumentException)
        }} finally {store.saveUpdateConfig(previous.first,previous.second,previous.third)}
    }
}
