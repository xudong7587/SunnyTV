package io.github.xudong7587.sunnytv

import androidx.test.platform.app.InstrumentationRegistry
import io.github.xudong7587.sunnytv.core.storage.ConfigStore
import io.github.xudong7587.sunnytv.core.network.UpdateTransport
import io.github.xudong7587.sunnytv.feature.update.ExperimentalUpdater
import io.github.xudong7587.sunnytv.feature.update.GithubUpdates
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference

class ExperimentalUpdateTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun release()=GithubUpdates.Release(BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE.toLong(),GithubUpdates.LATEST_PAGE,
        "https://github.com/xudong7587/SunnyTV/releases/download/v${BuildConfig.VERSION_NAME}/SunnyTV-v${BuildConfig.VERSION_NAME}.apk")
    private fun response(body:ByteArray,action:(UpdateTransport)->Unit) {
        val failure=AtomicReference<Throwable?>()
        ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use {server->
            server.soTimeout=10000
            val worker=Thread {
                try {server.accept().use {socket->
                    socket.soTimeout=10000
                    val reader=socket.getInputStream().bufferedReader()
                    assertEquals("GET /dav/SunnyTV-v${BuildConfig.VERSION_NAME}.apk HTTP/1.1",reader.readLine())
                    while(!reader.readLine().isNullOrEmpty()) {}
                    socket.getOutputStream().apply {
                        write(("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray())
                        write(body);flush()
                    }
                }} catch(e:Throwable) {failure.set(e)}
            }.apply {start()}
            try {action(UpdateTransport("http://127.0.0.1:${server.localPort}/dav/","fixture-user","fixture-password"))} finally {worker.join(12000)}
            failure.get()?.let {throw it}
        }
    }
    @Test fun encryptedDavConfigPersistsAndCurrentVersionDoesNotDownloadApk()=runBlocking {
        val store=ConfigStore(context);val previous=store.updateConfig()
        try {
            store.saveUpdateConfig("https://fixture.invalid/dav/","fixture-user","fixture-password")
            assertEquals(Triple("https://fixture.invalid/dav/","fixture-user","fixture-password"),ConfigStore(context).updateConfig())
            assertFalse(context.getSharedPreferences("sunny-config",0).getString("updateSecret","")!!.contains("fixture-password"))
            assertNull(ExperimentalUpdater(context).download {release()})
        } finally {store.saveUpdateConfig(previous.first,previous.second,previous.third)}
    }
    @Test fun apkIdentityAcceptsInstalledSignerAndRejectsWrongVersionAndPackage() {
        val updater=ExperimentalUpdater(context);val apk=File(context.applicationInfo.sourceDir)
        updater.verifyIdentity(apk,BuildConfig.VERSION_CODE.toLong(),BuildConfig.VERSION_NAME)
        assertTrue(runCatching {updater.verifyIdentity(apk,999)}.isFailure)
        assertTrue(runCatching {updater.verifyIdentity(apk,BuildConfig.VERSION_CODE.toLong(),"999.0.0")}.isFailure)
        assertTrue(runCatching {updater.verifyIdentity(File(InstrumentationRegistry.getInstrumentation().context.applicationInfo.sourceDir),BuildConfig.VERSION_CODE.toLong())}.isFailure)
    }
    @Test fun davDownloadsOnlyOfficialFilenameAndAcceptsSignedApkWithoutJson() {
        response(File(context.applicationInfo.sourceDir).readBytes()) {transport->
            val file=runBlocking {ExperimentalUpdater(context).stageDav(release(),transport)}
            try {assertEquals(File(context.applicationInfo.sourceDir).length(),file.length())} finally {file.delete()}
        }
    }
    @Test fun corruptApkIsRejectedAndPartialIsRemoved() {
        val folder=File(context.cacheDir,"updates");val before=folder.listFiles().orEmpty().map {it.name}.toSet()
        response("not an apk".toByteArray()) {transport->
            assertTrue(runCatching {runBlocking {ExperimentalUpdater(context).stageDav(release(),transport)}}.isFailure)
        }
        assertEquals(before,folder.listFiles().orEmpty().map {it.name}.toSet())
    }
    @Test fun disabledDavCannotStartEvenWithSavedCredentials()=runBlocking {
        val store=ConfigStore(context);val previous=store.webDavUpdatesEnabled()
        try {store.setWebDavUpdatesEnabled(false);assertTrue(runCatching {ExperimentalUpdater(context).download()}.isFailure)}
        finally {store.setWebDavUpdatesEnabled(previous)}
    }
}
