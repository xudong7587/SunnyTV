package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.network.*
import io.github.xudong7587.sunnytv.source.emby.EmbySource
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class UgreenLocalSortTest {
    private val unknown=MediaEntry("unknown","g","未知","Movie")
    private val low=unknown.copy(id="low",title="A",rating=6.0,year=2000,durationMs=1000,lastPlayedAtMs=1000,
        versions=listOf(MediaVersion("v","",0,0,"",100,100,"",emptyList())))
    private val high=low.copy(id="high",title="B",rating=9.0,year=2020,durationMs=2000,lastPlayedAtMs=2000,
        versions=listOf(MediaVersion("v","",0,0,"",200,200,"",emptyList())))
    private fun api(server:MockWebServer)=EmbySource(SourceConfig("g",SourceKind.EMBY,"Green",server.url("/emby/").toString(),
        "u",secret="fixture",embyProfile=EmbyProfile.UGREEN),SafeHttp(),"test")

    @Test fun metadataSortsAreStableAndKeepMissingDataLastBothWays() {
        for(key in listOf("CommunityRating","ProductionYear","Runtime","DatePlayed","Size","Bitrate")) {
            assertEquals(listOf("low","high","unknown"),MediaLogic.ugreenLocalSort(listOf(unknown,high,low),key,true).map {it.id})
            assertEquals(listOf("high","low","unknown"),MediaLogic.ugreenLocalSort(listOf(unknown,low,high),key,false).map {it.id})
            assertEquals(listOf("a","b"),MediaLogic.ugreenLocalSort(listOf(low.copy(id="a"),low.copy(id="b")),key,false).map {it.id})
        }
    }
    @Test fun namesReverseAndRandomDoesNotDropOrInventEntries() {
        assertEquals(listOf("high","low"),MediaLogic.ugreenLocalSort(listOf(low,high),"SortName",false).map {it.id})
        val entries=listOf(unknown,low,high)
        assertEquals(entries.toSet(),MediaLogic.ugreenLocalSort(entries,"Random",true).toSet())
        assertFalse(MediaLogic.ugreenSorts.any {it.first in setOf("DateCreated","PremiereDate","CriticRating","OfficialRating")})
    }
    @Test fun sortsTheCompleteDirectoryAcrossServerPagesWithoutUnsupportedParameters()=runBlocking {
        MockWebServer().use {server->
            server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"3","Name":"A","Type":"Movie","ProductionYear":2020},{"Id":"2","Name":"B","Type":"Movie","ProductionYear":1990}],"TotalRecordCount":3}"""))
            server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"1","Name":"C","Type":"Movie","ProductionYear":2010}],"TotalRecordCount":3}"""))
            val page=api(server).library("media-lib-1",sort="ProductionYear",ascending=true)
            assertEquals(listOf("2","1","3"),page.items.map {it.id});assertEquals(3,page.total)
            for(offset in listOf("0","2")) {
                val q=server.takeRequest().requestUrl!!
                assertEquals(offset,q.queryParameter("StartIndex"));assertEquals("48",q.queryParameter("Limit"))
                assertEquals("media-lib-1",q.queryParameter("ParentId"));assertEquals("SortName",q.queryParameter("SortBy"))
                assertNull(q.queryParameter("SortOrder"));assertNull(q.queryParameter("SearchTerm"))
            }
        }
    }
    @Test fun refusesUnreliableIncompleteAndOversizedSnapshots()=runBlocking {
        val first="""{"Items":[{"Id":"1","Type":"Movie"}],"TotalRecordCount":2}"""
        val cases=listOf(
            listOf("""{"Items":[]}"""),
            listOf("""{"Items":[],"TotalRecordCount":10001}"""),
            listOf("""{"Items":[],"TotalRecordCount":2}"""),
            listOf(first,"""{"Items":[{"Id":"1"}],"TotalRecordCount":2}"""),
            listOf(first,"""{"Items":[{"Id":"2"}],"TotalRecordCount":3}"""))
        for(bodies in cases) MockWebServer().use {server->
            bodies.forEach {server.enqueue(MockResponse().setBody(it))}
            try {api(server).library("media-lib-1",sort="Random");fail("partial/fake directory accepted")}
            catch(_:SourceException) {}
            assertEquals(bodies.size,server.requestCount)
        }
    }
    @Test fun absentMetadataReportsWhyTheSelectedSortCannotBeApplied()=runBlocking {
        MockWebServer().use {server->
            server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"1","Name":"Film","Type":"Movie"}],"TotalRecordCount":1}"""))
            try {api(server).library("media-lib-1",sort="Size");fail("fake size sort")}
            catch(e:SourceException) {assertTrue(e.message.orEmpty().contains("大小数据"))}
        }
    }
}
