package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.EmbyProfile
import io.github.xudong7587.sunnytv.core.network.SafeHttp
import io.github.xudong7587.sunnytv.source.emby.EmbySource
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory
import java.io.IOException

/** Exercise Android's actual TLS provider with a NAS-style self-signed, non-IP certificate. */
class UgreenLanConnectionTest {
    @Test fun lanLoginBrowsingImagesAndRangePlaybackWorkOnAndroid() = runBlocking {
        val certificate = HeldCertificate.Builder().commonName("UGREEN NAS").build()
        MockWebServer().use { server ->
            server.useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(),false)
            server.start()
            val root = server.url("/emby/").newBuilder().host("192.168.50.10").build().toString()
            val http = SafeHttp(object : SocketFactory() {
                override fun createSocket() = object : Socket() {
                    override fun connect(endpoint: SocketAddress, timeout: Int) =
                        super.connect(InetSocketAddress("127.0.0.1",(endpoint as InetSocketAddress).port),timeout)
                }
                override fun createSocket(h:String,p:Int): Socket = error("unused")
                override fun createSocket(h:String,p:Int,l:InetAddress,lp:Int): Socket = error("unused")
                override fun createSocket(h:InetAddress,p:Int): Socket = error("unused")
                override fun createSocket(h:InetAddress,p:Int,l:InetAddress,lp:Int): Socket = error("unused")
            })
            server.enqueue(MockResponse().setBody("""{"User":{"Id":"u"},"AccessToken":"fixture-token"}"""))
            val config = EmbySource.login(root,"Green","media-user","fixture-password",http,"test",EmbyProfile.UGREEN)
            assertTrue(config.lanTlsCertificate.isNotEmpty())
            assertEquals("/emby/Users/AuthenticateByName",server.takeRequest().path)
            val source = EmbySource(config,http,"test")
            server.enqueue(MockResponse().setBody("""{"Items":[]}"""))
            assertTrue(source.views().isEmpty())
            assertEquals("fixture-token",server.takeRequest().getHeader("X-Emby-Token"))
            for (playback in listOf(false,true)) {
                server.enqueue(MockResponse().setResponseCode(206).setBody("data"))
                http.scopedClient(source.scope,playback).newCall(Request.Builder()
                    .url(root+"videos/1/stream.mp4").header("Range","bytes=0-3").build()).execute().use {
                    assertEquals(206,it.code)
                }
                val request = server.takeRequest()
                assertEquals("fixture-token",request.getHeader("X-Emby-Token"))
                assertEquals("bytes=0-3",request.getHeader("Range"))
            }
            try {
                http.client.newCall(Request.Builder().url(root).build()).execute().close()
                fail("Unscoped client trusted a NAS certificate")
            } catch (_: IOException) {}
            assertEquals(4,server.requestCount)
        }
    }
}
