package io.github.xudong7587.sunnytv

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.player.PlayerActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

/** Generated solid-colour H.264 + synthetic VTT only; no real provider/media credentials. */
class CloudSubtitleContinuityTest {
    @get:Rule val rule=createEmptyComposeRule()
    private fun player(a:PlayerActivity)=PlayerActivity::class.java.getDeclaredMethod("getPlayer")
        .apply {isAccessible=true}.invoke(a) as ExoPlayer

    private class Fixture(embedded:Boolean=false,pceAudio:Boolean=false):AutoCloseable {
        val server=ServerSocket(0,8,InetAddress.getByName("127.0.0.1"))
        val paths=CopyOnWriteArrayList<String>()
        val root="http://127.0.0.1:${server.localPort}"
        val variant="a".repeat(32)
        val sid="b".repeat(32)
        val token="t".repeat(43)
        val metadata="""{"id":"$variant","width":3840,"height":2160,"bandwidth":7500000}"""
        private val assets=InstrumentationRegistry.getInstrumentation().context.assets
        private val mp4=assets.open(if(embedded) "quality/embedded.mp4" else "quality/original.mp4").use {it.readBytes()}
        private val ts=assets.open("quality/cloud.ts").use {it.readBytes()}
        private val thread=Thread {
            while(!server.isClosed) {
                val socket=runCatching {server.accept()}.getOrNull() ?: break
                Thread {
                    socket.use {s->runCatching {
                        s.soTimeout=5000
                        val input=s.getInputStream().bufferedReader()
                        val line=input.readLine() ?: return@runCatching
                        val path=line.split(' ')[1].substringBefore('?');paths+=path
                        val headers=mutableMapOf<String,String>()
                        while(true) {
                            val header=input.readLine() ?: break
                            if(header.isEmpty()) break
                            headers[header.substringBefore(':').lowercase()]=header.substringAfter(':').trim()
                        }
                        var remaining=headers["content-length"]?.toIntOrNull() ?: 0
                        while(remaining>0) {if(input.read()<0) break;remaining--}
                        val body=when {
                            path=="/api/play/fixture" || path=="/third-party.mp4" -> mp4
                            path=="/subtitle.vtt" -> "WEBVTT\n\n00:00:00.000 --> 00:00:29.900\n字幕连续性测试\n".toByteArray()
                            path=="/api/cloud-video/discover" -> """{"protocol":"mediaindex-cloud-video-v1","variants":[$metadata]}""".toByteArray()
                            path=="/api/cloud-video/sessions" -> """{"protocol":"mediaindex-cloud-video-v1","sessionId":"$sid","sessionToken":"$token","playlistUrl":"/api/cloud-video/sessions/$sid/index.m3u8?st=$token","variant":$metadata,"delivery":"direct","seekMode":"hls-vod"}""".toByteArray()
                            path.endsWith("index.m3u8") -> if(pceAudio) assets.open("quality/pce.m3u8").use {it.readBytes()} else "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:30\n#EXT-X-MEDIA-SEQUENCE:0\n#EXTINF:30.0,\ncloud.ts\n#EXT-X-ENDLIST\n".toByteArray()
                            pceAudio && path.substringAfterLast('/').matches(Regex("pce-[0-9]+\\.ts")) -> assets.open("quality/"+path.substringAfterLast('/')).use {it.readBytes()}
                            path.endsWith("cloud.ts") -> ts
                            else -> "{}".toByteArray()
                        }
                        val start=headers["range"]?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                        val end=headers["range"]?.substringAfter('-')?.toIntOrNull()?.coerceAtMost(body.lastIndex) ?: body.lastIndex
                        val partial=headers.containsKey("range") && start in body.indices && end>=start
                        val payload=if(partial) body.copyOfRange(start,end+1) else body
                        val type=when {path.endsWith("vtt")->"text/vtt";path.endsWith("m3u8")->"application/vnd.apple.mpegurl";path.endsWith("ts")->"video/mp2t";body===mp4->"video/mp4";else->"application/json"}
                        val range=if(partial) "Content-Range: bytes $start-$end/${body.size}\r\n" else ""
                        s.getOutputStream().apply {
                            write("HTTP/1.1 ${if(partial) 206 else 200} OK\r\nContent-Type: $type\r\nContent-Length: ${payload.size}\r\n${range}Connection: close\r\n\r\n".toByteArray())
                            write(payload);flush()
                        }
                    }}
                }.apply {isDaemon=true;start()}
            }
        }.apply {isDaemon=true;start()}
        override fun close() {server.close();thread.join(1000)}
    }

