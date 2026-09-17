package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.network.*
import io.github.xudong7587.sunnytv.source.emby.EmbySource
import io.github.xudong7587.sunnytv.source.clouddrive.WebDavSource
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Requires Gradle/Maven dependencies. Not run by the standalone core test script. */
class TransportTest {
    @Test fun rangeAndUserAgentSurviveCdnRedirectButAuthDoesNot() {
        MockWebServer().use {origin->MockWebServer().use {cdn->
            origin.enqueue(MockResponse().setResponseCode(302).addHeader("Location",cdn.url("/video?sig=x%2By")))
            cdn.enqueue(MockResponse().setResponseCode(206).addHeader("Content-Range","bytes 100-103/1000").setBody("data"))
            val client=SafeHttp().scopedClient(HeaderScope(origin.url("/").toString(),mapOf("Authorization" to "secret","X-Emby-Token" to "token")))
            client.newCall(Request.Builder().url(origin.url("/api/play/test_token")).header("Range","bytes=100-103").build()).execute().use {assertEquals(206,it.code)}
            val first=origin.takeRequest();val last=cdn.takeRequest()
            assertEquals("secret",first.getHeader("Authorization"));assertEquals("token",first.getHeader("X-Emby-Token"))
            assertNull(last.getHeader("Authorization"));assertNull(last.getHeader("X-Emby-Token"))
            assertEquals("bytes=100-103",last.getHeader("Range"))
            assertEquals(HttpPolicy.USER_AGENT,last.getHeader("User-Agent"))
            assertEquals("/video?sig=x%2By",last.path)
            assertEquals(1,origin.requestCount);assertEquals(1,cdn.requestCount)
        }}
    }
    @Test fun relativeRedirectAndProxyBody() {
        MockWebServer().use {server->
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location","/proxy"))
            server.enqueue(MockResponse().setResponseCode(206).addHeader("X-MediaIndex-Playback-Mode","proxy").setBody("video"))
            val client=SafeHttp().scopedClient(HeaderScope(server.url("/").toString(),mapOf("X-Emby-Token" to "token")))
            client.newCall(Request.Builder().url(server.url("/api/play/token")).build()).execute().use {
                assertEquals("video",it.body!!.string());assertEquals("proxy",it.header("X-MediaIndex-Playback-Mode"))
            }
            assertEquals("GET",server.takeRequest().method)
            assertEquals("token",server.takeRequest().getHeader("X-Emby-Token"))
        }
    }
    @Test fun embyImageTagsAndProgressAreUsed() {
        val source=EmbySource(SourceConfig("serverA",SourceKind.EMBY,"Emby","https://example.test/emby/","user","u","token"),SafeHttp(),"device")
        val item=source.parseItem(JSONObject("""{"Id":"lib","Name":"电影","Type":"CollectionFolder","IsFolder":true,"ImageTags":{"Primary":"native-tag"},"BackdropImageTags":["back"],"RunTimeTicks":1000000000,"UserData":{"PlaybackPositionTicks":300000000}}"""))
        assertEquals("native-tag",MediaLogic.libraryArtwork(item)!!.tag)
        assertEquals(30000L,item.positionMs)
        val url=source.imageUrl(item.primary!!,640)
        assertTrue(url.startsWith("https://example.test/emby/Items/lib/Images/Primary?"))
        assertTrue(url.contains("tag=native-tag"));assertFalse(url.contains("token"))
    }
    @Test fun webDav207IsSuccessAndStrmIsReadOnly() = runBlocking {
        MockWebServer().use {server->
            val root=server.url("/dav/").toString()
            val xml="""<d:multistatus xmlns:d="DAV:"><d:response><d:href>/dav/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response><d:response><d:href>/dav/movie.strm</d:href><d:propstat><d:prop><d:displayname>电影.strm</d:displayname><d:resourcetype/></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>"""
            server.enqueue(MockResponse().setResponseCode(207).setBody(xml))
            val source=WebDavSource(SourceConfig("cd",SourceKind.CLOUDDRIVE,"CD2",root,username="u",secret="p"),SafeHttp())
            val entries=source.list()
            assertEquals(1,entries.size);assertEquals("电影.strm",entries[0].title)
            val req=server.takeRequest();assertEquals("PROPFIND",req.method);assertEquals("1",req.getHeader("Depth"))
            server.enqueue(MockResponse().setBody("https://cdn.test/api/play/test-token\n"))
            val playback=source.playback(entries[0])
            assertEquals("https://cdn.test/api/play/test-token",playback.stableUrl)
            assertNull(playback.mimeHint)
            assertEquals("GET",server.takeRequest().method)
        }
    }
}
