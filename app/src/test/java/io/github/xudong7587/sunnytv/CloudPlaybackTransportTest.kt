package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.HeaderScope
import io.github.xudong7587.sunnytv.core.network.CloudPlaybackTransport
import io.github.xudong7587.sunnytv.core.network.SafeHttp
import okhttp3.Request
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import io.github.xudong7587.sunnytv.core.network.HttpPolicy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class CloudPlaybackTransportTest {
    @Test fun cloudSegmentsNeverReceiveEmbyCredentialsEvenOnTheSameOrigin() {
        MockWebServer().use {server ->
            val subtitle=server.url("/emby/subtitles/one.srt").toString()
            val transport=CloudPlaybackTransport.client(SafeHttp(),server.url("/api/cloud-video/sessions/id/index.m3u8").toString(),HeaderScope(server.url("/").toString(),mapOf("X-Emby-Token" to "secret")),listOf(subtitle))
            for(url in listOf(server.url("/api/cloud-video/sessions/id/index.m3u8").toString(),subtitle)) {
                server.enqueue(MockResponse().setBody("content"))
                transport.newCall(Request.Builder().url(url).build()).execute().close()
            }
            assertNull(server.takeRequest().getHeader("X-Emby-Token"))
            assertEquals("secret",server.takeRequest().getHeader("X-Emby-Token"))
        }
    }
    @Test fun subtitleRedirectCannotCarryAuthToGatewayOrOtherHosts() {
        MockWebServer().use {server ->
            MockWebServer().use {other ->
                val subtitle=server.url("/emby/subtitles/one.srt").toString()
                val transport=CloudPlaybackTransport.client(SafeHttp(),server.url("/api/cloud-video/sessions/id/index.m3u8").toString(),HeaderScope(server.url("/").toString(),mapOf("Authorization" to "secret")),listOf(subtitle))
                server.enqueue(MockResponse().setResponseCode(302).addHeader("Location","/api/cloud-video/discover"))
                server.enqueue(MockResponse().setResponseCode(302).addHeader("Location",other.url("/subtitle")))
                other.enqueue(MockResponse().setBody("text"))
                transport.newCall(Request.Builder().url(subtitle).build()).execute().close()
                assertEquals("secret",server.takeRequest().getHeader("Authorization"))
                assertNull(server.takeRequest().getHeader("Authorization"))
                assertNull(other.takeRequest().getHeader("Authorization"))
            }
        }
    }

    @Test fun directMediaAllowsOnlyTls115CdnAndItsOwnGatewaySession() {
        val playlist="http://nas.test:8000/api/cloud-video/sessions/id/index.m3u8?st=token".toHttpUrl()
        for(url in listOf("https://video.115.com/segment.ts?sign=a%2Fb", "https://a.115cdn.net/init.mp4", "https://115cdn.com/a", "https://v.anxia.com/a",
            "http://nas.test:8000/api/cloud-video/sessions/id/resources/key?st=token")) {
            assertTrue(url,CloudPlaybackTransport.permittedMedia(url.toHttpUrl(),playlist))
        }
        for(url in listOf("http://video.115.com/a", "https://video.115.com:8443/a", "https://115.com.evil.test/a", "https://127.0.0.1/a",
            "https://user@115.com/a", "https://115.com/a#fragment", "http://nas.test:8001/api/cloud-video/sessions/id/resources/key",
            "http://nas.test:8000/api/cloud-video/sessions/other/a", "http://nas.test:8000/api/cloud-video/sessions/id/%252e%252e/private")) {
            assertFalse(url,CloudPlaybackTransport.permittedMedia(url.toHttpUrl(),playlist))
        }
    }

    @Test fun cloudRedirectToUntrustedOriginIsRejectedBeforeNetworkAndNeverAutoProxied() {
        MockWebServer().use {gateway ->
            MockWebServer().use {untrusted ->
                val url=gateway.url("/api/cloud-video/sessions/id/index.m3u8").toString()
                val transport=CloudPlaybackTransport.client(SafeHttp(),url,null,emptyList())
                gateway.enqueue(MockResponse().setResponseCode(302).addHeader("Location","resources/one"))
                gateway.enqueue(MockResponse().setResponseCode(302).addHeader("Location",untrusted.url("/segment.ts")))
                assertTrue(runCatching {transport.newCall(Request.Builder().url(url).build()).execute().close()}.isFailure)
                assertEquals(2,gateway.requestCount)
                assertEquals(0,untrusted.requestCount)
            }
        }
    }

    @Test fun directCdnRequestRetainsSignedEncodingRangeAndUaWithoutCredentials() {
        val url="https://video.115.com/seg%2Fpart.ts?sign=a%2Fb%2Bc%3D"
        var sent:Request?=null
        val transport=CloudPlaybackTransport.client(SafeHttp(),"http://nas.test/api/cloud-video/sessions/id/index.m3u8",
            HeaderScope("https://video.115.com/",mapOf("Authorization" to "private")),emptyList()).newBuilder()
            .addInterceptor {chain ->
                sent=chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(206).message("Partial")
                    .body("synthetic segment".toResponseBody()).build()
            }.build()
        transport.newCall(Request.Builder().url(url).header("Range","bytes=10-20")
            .header("Cookie","private").header("Authorization","private").build()).execute().close()
        assertEquals(url,sent!!.url.toString())
        assertEquals("bytes=10-20",sent!!.header("Range"))
        assertEquals(HttpPolicy.USER_AGENT,sent!!.header("User-Agent"))
        assertNull(sent!!.header("Cookie"));assertNull(sent!!.header("Authorization"))
    }
}
