package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.network.HttpPolicy
import io.github.xudong7587.sunnytv.core.network.SafeHttp
import io.github.xudong7587.sunnytv.source.transcode.TranscodeSource
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class CloudVideoSourceTest {
    private val variantId="a".repeat(32)
    private val sessionId="b".repeat(32)
    private val token="t".repeat(43)
    private val variant get() = """{"id":"$variantId","width":3840,"height":2160,"bandwidth":15000000}"""
    private fun session(url:String="/api/cloud-video/sessions/$sessionId/index.m3u8?st=$token") =
        """{"protocol":"mediaindex-cloud-video-v1","sessionId":"$sessionId","sessionToken":"$token","playlistUrl":"$url","variant":$variant,"seekMode":"hls-vod","delivery":"direct"}"""

    @Test fun explicitCloudDiscoverySelectionAndCleanupUseSameUaWithoutProviderCredentials() = runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setBody("""{"protocol":"mediaindex-cloud-video-v1","variants":[$variant]}"""))
            server.enqueue(MockResponse().setBody(session()))
            server.enqueue(MockResponse().setResponseCode(204))
            val source=TranscodeSource.from(SafeHttp(),server.url("/api/play/asset-token").toString())!!.cloudVideo()
            val variants=source.discover()
            assertEquals("4K",variants.single().badge)
            assertEquals("115:$variantId",variants.single().qualityId)
            val selected=source.create(variants.single().id)
            assertEquals(server.url("/api/cloud-video/sessions/$sessionId/index.m3u8?st=$token").toString(),selected.url)
            source.stop(selected)
            val calls=(1..3).map {server.takeRequest()}
            assertEquals(listOf("POST","POST","DELETE"),calls.map {it.method})
            assertEquals("asset-token",JSONObject(calls[0].body.readUtf8()).getString("assetToken"))
            val body=JSONObject(calls[1].body.readUtf8())
            assertEquals(variantId,body.getString("variantId"))
            assertEquals("direct",body.getString("delivery"))
            assertEquals("direct",selected.delivery)
            assertFalse(body.has("profile"));assertFalse(body.has("startPositionMs"))
            calls.forEach {req ->
                assertEquals(HttpPolicy.USER_AGENT,req.getHeader("User-Agent"))
                assertNull(req.getHeader("Cookie"));assertNull(req.getHeader("Authorization"));assertNull(req.getHeader("X-Emby-Token"))
            }
        }
    }

    @Test fun cloudSessionRejectsOtherOriginsPathsAndAmbiguousCredentials() = runBlocking {
        MockWebServer().use {server ->
            val source=TranscodeSource.from(SafeHttp(),server.url("/api/play/token").toString())!!.cloudVideo()
            val urls=listOf("https://evil.invalid/index.m3u8", "/api/transcode/sessions/$sessionId/index.m3u8?st=$token",
                "/api/cloud-video/sessions/$sessionId/index.m3u8?st=$token&st=$token",
                "/api/cloud-video/sessions/$sessionId/index.m3u8?st=$token&extra=value",
                "/api/cloud-video/sessions/$sessionId/index.m3u8?st=$token#fragment")
            for(url in urls) {
                server.enqueue(MockResponse().setBody(session(url)))
                assertTrue(url,runCatching {source.create(variantId)}.isFailure)
            }
            server.enqueue(MockResponse().setBody(session().replace("hls-vod","live")))
            assertTrue(runCatching {source.create(variantId)}.isFailure)
            server.enqueue(MockResponse().setBody(session().replace("\"id\":\"$variantId\"","\"id\":\"${"c".repeat(32)}\"")))
            assertTrue(runCatching {source.create(variantId)}.isFailure)
        }
    }

    @Test fun unavailableGatewayAndUntrustedDetailsAreReportedSafely() = runBlocking {
        MockWebServer().use {server ->
            val source=TranscodeSource.from(SafeHttp(),server.url("/api/play/token").toString())!!.cloudVideo()
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":"https://secret.invalid?token=private"}"""))
            assertEquals("此 MediaIndex 尚未提供 115 云端清晰度",runCatching {source.discover()}.exceptionOrNull()?.message)
            server.enqueue(MockResponse().setResponseCode(409).setBody("private Cookie value"))
            assertFalse(runCatching {source.discover()}.exceptionOrNull()?.message.orEmpty().contains("private"))
            server.enqueue(MockResponse().setBody("x".repeat(65537)))
            assertEquals("云端服务响应过大",runCatching {source.discover()}.exceptionOrNull()?.message)
        }
    }

    @Test fun proxyRequiresExplicitSelectionAndServerCannotSilentlyDowngradeDirect() = runBlocking {
        MockWebServer().use {server ->
            val source=TranscodeSource.from(SafeHttp(),server.url("/api/play/token").toString())!!.cloudVideo()
            val proxied=session().replace("\"delivery\":\"direct\"","\"delivery\":\"proxy\"")
            server.enqueue(MockResponse().setBody(proxied))
            assertTrue(runCatching {source.create(variantId)}.isFailure)
            server.enqueue(MockResponse().setBody(proxied))
            assertEquals("proxy",source.create(variantId,"proxy").delivery)
            assertEquals("direct",JSONObject(server.takeRequest().body.readUtf8()).getString("delivery"))
            assertEquals("proxy",JSONObject(server.takeRequest().body.readUtf8()).getString("delivery"))
        }
    }

    @Test fun unknownProtocolDuplicateVariantsAndInvalidDimensionsFailClosed() = runBlocking {
        MockWebServer().use {server ->
            val source=TranscodeSource.from(SafeHttp(),server.url("/api/play/token").toString())!!.cloudVideo()
            for(body in listOf(
                """{"protocol":"another","variants":[$variant]}""",
                """{"protocol":"mediaindex-cloud-video-v1","variants":[$variant,$variant]}""",
                """{"protocol":"mediaindex-cloud-video-v1","variants":[${variant.replace("3840","0")}]}""")) {
                server.enqueue(MockResponse().setBody(body))
                assertTrue(runCatching {source.discover()}.isFailure)
            }
        }
    }

    @Test fun cancelledDiscoveryDoesNotWaitForTransportTimeout() = runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val source=TranscodeSource.from(SafeHttp(),server.url("/api/play/token").toString())!!.cloudVideo()
            val job=launch(Dispatchers.IO) {source.discover()}
            assertNotNull(server.takeRequest(3,TimeUnit.SECONDS))
            withTimeout(1000) {job.cancelAndJoin()}
            assertTrue(job.isCancelled)
        }
    }
}