    @Test fun embeddedSubtitleSurvivesAutomaticDiscoveryAndQualitySwitch() = verifyContinuity(true)
    @Test fun externalSubtitleSurvivesAutomaticDiscoveryAndQualitySwitch() = verifyContinuity(false)

    @Test fun cloudInBandPcePrimesBeforePausedResumeAndSurvivesSeeking() {
        Fixture(pceAudio=true).use {f->
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            ActivityScenario.launch<PlayerActivity>(PlayerActivity.intent(context,
                PlaybackRequest("",f.root+"/api/play/fixture","PCE 云端音频",startMs=4000,
                    subtitles=listOf(ExternalSubtitle(f.root+"/subtitle.vtt","text/vtt","zh","测试字幕","pce-sub",true))))).use {scenario->
                rule.waitUntil(15000) {f.paths.any {it=="/api/cloud-video/discover"}}
                var old:ExoPlayer?=null
                var resumeTarget=0L
                scenario.onActivity {old=player(it);old!!.pause();resumeTarget=old!!.currentPosition}
                rule.onNodeWithTag("player:quality").performClick()
                rule.onNodeWithText("115 云端 · 4K · 7.5 Mbps").performClick()
                var audioDiagnostic=""
                fun readyAt(target:Long):Boolean {
                    var ready=false
                    scenario.onActivity {a->val p=player(a)
                        assertNull("Cloud audio failed: ${p.playerError?.errorCodeName}",p.playerError)
                        audioDiagnostic="state=${p.playbackState}; position=${p.currentPosition}; mime=${p.audioFormat?.sampleMimeType}; channels=${p.audioFormat?.channelCount}; decoded=${p.audioDecoderCounters?.renderedOutputBufferCount}; cues=${p.currentCues.cues.map {it.text}}; bootstrap="+PlayerActivity::class.java.getDeclaredField("cloudBootstrapTarget").apply {isAccessible=true}.get(a)
                        ready=p!==old && p.playbackState==androidx.media3.common.Player.STATE_READY &&
                            kotlin.math.abs(target-p.currentPosition)<700 && p.audioDecoderCounters?.renderedOutputBufferCount?.let {it>0}==true &&
                            p.currentCues.cues.any {it.text.toString().contains("字幕连续性测试")}
                    }
                    return ready
                }
                try {rule.waitUntil(25000) {readyAt(resumeTarget)}} catch(e:Throwable) {throw AssertionError(audioDiagnostic,e)}
                scenario.onActivity {a->val p=player(a)
                    assertFalse(p.playWhenReady)
                    assertEquals(androidx.media3.common.MimeTypes.AUDIO_AAC,p.audioFormat!!.sampleMimeType)
                    assertEquals(8,p.audioFormat!!.channelCount)
                    assertTrue(p.audioFormat!!.initializationData[0].size>2)
                    p.seekTo(1500)
                }
                try {rule.waitUntil(10000) {readyAt(1500)}} catch(e:Throwable) {throw AssertionError(audioDiagnostic,e)}
                scenario.onActivity {player(it).seekTo(4000)}
                try {rule.waitUntil(10000) {readyAt(4000)}} catch(e:Throwable) {throw AssertionError(audioDiagnostic,e)}
                scenario.onActivity {player(it).play()}
                rule.waitUntil(10000) {
                    var advancing=false
                    scenario.onActivity {a->assertNull(player(a).playerError);advancing=player(a).currentPosition>5000}
                    advancing
                }
                val store=io.github.xudong7587.sunnytv.core.storage.ConfigStore(context)
                val previous=store.realtimePlaybackInfo()
                try {
                    if(!previous) rule.onNodeWithTag("player:stats").performClick()
                    rule.waitUntil(5000) {rule.onAllNodesWithText("320×180",substring=true).fetchSemanticsNodes().isNotEmpty()}
                    rule.onNodeWithTag("player:realtime-overlay").assertTextContains("AAC",substring=true)
                        .assertTextContains("8 声道",substring=true).assertTextContains("流标称码率 7.50 Mbps",substring=true)
                    scenario.onActivity {it.onBackPressedDispatcher.onBackPressed()}
                    rule.onNodeWithTag("player:stats").assertDoesNotExist()
                    rule.onNodeWithTag("player:realtime-overlay").assertIsDisplayed()
                    val screenshot=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                    java.io.File(context.cacheDir,"realtime-info.png").outputStream().use {
                        check(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
                    }
                    screenshot.recycle()
                } finally {store.setRealtimePlaybackInfo(previous)}
            }
        }
    }

    private fun verifyContinuity(embedded:Boolean) {
        Fixture(embedded=embedded).use {f->
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            val request=PlaybackRequest("",f.root+"/api/play/fixture","自动查询与字幕",startMs=5000,
                subtitles=listOf(ExternalSubtitle(f.root+"/subtitle.vtt","text/vtt","zh","测试字幕","emby-sub:4",!embedded)),
                sourceTracks=listOf(MediaTrack(4,"Subtitle","zh","测试字幕","mov_text")),
                explicitSubtitle=true,subtitleOrdinal=if(embedded) 0 else -1,subtitleTrackId=if(embedded) "" else "emby-sub:4")
            ActivityScenario.launch<PlayerActivity>(PlayerActivity.intent(context,request)).use {scenario->
                rule.waitUntil(15000) {f.paths.count {it=="/api/cloud-video/discover"}==1}
                var old:ExoPlayer?=null
                var pausedPosition=0L
                scenario.onActivity {
                    old=player(it);old!!.pause();pausedPosition=old!!.currentPosition
                    val text=old!!.currentTracks.groups.filter {it.type==C.TRACK_TYPE_TEXT}
                    assertTrue(text.any {g->(0 until g.length).any {i->g.isTrackSelected(i) && (SubtitleSelection.stableId(g.getTrackFormat(i).id.orEmpty()).startsWith("emby-sub:") != embedded)}})
                }
                rule.onNodeWithTag("player:quality").performClick()
                rule.onNodeWithText("115 云端 · 4K · 7.5 Mbps").performClick()
                var diagnostic=""
                try {rule.waitUntil(15000) {
                    var ready=false
                    scenario.onActivity {a->
                        val p=player(a)
                        ready=p!==old && p.currentCues.cues.any {it.text.toString().contains("字幕连续性测试")}
                        val req=PlayerActivity::class.java.getDeclaredField("request").apply {isAccessible=true}.get(a) as PlaybackRequest
                        diagnostic="state=${p.playbackState}; id=${req.subtitleTrackId}; title=${req.subtitleTitle}; ordinal=${req.subtitleOrdinal}; tracks="+
                            p.currentTracks.groups.filter {it.type==C.TRACK_TYPE_TEXT}.flatMap {g->(0 until g.length).map {i->"${g.getTrackFormat(i).id}:${g.isTrackSelected(i)}"}}+
                            "; cues=${p.currentCues.cues.map {it.text}}"
                    }
                    ready
                }} catch(e:Throwable) {throw AssertionError(diagnostic,e)}
                scenario.onActivity {a->
                    val p=player(a)
                    assertFalse(p.playWhenReady)
                    assertTrue("Paused quality switch: expected $pausedPosition, got ${p.currentPosition}",kotlin.math.abs(pausedPosition-p.currentPosition)<=1000L)
                    assertTrue(p.currentTracks.groups.any {it.type==C.TRACK_TYPE_TEXT && it.isSelected})
                }
                assertEquals(1,f.paths.count {it=="/api/cloud-video/discover"})
                rule.onNodeWithTag("player:subtitle").performClick()
                rule.onNodeWithText("关闭字幕").performClick()
                rule.onNodeWithTag("player:quality").performClick()
                rule.onNodeWithText("原画").performClick()
                rule.waitUntil(10000) {
                    var original=false
                    scenario.onActivity {a->original=player(a).currentMediaItem?.localConfiguration?.uri?.path=="/api/play/fixture"}
                    original
                }
                scenario.onActivity {assertTrue(C.TRACK_TYPE_TEXT in player(it).trackSelectionParameters.disabledTrackTypes)}
            }
        }
    }

    @Test fun thirdPartyQualityListKeepsOriginalPlayerAndDoesNotQueryCloud() {
        Fixture().use {f->
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            ActivityScenario.launch<PlayerActivity>(PlayerActivity.intent(context,PlaybackRequest("",f.root+"/third-party.mp4","第三方 STRM"))).use {scenario->
                var old:ExoPlayer?=null
                scenario.onActivity {old=player(it)}
                rule.onNodeWithTag("player:quality").performClick()
                rule.onNodeWithText("当前播放源不支持 115 云端清晰度",substring=true).assertDoesNotExist()
                rule.onNodeWithText("查询 / 刷新 115 云端清晰度").assertDoesNotExist()
                rule.onNodeWithText("115 连接：优先直连").assertDoesNotExist()
                rule.onNodeWithText("原画").performClick()
                scenario.onActivity {assertSame(old,player(it))}
                assertFalse(f.paths.any {it.contains("cloud-video")})
            }
        }
    }
}
