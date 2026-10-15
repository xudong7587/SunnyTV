package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.network.SafeHttp
import io.github.xudong7587.sunnytv.source.emby.EmbySource
import io.github.xudong7587.sunnytv.source.transcode.TranscodeSource
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UgreenCloudQualityTest {
    @Test fun retainedMediaIndexEntryDiscoversCloudQualityWithoutSendingNasCredentials()=runBlocking {
        MockWebServer().use {nas->MockWebServer().use {mediaIndex->
            val entry=mediaIndex.url("/api/play/fixture-asset").toString()
            nas.enqueue(MockResponse().setBody("""{"PlaySessionId":"s","MediaSources":[{"Id":"v","Path":"$entry","Protocol":"Http","SupportsDirectPlay":true,"SupportsDirectStream":true}]}"""))
            val http=SafeHttp()
            val api=EmbySource(SourceConfig("g",SourceKind.EMBY,"Green",nas.url("/emby/").toString(),"u",secret="nas-secret",embyProfile=EmbyProfile.UGREEN),http,"test")
            val request=api.playback(MediaEntry("7100","g","Film","Movie"),preferServerStream=true)
            assertEquals(entry,request.sourceMediaUrl)
            val source=TranscodeSource.from(http,request.stableUrl) ?: TranscodeSource.from(http,request.sourceMediaUrl!!)!!
            val id="a".repeat(32)
            mediaIndex.enqueue(MockResponse().setBody("""{"protocol":"mediaindex-cloud-video-v1","variants":[{"id":"$id","width":1920,"height":1080,"bandwidth":8000000}]}"""))
            assertEquals("1080P",source.cloudVideo().discover().single().badge)
            val call=mediaIndex.takeRequest()
            assertEquals("/api/cloud-video/discover",call.path)
            assertEquals("fixture-asset",JSONObject(call.body.readUtf8()).getString("assetToken"))
            for(header in listOf("X-Emby-Token","Authorization","Cookie")) assertNull(call.getHeader(header))
        }}
    }
    @Test fun finalCdnAndNasRoutesCannotBeUsedToGuessMediaIndexAuthorization() {
        val http=SafeHttp()
        for(url in listOf("https://cdn.example/video.mkv?sign=fixture","https://nas.example/emby/videos/1/stream.mp4"))
            assertNull(TranscodeSource.from(http,url))
    }
}
