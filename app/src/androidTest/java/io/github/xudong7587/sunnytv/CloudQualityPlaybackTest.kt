package io.github.xudong7587.sunnytv

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.exoplayer.ExoPlayer
import io.github.xudong7587.sunnytv.core.model.PlaybackRequest
import io.github.xudong7587.sunnytv.feature.player.PlayerActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real Activity/network integration with synthetic responses, no provider account. */
class CloudQualityPlaybackTest {
    @get:Rule val rule=createEmptyComposeRule()

    @Test fun cloudQueryIsExplicitAndFailedSwitchPreservesPlayerThenVodSwitchKeepsPositionAndPause() {
        val server=ServerSocket(0,8,InetAddress.getByName("127.0.0.1"))
        val release=CountDownLatch(1)
        val paths=CopyOnWriteArrayList<String>()
        val selections=CopyOnWriteArrayList<String>()
        val variant="a".repeat(32);val sid="b".repeat(32);val secret="t".repeat(43)
        val metadata="""{"id":"$variant","width":3840,"height":2160,"bandwidth":15000000}"""
        val failSelection=java.util.concurrent.atomic.AtomicBoolean(true)
        val playlistRequests=java.util.concurrent.atomic.AtomicInteger()
        val acceptor=Thread {
            while(!server.isClosed) {
                val socket=runCatching {server.accept()}.getOrNull() ?: break
                Thread {
                    socket.use {
                        runCatching {
                            val reader=it.getInputStream().bufferedReader()
                            val first=reader.readLine() ?: return@runCatching
                            val path=first.split(" ")[1];paths.add(path)
                            // A reused CDN/gateway connection can close before headers. Cloud HLS
                            // must retain its one retry even when the original item has an Emby fallback.
                            if(path.contains("/index.m3u8") && playlistRequests.incrementAndGet()==1) return@runCatching
                            var length=0
                            while(true) {
                                val line=reader.readLine() ?: break
                                if(line.isEmpty()) break
                                if(line.startsWith("Content-Length:",true)) length=line.substringAfter(':').trim().toInt()
                            }
                            val input=CharArray(length)
                            var read=0
                            while(read<length) {val count=reader.read(input,read,length-read);if(count<0) break;read+=count}
                            val delivery=if(path=="/api/cloud-video/sessions") org.json.JSONObject(String(input)).getString("delivery").also {selections.add(it)} else "direct"
                            val code=if(path=="/api/cloud-video/sessions" && failSelection.get()) 409 else 200
                            val body=when(path) {
                                "/api/transcode/capabilities" -> """{"protocol":"mediaindex-transcode-v1","available":false}"""
                                "/api/cloud-video/discover" -> """{"protocol":"mediaindex-cloud-video-v1","variants":[$metadata]}"""
                                "/api/cloud-video/sessions" -> if(failSelection.get()) "{}" else """{"protocol":"mediaindex-cloud-video-v1","sessionId":"$sid","sessionToken":"$secret","playlistUrl":"/api/cloud-video/sessions/$sid/index.m3u8?st=$secret","variant":$metadata,"delivery":"$delivery","seekMode":"hls-vod"}"""
                                else -> null
                            }
                            val output=it.getOutputStream()
                            if(body!=null) {
                                val data=body.toByteArray()
                                output.write("HTTP/1.1 $code Result\r\nContent-Type: application/json\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                output.write(data);output.flush()
                            } else {
                                output.write("HTTP/1.1 200 OK\r\nContent-Length: 1000000\r\nConnection: close\r\n\r\n".toByteArray());output.flush()
                                release.await(60,TimeUnit.SECONDS)
                            }
                        }
                    }
                }.apply {isDaemon=true;start()}
            }
        }.apply {isDaemon=true;start()}
        fun player(activity:PlayerActivity):ExoPlayer? = PlayerActivity::class.java.getDeclaredMethod("getPlayer").apply {isAccessible=true}.invoke(activity) as? ExoPlayer
        try {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            val request=PlaybackRequest("","http://127.0.0.1:${server.localPort}/api/play/fixture","云端协议测试",startMs=42000,
                fallbackUrl="http://127.0.0.1:${server.localPort}/emby-fixture")
            ActivityScenario.launch<PlayerActivity>(PlayerActivity.intent(context,request)).use {scenario ->
                rule.onNodeWithTag("player:quality").performClick()
                rule.waitUntil(5000) {paths.any {it=="/api/transcode/capabilities"}}
                assertFalse(paths.any {it.contains("cloud-video")})
                rule.onNodeWithText("查询 / 刷新 115 云端清晰度").performClick()
                rule.waitUntil(5000) {rule.onAllNodesWithText("115 云端 · 4K · 15.0 Mbps").fetchSemanticsNodes().isNotEmpty()}
                var before:ExoPlayer?=null
                scenario.onActivity {before=player(it);before!!.pause()}
                rule.onNodeWithText("115 云端 · 4K · 15.0 Mbps").performClick()
                rule.waitUntil(5000) {rule.onAllNodesWithText("115 暂无可用云端流，请检查登录态、权限或转码状态").fetchSemanticsNodes().isNotEmpty()}
                scenario.onActivity {assertSame(before,player(it));assertFalse(player(it)!!.playWhenReady)}
                failSelection.set(false)
                rule.onNodeWithText("115 云端 · 4K · 15.0 Mbps").performClick()
                rule.waitUntil(8000) {playlistRequests.get()>=2}
                assertFalse(paths.any {it=="/emby-fixture"})
                scenario.onActivity {
                    val after=player(it)!!
                    assertNotSame(before,after)
                    assertEquals(42000L,after.currentPosition)
                    assertFalse(after.playWhenReady)
                    assertTrue(after.currentMediaItem!!.localConfiguration!!.uri.path!!.startsWith("/api/cloud-video/"))
                }
                assertEquals(listOf("direct","direct"),selections.toList())
                rule.onNodeWithTag("player:quality").performClick()
                rule.onNodeWithText("115 连接：优先直连").performClick()
                rule.onNodeWithText("115 云端 · 4K · 15.0 Mbps").performClick()
                rule.waitUntil(5000) {selections.size==3}
                assertEquals("proxy",selections.last())
                assertFalse(paths.any {it.contains("/pause") || it.startsWith("/api/transcode/sessions")})
            }
        } finally {release.countDown();server.close();acceptor.join(1000)}
    }
}
