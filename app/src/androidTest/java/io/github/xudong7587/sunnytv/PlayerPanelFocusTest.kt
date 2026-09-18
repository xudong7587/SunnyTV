package io.github.xudong7587.sunnytv

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xudong7587.sunnytv.core.model.PlaybackRequest
import io.github.xudong7587.sunnytv.feature.player.PlayerActivity
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test

class PlayerPanelFocusTest {
    @get:Rule val rule=createEmptyComposeRule()

    @Test fun selectingSpeedAndDismissingPanelsRestoreTheirOpeningControl() {
        // A local response held before media bytes keeps preparation deterministic, without private media or services.
        val server=ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))
        val release=CountDownLatch(1)
        val worker=Thread {
            runCatching {
                server.accept().use {socket->
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: video/mp4\r\nContent-Length: 1000000\r\nConnection: close\r\n\r\n".toByteArray())
                        flush()
                    }
                    release.await(60,TimeUnit.SECONDS)
                }
            }
        }.apply {isDaemon=true;start()}
        try {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            val request=PlaybackRequest("","http://127.0.0.1:${server.localPort}/fixture.mp4","焦点测试")
            ActivityScenario.launch<PlayerActivity>(PlayerActivity.intent(context,request)).use {scenario->
                rule.onNodeWithTag("player:speed").performClick()
                rule.onNodeWithText("1.5x").performClick()
                rule.onNodeWithTag("player:speed").assertIsFocused().assertContentDescriptionEquals("倍速 1.5x")
                rule.onNodeWithTag("player:speed").performClick()
                rule.onNodeWithTag("player:close").performClick()
                rule.onNodeWithTag("player:speed").assertIsFocused()
                rule.onNodeWithTag("player:info").performClick()
                scenario.onActivity {it.onBackPressedDispatcher.onBackPressed()}
                rule.onNodeWithTag("player:info").assertIsFocused()
            }
        } finally {
            release.countDown()
            server.close()
            worker.join(1000)
        }
    }
}
