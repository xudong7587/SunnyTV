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
    @Test fun seasonResumeUsesServerHistoryAndKeepsTheSeasonScope()=runBlocking {
        MockWebServer().use {server->
            val body="""{"Items":[
                {"Id":"later","Type":"Episode","ParentIndexNumber":2,"IndexNumber":10,"UserData":{"PlaybackPositionTicks":50000000,"LastPlayedDate":"2026-09-17T12:00:00.1234567Z"}},
                {"Id":"earlier","Type":"Episode","ParentIndexNumber":2,"IndexNumber":2,"UserData":{"PlaybackPositionTicks":60000000,"LastPlayedDate":"2026-09-16T12:00:00Z"}}]}"""
            val season=MediaEntry("season-two","s","Season 2","Season",seriesId="show")
            server.enqueue(MockResponse().setBody(body))
            val resumed=source(server).playableEpisode(season,false)
            assertEquals("later",resumed.id);assertEquals(5000L,resumed.positionMs)
            val request=server.takeRequest()
            assertEquals("GET",request.method);assertEquals("season-two",request.requestUrl!!.queryParameter("SeasonId"))
            server.enqueue(MockResponse().setBody(body))
            assertEquals("earlier",source(server).playableEpisode(season,true).id)
            server.takeRequest()
            assertEquals(2,server.requestCount)
        }
    }
    private fun source(server:MockWebServer)=EmbySource(SourceConfig("s",SourceKind.EMBY,"Test",server.url("/").toString(),"u","","token"),SafeHttp(),"test")

    /** dev27: a direct-play STRM keeps Emby's own entry as the fallback route, and the switch forces it. */
    @Test fun directPlayKeepsAServerRouteAndThePreferenceForcesIt()=runBlocking {
        MockWebServer().use {server->
            val api=source(server)
            val item=MediaEntry("film","s","Film","Movie")
            val body="""{"MediaSources":[{"Id":"v","Container":"mkv","SupportsDirectPlay":true,
                "Path":"http://192.168.31.12:3005/d/115/movie.mkv"}]}"""
            server.enqueue(MockResponse().setBody(body))
            val direct=api.playback(item)
            assertEquals("/Items/film/PlaybackInfo",server.takeRequest().path)
            assertEquals("http://192.168.31.12:3005/d/115/movie.mkv",direct.stableUrl)
            assertEquals("DirectPlay",direct.playMethod)
            assertTrue(direct.fallbackUrl!!.startsWith(server.url("/").toString()))
            assertTrue(direct.fallbackUrl!!.contains("Videos/film/stream"))
            server.enqueue(MockResponse().setBody(body))
            val serverRoute=api.playback(item,preferServerStream=true)
            server.takeRequest()
            assertTrue(serverRoute.stableUrl.startsWith(server.url("/").toString()))
            assertTrue(serverRoute.stableUrl.contains("Videos/film/stream"))
            assertEquals("DirectStream",serverRoute.playMethod)
            assertNull(serverRoute.fallbackUrl)
        }
    }
    @Test fun latestIsScopedAndBounded()=runBlocking {
        MockWebServer().use {server->
            server.enqueue(MockResponse().setBody("[]"));source(server).latest("library-A",10)
            val url=server.takeRequest().requestUrl!!
            assertEquals("library-A",url.queryParameter("ParentId"));assertEquals("10",url.queryParameter("Limit"))
        }
    }
    @Test fun homeShelfReleaseAndRandomSortUseWholeLibraryScope()=runBlocking {
        MockWebServer().use {server->
            listOf("PremiereDate","Random").forEach {sort->
                server.enqueue(MockResponse().setBody("{\"Items\":[],\"TotalRecordCount\":200}"))
                source(server).library("library-A",sort=sort,ascending=false,limit=10)
                val url=server.takeRequest().requestUrl!!
                assertEquals("library-A",url.queryParameter("ParentId"))
                assertEquals(sort,url.queryParameter("SortBy"))
                assertEquals("Descending",url.queryParameter("SortOrder"))
                assertEquals("true",url.queryParameter("Recursive"))
                assertEquals("0",url.queryParameter("StartIndex"))
                assertEquals("10",url.queryParameter("Limit"))
            }
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
    @Test fun browsingAndRandomRecommendationsNeverWritePlaybackEvents()=runBlocking {
        MockWebServer().use {server->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest)=MockResponse().setBody(
                    if(request.path.orEmpty().contains("/Latest")) "[]" else "{\"Items\":[]}")
            }
            val api=source(server)
            api.home();api.library("lib",sort="Random",limit=6);api.latest("lib",10);api.similar("film")
            assertEquals(7,server.requestCount)
            repeat(7) {
                val request=server.takeRequest()
                assertEquals("GET",request.method)
                assertFalse(request.path.orEmpty().contains("Sessions/"))
                assertFalse(request.path.orEmpty().contains("PlayedItems/"))
            }
        }
    }
    @Test fun wholeSeriesAndSeasonCannotTriggerBulkPlayedUpdates()=runBlocking {
        MockWebServer().use {server->
            val api=source(server)
            for(type in listOf("Series","Season","Folder")) {
                try {api.setPlayed(MediaEntry("series","s","Series",type),true);fail("bulk update accepted")}
                catch(_:IllegalArgumentException) {}
            }
            assertEquals(0,server.requestCount)
        }
    }
    @Test fun enteringSeriesSeasonAndEpisodeListsUsesReadRequestsOnly()=runBlocking {
        MockWebServer().use {server->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest)=MockResponse().setBody(when(request.requestUrl?.encodedPath) {
                    "/Users/u/Items/series"->"""{"Id":"series","Type":"Series","Name":"Documentary"}"""
                    "/Shows/series/Seasons"->"""{"Items":[{"Id":"season","Type":"Season","SeriesId":"series"}]}"""
                    else->"""{"Items":[{"Id":"episode","Type":"Episode","SeriesId":"series"}]}"""
                })
            }
            val api=source(server)
            val series=api.item("series")
            val season=api.children(series).single()
            assertEquals("Episode",api.children(season).single().type)
            api.similar(series.id)
            assertEquals(4,server.requestCount)
            repeat(4) {assertEquals("GET",server.takeRequest().method)}
        }
    }
    @Test fun favoriteAndPlayedUseReturnedServerState()=runBlocking {
        MockWebServer().use {server->
            val api=source(server);val item=MediaEntry("film","s","Film","Movie",positionMs=9000)
            server.enqueue(MockResponse().setBody("{\"IsFavorite\":true,\"Played\":false,\"PlaybackPositionTicks\":90000000}"))
            val favorite=api.setFavorite(item,true)
            assertTrue(favorite.favorite);assertEquals(9000L,favorite.positionMs)
            assertEquals("/Users/u/FavoriteItems/film",server.takeRequest().path)
            server.enqueue(MockResponse().setBody("{\"IsFavorite\":true,\"Played\":true,\"PlaybackPositionTicks\":0}"))
            val played=api.setPlayed(favorite,true)
            assertTrue(played.played);assertTrue(played.favorite);assertEquals(0L,played.positionMs)
            server.enqueue(MockResponse().setBody("{\"IsFavorite\":false,\"Played\":true}"))
            val removed=api.setFavorite(played,false)
            server.takeRequest();assertEquals("DELETE",server.takeRequest().method)
            assertFalse(removed.favorite);assertTrue(removed.played)
        }
    }
    @Test fun episodeArtUsesItsOwnPrimaryBeforeInheritedBackdrop() {
        MockWebServer().use {server->
            val episode=source(server).parseItem(JSONObject("""{"Id":"ep","Type":"Episode","SeriesId":"series",
                "ImageTags":{"Primary":"own"},"ParentBackdropItemId":"series","ParentBackdropImageTags":["shared"]}"""))
            assertEquals("ep",MediaLogic.wideArtwork(episode)?.itemId)
            assertEquals("own",MediaLogic.wideArtwork(episode)?.tag)
        }
    }
    @Test fun seriesPlaySelectsUnfinishedEpisodeWithReadOnlyRequest()=runBlocking {
        MockWebServer().use {server->
            val body="""{"Items":[{"Id":"first","Type":"Episode","UserData":{"Played":true}},
                {"Id":"next","Type":"Episode","UserData":{"Played":false}},
                {"Id":"resume","Type":"Episode","UserData":{"Played":false,"PlaybackPositionTicks":10000000}}]}"""
            server.enqueue(MockResponse().setBody(body))
            val api=source(server)
            assertEquals("resume",api.playableEpisode(MediaEntry("series","s","Show","Series"),false).id)
            val request=server.takeRequest();assertEquals("GET",request.method)
            assertEquals("/Shows/series/Episodes",request.requestUrl!!.encodedPath)
            server.enqueue(MockResponse().setBody(body))
            assertEquals("first",api.playableEpisode(MediaEntry("season","s","Season","Season",seriesId="series"),true).id)
            assertEquals("season",server.takeRequest().requestUrl!!.queryParameter("SeasonId"))
            assertEquals(2,server.requestCount)
        }
    }

    @Test fun sidecarsAreScopedToMediaVersionAndKeepFileIdentity() {
        MockWebServer().use {server->
            val api=source(server)
            val streams=org.json.JSONArray("""[
                {"Index":2,"Type":"Subtitle","IsExternal":false,"Codec":"srt"},
                {"Index":4,"Type":"Subtitle","IsExternal":true,"Codec":"srt","Language":"zh","Path":"/media/fiction/film.zh.srt","IsDefault":true},
                {"Index":5,"Type":"Subtitle","IsExternal":true,"Codec":"ass","Language":"zh","DeliveryUrl":"/subs/actual.ass","Path":"/media/fiction/film.commentary.ass"},
                {"Index":6,"Type":"Subtitle","IsExternal":true,"Codec":"pgs"}
            ]""")
            val subtitles=api.externalSubtitles("film","version-a",streams)
            assertEquals(2,subtitles.size)
            assertEquals("film.zh.srt",subtitles[0].title)
            assertTrue(subtitles[0].url.endsWith("/Videos/film/version-a/Subtitles/4/Stream.srt"))
            assertEquals("emby-sub:4",subtitles[0].id);assertTrue(subtitles[0].isDefault)
            assertEquals("film.commentary.ass",subtitles[1].title)
            assertTrue(subtitles[1].url.endsWith("/subs/actual.ass"))
            assertEquals(0,server.requestCount)
        }
    }
    @Test fun personBiographyAndPagedCreditsUseReadOnlyEmbyQueries()=runBlocking {
        MockWebServer().use {server->
            server.enqueue(MockResponse().setBody("""{"Id":"actor-1","Type":"Person","Name":"A / B","Overview":"Fictional biography"}"""))
            server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"film","Type":"Movie"}],"TotalRecordCount":100}"""))
            val api=source(server);val person=api.person("A / B")
            assertEquals("Fictional biography",person.overview)
            val request=server.takeRequest();assertEquals("GET",request.method)
            assertEquals(listOf("Persons","A / B"),request.requestUrl!!.pathSegments)
            assertEquals("u",request.requestUrl!!.queryParameter("UserId"))
            val works=api.personWorks(person.id,48);assertEquals(100,works.total)
            val credits=server.takeRequest();assertEquals("GET",credits.method)
            assertEquals("actor-1",credits.requestUrl!!.queryParameter("PersonIds"))
            assertEquals("48",credits.requestUrl!!.queryParameter("StartIndex"))
            assertEquals("48",credits.requestUrl!!.queryParameter("Limit"))
        }
    }

}
