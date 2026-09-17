package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.network.SafeHttp
import io.github.xudong7587.sunnytv.source.emby.EmbySource
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LibraryApiTest {
    private fun source(server:MockWebServer)=EmbySource(SourceConfig("s",SourceKind.EMBY,"Test",server.url("/").toString(),"u","","token"),SafeHttp(),"test")
    @Test fun latestIsScopedAndBounded()=runBlocking {
        MockWebServer().use {server->
            server.enqueue(MockResponse().setBody("[]"));source(server).latest("library-A",10)
            val url=server.takeRequest().requestUrl!!
            assertEquals("library-A",url.queryParameter("ParentId"));assertEquals("10",url.queryParameter("Limit"))
        }
    }
    @Test fun sortingAndFoldersAreServerSideAndPaged()=runBlocking {
        MockWebServer().use {server->
            val api=source(server)
            server.enqueue(MockResponse().setBody("{\"Items\":[],\"TotalRecordCount\":200}"))
            api.library("lib",48,"PremiereDate",ascending=true,mixed=true)
            val url=server.takeRequest().requestUrl!!
            assertEquals("48",url.queryParameter("StartIndex"));assertEquals("PremiereDate",url.queryParameter("SortBy"))
            assertEquals("Ascending",url.queryParameter("SortOrder"));assertEquals("Movie,Episode,Video",url.queryParameter("IncludeItemTypes"))
            server.enqueue(MockResponse().setBody("{\"Items\":[]}"));api.library("lib",foldersOnly=true)
            val folderUrl=server.takeRequest().requestUrl!!
            assertEquals("false",folderUrl.queryParameter("Recursive"));assertEquals("true",folderUrl.queryParameter("IsFolder"))
            assertNull(folderUrl.queryParameter("IncludeItemTypes"))
        }
    }
    @Test fun detailsParseRealVersionsPeopleAndSeriesLogo() {
        MockWebServer().use {server->
            val entry=source(server).parseItem(JSONObject("""{"Id":"m","Type":"Movie","Name":"Film",
                "ParentLogoItemId":"series","ParentLogoImageTag":"logo","ImageTags":{"Banner":"banner"},
                "People":[{"Id":"actor","Name":"Actor","Role":"Lead","PrimaryImageTag":"p"}],
                "MediaSources":[{"Id":"v","Name":"Original","Size":100,"Bitrate":1000,"Container":"mkv",
                  "MediaStreams":[{"Index":0,"Type":"Video","Width":3840,"Height":2160,"VideoRange":"HDR"},
                    {"Index":2,"Type":"Subtitle","Language":"chi","DisplayTitle":"简体中文","Codec":"srt"}]}]}"""))
            assertEquals("series",entry.logo!!.itemId);assertEquals("banner",entry.banner!!.tag)
            assertEquals("Lead",entry.people.single().role);assertEquals(3840,entry.versions.single().width)
            assertEquals("简体中文",entry.versions.single().tracks.last().title)
        }
    }
    @Test fun playedWritesAreExplicitAndReversible()=runBlocking {
        MockWebServer().use {server->
            val api=source(server);val item=MediaEntry("film","s","Film","Movie")
            server.enqueue(MockResponse().setBody("{}"));api.setPlayed(item,true)
            val set=server.takeRequest();assertEquals("POST",set.method);assertEquals("/Users/u/PlayedItems/film",set.path)
            server.enqueue(MockResponse().setBody("{}"));api.setPlayed(item,false)
            assertEquals("DELETE",server.takeRequest().method)
        }
    }
}
