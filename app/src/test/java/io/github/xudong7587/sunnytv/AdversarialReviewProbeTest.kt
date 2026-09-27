package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.network.*
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.source.emby.EmbySource
import kotlinx.coroutines.*
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Regression coverage for the dev27 audit; synthetic accounts and loopback services only. */
class AdversarialReviewProbeTest {
    private fun source(server: MockWebServer) = EmbySource(
        SourceConfig("s", SourceKind.EMBY, "Audit", server.url("/").toString(), "u", "", "fixture-token"),
        SafeHttp(), "audit-device")

    @Test fun inactiveAccountSearchEntriesAreExcludedFromActiveAccountPool() {
        val app = SunnyApp()
        // Replace the platform RAM query only; exercise the real AppModel and Compose state maps.
        SunnyApp::class.java.getDeclaredField("lowRamDevice\$delegate").apply {
            isAccessible = true
            set(app, lazy { false })
        }
        val active = SourceConfig("B", SourceKind.EMBY, "B", "https://b.invalid/")
        val inactive = SourceConfig("A", SourceKind.EMBY, "A", "https://a.invalid/")
        val model = AppModel(app, false, listOf(active, inactive))
        model.pages["search:A"] = MediaPage(listOf(MediaEntry("private", "A", "Private A movie", "Movie")), 1)
        assertEquals("B", model.activeSourceId)
        assertTrue(model.knownEntries().isEmpty())
        model.pages["search:B"] = MediaPage(listOf(MediaEntry("public", "B", "B movie", "Movie")), 1)
        assertEquals(listOf("B"), model.knownEntries().map {it.sourceId})
        model.pages["search:B"] = MediaPage(listOf(MediaEntry("updated", "B", "Updated", "Movie")), 1)
        assertEquals(listOf("updated"), model.knownEntries().map {it.id})
    }

    @Test fun localSizeSortIncludesLargestItemBeyondFirstTwoHundred() = runBlocking {
        MockWebServer().use { server ->
            fun entry(id: String, size: Long) = JSONObject().put("Id", id).put("Type", "Movie")
                .put("MediaSources", JSONArray().put(JSONObject().put("Size", size)))
            val first = JSONArray()
            repeat(200) { first.put(entry("small-$it", 1)) }
            server.enqueue(MockResponse().setBody(JSONObject().put("Items", first).put("TotalRecordCount", 201).toString()))
            server.enqueue(MockResponse().setBody(JSONObject().put("Items", JSONArray().put(entry("largest", 999999)))
                .put("TotalRecordCount", 201).toString()))
            val api = source(server)
            val a = api.library("library", sort = "Size", ascending = false)
            assertEquals(201, a.items.size)
            assertEquals(201, a.total)
            assertEquals("largest", a.items.first().id)
            assertEquals("SortName", server.takeRequest().requestUrl!!.queryParameter("SortBy"))
            assertEquals("200", server.takeRequest().requestUrl!!.queryParameter("StartIndex"))
        }
    }

    @Test fun embyRemovalUsesRemoteDeleteRatherThanLocalHide() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(""))
            source(server).deleteItem("fixture-media")
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/Items/fixture-media", request.path)
        }
    }

    @Test fun apiDeadlineStopsSlowDripWhileVideoHasNoWholeStreamDeadline() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(100)).throttleBody(1, 50, TimeUnit.MILLISECONDS))
            val http = SafeHttp()
            assertEquals(30000, http.client.callTimeoutMillis)
            assertEquals(0, http.playbackClient.callTimeoutMillis)
            assertEquals(30000, http.scopedClient(null).callTimeoutMillis)
            assertEquals(0, http.scopedClient(null, playback=true).callTimeoutMillis)
            val client = http.client.newBuilder().callTimeout(300, TimeUnit.MILLISECONDS).build()
            val failure = runCatching { client.bytes(Request.Builder().url(server.url("/api")).build()) }.exceptionOrNull()
            assertTrue(failure is java.io.IOException)
        }
    }

    @Test fun localSortRefusesIncompleteOrOversizedSnapshots() = runBlocking {
        for(body in listOf("{\"Items\":[],\"TotalRecordCount\":201}",
            "{\"Items\":[],\"TotalRecordCount\":10001}")) {
            MockWebServer().use {server ->
                server.enqueue(MockResponse().setBody(body))
                assertTrue(runCatching {source(server).library("lib",sort="Size")}.exceptionOrNull() is SourceException)
                assertEquals(1,server.requestCount)
            }
        }
    }
    @Test fun globalBitrateSortAscendingAndDescendingSpanPages() = runBlocking {
        for(ascending in listOf(true,false)) MockWebServer().use {server ->
            fun row(id:String,rate:Long) = JSONObject().put("Id",id).put("Type","Movie")
                .put("MediaSources",JSONArray().put(JSONObject().put("Bitrate",rate)))
            val first=JSONArray();repeat(200) {first.put(row("item-$it",100))}
            server.enqueue(MockResponse().setBody(JSONObject().put("Items",first).put("TotalRecordCount",201).toString()))
            server.enqueue(MockResponse().setBody(JSONObject().put("Items",JSONArray().put(row("largest",9999)))
                .put("TotalRecordCount",201).toString()))
            val page=source(server).library("lib",sort="Bitrate",ascending=ascending)
            assertEquals("largest",if(ascending) page.items.last().id else page.items.first().id)
            assertEquals(201,page.items.size)
        }
    }

    @Test fun globalSortRefusesChangedTotalsAndDuplicatePages() = runBlocking {
        for(second in listOf("{\"Items\":[{\"Id\":\"same\"}],\"TotalRecordCount\":2}",
            "{\"Items\":[{\"Id\":\"other\"}],\"TotalRecordCount\":3}")) MockWebServer().use {server ->
            server.enqueue(MockResponse().setBody("{\"Items\":[{\"Id\":\"same\"}],\"TotalRecordCount\":2}"))
            server.enqueue(MockResponse().setBody(second))
            assertTrue(runCatching {source(server).library("lib",sort="Size")}.exceptionOrNull() is SourceException)
            assertEquals(2,server.requestCount)
        }
    }

}
