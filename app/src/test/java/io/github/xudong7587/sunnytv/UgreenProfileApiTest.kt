package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.network.SafeHttp
import io.github.xudong7587.sunnytv.source.emby.EmbySource
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UgreenProfileApiTest {
    private fun api(server:MockWebServer)=EmbySource(SourceConfig("green",SourceKind.EMBY,"Green",
        server.url("/emby/").toString(),"u","","token",EmbyProfile.UGREEN),SafeHttp(),"test")
    private val movie=MediaEntry("7100","green","Film","Movie")

    @Test fun connectionAddressesAreNormalizedBeforeSendingCredentials() {
        assertEquals("https://nas.example:9443/emby/",EmbySource.connectionBase("https://nas.example:9443",EmbyProfile.UGREEN))
        assertEquals("https://nas.example:666/emby/",EmbySource.connectionBase(" https://nas.example:666/emby ",EmbyProfile.UGREEN))
        for(value in listOf("http://nas.example:9999","https://nas.example/desktop/","https://nas.example/video/#/",
            "https://nas.example/emby/emby/","https://user:password@nas.example/","https://nas.example/?api_key=secret")) {
            try {EmbySource.connectionBase(value,EmbyProfile.UGREEN);fail("unsafe/incorrect address accepted")}
            catch(_:IllegalArgumentException) {}
        }
        assertEquals("http://nas.example/proxy/",EmbySource.connectionBase("http://nas.example/proxy",EmbyProfile.STANDARD))
    }

    @Test fun homeScopesLatestToEachLibraryAndDoesNotCallGlobalNextUp()=runBlocking {
        MockWebServer().use {server->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse {
                    val path=request.requestUrl!!.encodedPath
                    return MockResponse().setBody(when {
                        path.endsWith("/Views")->"""{"Items":[{"Id":"media-lib-1","Name":"Movies","Type":"CollectionFolder"},{"Id":"media-lib-2","Name":"Shows","Type":"CollectionFolder"}]}"""
                        path.endsWith("/Latest")->"""[{"Id":"${if(request.requestUrl!!.queryParameter("ParentId")=="media-lib-1") "1" else "2"}","Type":"Movie"}]"""
                        else->"""{"Items":[]}"""
                    })
                }
            }
            val feed=api(server).home()
            assertEquals(2,feed.libraries.size);assertEquals(listOf("1","2"),feed.latest.map {it.id})
            assertTrue(feed.nextUp.isEmpty());assertTrue(feed.warnings.isEmpty())
            assertEquals(4,server.requestCount)
            val requests=(1..4).map {server.takeRequest()}
            val latest=requests.filter {it.requestUrl!!.encodedPath.endsWith("/Latest")}
            assertEquals(setOf("media-lib-1","media-lib-2"),latest.map {it.requestUrl!!.queryParameter("ParentId")}.toSet())
            latest.forEach {assertEquals("Video",it.requestUrl!!.queryParameter("MediaTypes"))}
            requests.forEach {assertEquals("GET",it.method);assertFalse(it.path.orEmpty().contains("NextUp"))}
        }
    }

    @Test fun failingLibraryShelfDoesNotHideOtherLibrariesOrAuthenticationFailure()=runBlocking {
        MockWebServer().use {server->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(r:RecordedRequest)=when {
                    r.path.orEmpty().endsWith("/Views")->MockResponse().setBody("""{"Items":[{"Id":"media-lib-1","Name":"Films","Type":"CollectionFolder"}]}""")
                    r.path.orEmpty().contains("/Latest")->MockResponse().setResponseCode(500)
                    else->MockResponse().setBody("""{"Items":[]}""")
                }
            }
            val feed=api(server).home()
            assertEquals(1,feed.libraries.size);assertTrue(feed.latest.isEmpty());assertTrue(feed.warnings.isNotEmpty())
            server.dispatcher=object:Dispatcher() {override fun dispatch(r:RecordedRequest)=MockResponse().setResponseCode(401)}
            try {api(server).home();fail("authentication hidden as empty feed")} catch(_:java.io.IOException) {}
        }
    }

    @Test fun collectionBrowsingAndFavoriteQueriesUsePromisedTypes()=runBlocking {
        MockWebServer().use {server->
            val source=api(server)
            for(action in listOf<suspend ()->Unit>(
                {source.library("media-lib-1",start=48,sort="DateCreated")},
                {source.library("media-lib-1",foldersOnly=true)},
                {source.library("collection-tmdb-15")},
                {source.library("",favorites=true)})) {
                server.enqueue(MockResponse().setBody("""{"Items":[],"TotalRecordCount":0}"""));action()
            }
            val normal=server.takeRequest().requestUrl!!
            assertEquals("48",normal.queryParameter("StartIndex"));assertNull(normal.queryParameter("IncludeItemTypes"))
            assertEquals("SortName",normal.queryParameter("SortBy"));assertNull(normal.queryParameter("SortOrder"))
            assertEquals("Folder",server.takeRequest().requestUrl!!.queryParameter("IncludeItemTypes"))
            assertEquals("collection-tmdb-15",server.takeRequest().requestUrl!!.queryParameter("ParentId"))
            val favorites=server.takeRequest().requestUrl!!
            assertEquals("Movie,Series",favorites.queryParameter("IncludeItemTypes"));assertEquals("IsFavorite",favorites.queryParameter("Filters"))
            try {source.library("media-lib-1",query="film");fail("fake server search")} catch(_:IllegalArgumentException) {}
            try {source.latest();fail("unscoped latest")} catch(_:IllegalArgumentException) {}
            assertTrue(source.resume("media-lib-1").isEmpty());assertEquals(4,server.requestCount)
        }
    }

    @Test fun directCdnKeepsNasFallbackAndServerPreferenceUsesNasRoute()=runBlocking {
        MockWebServer().use {server->
            val source=api(server)
            val body="""{"PlaySessionId":"session","MediaSources":[{"Id":"source","Protocol":"Http","Path":"https://cdn.example/film.mkv?sig=a%2Fb","DirectStreamUrl":"https://cdn.example/film.mkv?sig=a%2Fb","SupportsDirectPlay":true,"SupportsDirectStream":true}]}"""
            server.enqueue(MockResponse().setBody(body))
            val direct=source.playback(movie)
            assertEquals("https://cdn.example/film.mkv?sig=a%2Fb",direct.stableUrl)
            assertEquals(server.url("/emby/videos/7100/stream.mp4").toString(),direct.fallbackUrl)
            assertNull(direct.mimeHint) // Missing Container: let Media3 sniff the actual stream.
            server.enqueue(MockResponse().setBody(body))
            val proxied=source.playback(movie,preferServerStream=true)
            assertEquals(direct.fallbackUrl,proxied.stableUrl);assertNull(proxied.fallbackUrl)
        }
    }

    @Test fun seasonsInheritParentArtworkAndEpisodesRequestStreams()=runBlocking {
        MockWebServer().use {server->
            val source=api(server)
            val show=MediaEntry("7500","green","Show","Series",primary=Artwork("7500","Primary","tag"))
            server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"7500-season-3","Type":"Season"}]}"""))
            val season=source.children(show).single();server.takeRequest()
            assertEquals(show.id,season.seriesId);assertEquals(show.primary,season.primary)
            server.enqueue(MockResponse().setBody("""{"Items":[]}"""));source.children(season)
            val url=server.takeRequest().requestUrl!!
            assertEquals("7500-season-3",url.queryParameter("SeasonId"));assertTrue(url.queryParameter("Fields")!!.contains("MediaStreams"))
        }
    }

    @Test fun nativeSegmentsUseEpisodeIdAndDiscardInvalidRanges()=runBlocking {
        MockWebServer().use {server->
            val source=api(server)
            server.enqueue(MockResponse().setBody("""{"Id":"7500-episode-8874","Type":"Episode","RunTimeTicks":600000000}"""))
            server.enqueue(MockResponse().setBody("""{"Items":[{"Type":"Intro","StartTicks":0,"EndTicks":90000000},{"Type":"Outro","StartTicks":500000000,"EndTicks":600000000},{"Type":"Intro","StartTicks":-1,"EndTicks":10000},{"Type":"Unknown","StartTicks":0,"EndTicks":10000},{"Type":"Outro","StartTicks":0,"EndTicks":700000000}]}"""))
            val context=source.playerContext("7500-episode-8874");server.takeRequest()
            assertEquals(listOf("intro","outro"),context.skipSegments.map {it.type})
            assertEquals(9000L,context.skipSegments.first().endMs)
            assertEquals("/emby/MediaSegments/7500-episode-8874",server.takeRequest().requestUrl!!.encodedPath)
            server.enqueue(MockResponse().setBody("""{"Id":"7100","Type":"Movie"}"""))
            server.enqueue(MockResponse().setResponseCode(404))
            assertTrue(source.playerContext("7100").skipSegments.isEmpty())
        }
    }

    @Test fun unsupportedWritesNeverLeaveTheClient()=runBlocking {
        MockWebServer().use {server->
            val source=api(server)
            for(action in listOf<suspend ()->Unit>(
                {source.setFavorite(movie.copy(id="7500-episode-1",type="Episode"),true)},
                {source.setPlayed(movie.copy(id="7500-episode-1",type="Episode"),true)},
                {source.setPlayed(movie.copy(type="Series"),true)},
                {source.refreshMetadata(movie.id)}, {source.deleteItem(movie.id)})) {
                try {action();fail("unpromised write accepted")} catch(_:IllegalArgumentException) {}
            }
            assertFalse(source.canDeleteContent());assertEquals(0,server.requestCount)
            val subtitle=RemoteSubtitle("subtitle","name","provider","chi","srt",true)
            for(action in listOf<suspend ()->Unit>(
                {source.searchSubtitles(movie.id,"source","chi")},
                {source.downloadSubtitle(movie.id,"source",subtitle)},
                {source.downloadAndSelectSubtitle(movie.id,"source",subtitle)})) {
                try {action();fail("unpromised subtitle operation accepted")} catch(_:IllegalArgumentException) {}
            }
            assertNull(source.ensureSeriesSubtitle(movie,SeriesSubtitleChoice("provider","chi","srt")))
            assertEquals(0,server.requestCount)
        }
    }

    @Test fun supportedFavoriteWritesKeepServerUserData()=runBlocking {
        MockWebServer().use {server->
            server.enqueue(MockResponse().setBody("""{"IsFavorite":true,"Played":false}"""))
            assertTrue(api(server).setFavorite(movie,true).favorite)
            assertEquals("/emby/Users/u/FavoriteItems/7100",server.takeRequest().path)
        }
    }

    @Test fun subtitlesNeedAnExplicitDeliveryUrlAndPeopleUseTheirReturnedId()=runBlocking {
        MockWebServer().use {server->
            val source=api(server)
            val subs=source.externalSubtitles(movie.id,"source",JSONArray("""[{"Index":1,"Type":"Subtitle","IsExternal":true,"Codec":"srt"},{"Index":2,"Type":"Subtitle","IsExternal":true,"Codec":"srt","DeliveryUrl":"/emby/subs/2.srt"}]"""))
            assertEquals(1,subs.size);assertEquals(server.url("/emby/subs/2.srt").toString(),subs.single().url)
            server.enqueue(MockResponse().setBody("""{"Id":"person-1","Type":"Person","Name":"Actor"}"""))
            assertEquals("person-1",source.person("Actor","person-1").id)
            assertEquals("/emby/Users/u/Items/person-1",server.takeRequest().requestUrl!!.encodedPath)
        }
    }
}
