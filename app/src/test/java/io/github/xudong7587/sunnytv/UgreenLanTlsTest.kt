package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.network.*
import io.github.xudong7587.sunnytv.source.emby.EmbySource
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.ByteString.Companion.toByteString
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory
import java.util.concurrent.TimeUnit

class UgreenLanTlsTest {
    // OkHttp bypasses Dns for literal IPs. Route test sockets to loopback before any connection.
    private val sockets = object : SocketFactory() {
        override fun createSocket() = object : Socket() {
            override fun connect(endpoint: SocketAddress, timeout: Int) =
                super.connect(InetSocketAddress("127.0.0.1",(endpoint as InetSocketAddress).port),timeout)
        }
        override fun createSocket(h:String,p:Int): Socket = error("unused")
        override fun createSocket(h:String,p:Int,l:InetAddress,lp:Int): Socket = error("unused")
        override fun createSocket(h:InetAddress,p:Int): Socket = error("unused")
        override fun createSocket(h:InetAddress,p:Int,l:InetAddress,lp:Int): Socket = error("unused")
    }
    private fun certificate() = HeldCertificate.Builder().commonName("UGREEN NAS")
        .addSubjectAlternativeName("nas.invalid").build()
    private fun server(cert: HeldCertificate) = MockWebServer().apply {
        useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory(), false)
        start()
    }
    private fun root(server: MockWebServer, host: String = "192.168.50.10") =
        server.url("/emby/").newBuilder().host(host).build().toString()
    private fun encoded(cert: HeldCertificate) = cert.certificate.encoded.toByteString().base64()
    private suspend fun login(server: MockWebServer, http: SafeHttp): SourceConfig {
        server.enqueue(MockResponse().setBody("""{"User":{"Id":"u"},"AccessToken":"fixture-token","ServerId":"s"}"""))
        return EmbySource.login(root(server),"Green","media-user","fixture-password",http,"test",EmbyProfile.UGREEN)
    }
    private fun rejected(action: () -> Unit) {
        try { action(); fail("TLS connection must fail") } catch (_: IOException) {}
    }

    @Test fun greenLanLoginAndApiImagePlaybackUseTheRememberedCertificate() = runBlocking {
        val cert = certificate()
        server(cert).use { server ->
            val http = SafeHttp(sockets)
            val config = login(server,http)
            assertEquals(encoded(cert),config.lanTlsCertificate)
            val auth = server.takeRequest(2,TimeUnit.SECONDS)!!
            assertEquals("/emby/Users/AuthenticateByName",auth.path)
            assertTrue(auth.body.readUtf8().contains("fixture-password"))
            val source = EmbySource(config,http,"test")
            server.enqueue(MockResponse().setBody("""{"Items":[]}"""))
            assertTrue(source.views().isEmpty())
            assertEquals("fixture-token",server.takeRequest().getHeader("X-Emby-Token"))
            for (playback in listOf(false,true)) {
                server.enqueue(MockResponse().setResponseCode(206).setBody("data"))
                http.scopedClient(source.scope,playback).newCall(Request.Builder()
                    .url(root(server)+"videos/1/stream.mp4").header("Range","bytes=0-3").build()).execute().use {
                    assertEquals(206,it.code)
                }
                val request = server.takeRequest()
                assertEquals("bytes=0-3",request.getHeader("Range"))
                assertEquals("fixture-token",request.getHeader("X-Emby-Token"))
            }
            assertEquals(4,server.requestCount) // Discovery sends no HTTP request or password.
        }
    }

    @Test fun ordinaryEmbyAndGlobalClientsDoNotAcceptTheNasCertificate() = runBlocking {
        server(certificate()).use { server ->
            val http = SafeHttp(sockets)
            val config = login(server,http)
            server.takeRequest()
            rejected { http.client.newCall(Request.Builder().url(root(server)).build()).execute().close() }
            val standard = EmbySource(config.copy(embyProfile=EmbyProfile.STANDARD),http,"test")
            rejected { http.scopedClient(standard.scope).newCall(Request.Builder().url(root(server)).build()).execute().close() }
            assertEquals(1,server.requestCount)
        }
    }

    @Test fun aChangedCertificateCannotReceiveSavedCredentials() = runBlocking {
        server(certificate()).use { server ->
            val http = SafeHttp(sockets)
            val config = login(server,http).copy(lanTlsCertificate=encoded(certificate()))
            val source = EmbySource(config,http,"test")
            rejected { http.scopedClient(source.scope).newCall(Request.Builder().url(root(server)).build()).execute().close() }
            assertEquals(1,server.requestCount)
        }
    }

    @Test fun certificateTrustDoesNotFollowRedirectsToAnotherHostOrPort() = runBlocking {
        val cert = certificate()
        server(cert).use { origin -> server(cert).use { other ->
            val http = SafeHttp(sockets)
            val config = login(origin,http)
            val scope = EmbySource(config,http,"test").scope
            for (target in listOf(root(other),root(other,"192.168.50.11"),root(other,"203.0.113.8"))) {
                origin.enqueue(MockResponse().setResponseCode(302).addHeader("Location",target))
                rejected { http.scopedClient(scope).newCall(Request.Builder().url(root(origin)).build()).execute().close() }
            }
            assertEquals(0,other.requestCount)
        }}
    }

    @Test fun onlyLiteralRfc1918HttpsAddressesCanUseLanTrust() {
        for (address in listOf("https://10.0.0.2:9443/","https://172.16.0.1/","https://172.31.255.254/","https://192.168.1.2/"))
            assertTrue(address,UgreenLanTls.isLanAddress(address))
        for (address in listOf("http://192.168.1.2/","https://172.15.0.1/","https://172.32.0.1/","https://127.0.0.1/","https://8.8.8.8/","https://nas.example/"))
            assertFalse(address,UgreenLanTls.isLanAddress(address))
    }

    @Test fun expiredNasCertificateIsStillRejected() = runBlocking {
        val now = System.currentTimeMillis()
        val expired = HeldCertificate.Builder().commonName("NAS").validityInterval(now-120_000,now-60_000).build()
        server(expired).use { server ->
            try { UgreenLanTls.inspect(root(server),SafeHttp(sockets).client);fail("Expired certificate accepted") }
            catch (_: IOException) {}
            assertEquals(0,server.requestCount)
        }
    }
}
