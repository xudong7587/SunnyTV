package io.github.xudong7587.sunnytv

import androidx.media3.common.PlaybackException
import io.github.xudong7587.sunnytv.core.playback.PlaybackFailure
import io.github.xudong7587.sunnytv.core.playback.PlaybackRecovery
import org.junit.Assert.*
import org.junit.Test
import java.io.EOFException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutionException
import javax.net.ssl.SSLHandshakeException

class PlaybackFailureTest {
    private class UnexpectedLoaderException(cause:Throwable):IOException(cause)

    @Test fun mp4UnexpectedLoaderBoundsFailureEnablesOnlyTheNarrowFallback() {
        val error=PlaybackException("hidden",UnexpectedLoaderException(IndexOutOfBoundsException("private path")),2000)
        assertTrue(PlaybackFailure.isMp4IndexFailure(error,"video/mp4"))
        assertFalse(PlaybackFailure.isMp4IndexFailure(error,"video/x-matroska"))
        assertFalse(PlaybackFailure.isMp4IndexFailure(PlaybackException("hidden",IOException("other"),2000),"video/mp4"))
        val text=PlaybackFailure.describe(error,"首帧前读取","video/mp4")
        assertTrue(text.contains("MP4 索引"));assertFalse(text.contains("private path"))
    }

    @Test fun generic2000ShowsTheActualCauseWithoutLeakingPrivateAddresses() {
        val error=PlaybackException("request https://private.test/video?token=secret",
            IOException("Authorization: private",EOFException("/nas/private/movie.mp4")),2000)
        val text=PlaybackFailure.describe(error,"首帧前读取","video/mp4")
        assertTrue(text.contains("数据提前结束"));assertTrue(text.contains("EOFException"));assertTrue(text.contains("2000"))
        listOf("private","secret","Authorization","/nas/","https://").forEach {assertFalse(text.contains(it))}
    }
    @Test fun wrappedTimeoutAndTlsAreDistinctEvenForTheSameErrorCode() {
        fun describe(cause:Throwable)=PlaybackFailure.describe(PlaybackException("hidden",IOException(cause),2000),"播放读取",null)
        assertTrue(describe(SocketTimeoutException()).contains("超时"))
        assertTrue(describe(SSLHandshakeException("secret host")).contains("TLS"))
        assertFalse(describe(SSLHandshakeException("secret host")).contains("secret host"))
    }
    @Test fun unknownCauseDoesNotGuessACodecOrDisplayItsMessage() {
        val text=PlaybackFailure.describe(PlaybackException("secret",IOException("private credential"),2000),"首帧前读取",null)
        assertTrue(text.contains("尚未分类"));assertTrue(text.contains("IOException"))
        assertFalse(text.contains("解码器"));assertFalse(text.contains("credential"))
    }

    /** Mirrors the real dev26 report: Media3's OkHttp data source wraps an asynchronous call failure. */
    private fun wrappedTimeout()=PlaybackException("hidden",
        IOException(ExecutionException(SocketTimeoutException())),2001)

    @Test fun timeoutNamesTheRequestedHostAndStageWithoutLeakingTheSignedUrl() {
        val text=PlaybackFailure.describe(wrappedTimeout(),"首帧前读取","video/mp4",
            mediaUrl="http://cdn.test:5244/d/115/movie.mp4?sign=s%2Fcret",baseUrl="https://nas.test:8096/emby/",
            retryAttempts=1)
        assertTrue(text.contains("超时"))
        assertTrue(text.contains("等待响应"))
        assertTrue(text.contains("cdn.test:5244（直连媒体源，不是 Emby）"))
        assertTrue(text.contains("已自动重试 1 次"))
        assertTrue(text.contains("ExecutionException"))
        listOf("sign","cret","/d/115","https://","nas.test").forEach {assertFalse(text.contains(it))}
    }

    @Test fun embyHostIsNamedWhenThePlayerTalksToTheServerItself() {
        val text=PlaybackFailure.describe(wrappedTimeout(),"首帧前读取",null,
            mediaUrl="https://nas.test:8096/emby/Videos/1/stream?api_key=secret",baseUrl="https://nas.test:8096/emby/")
        assertTrue(text.contains("nas.test:8096（Emby 本机）"))
        assertFalse(text.contains("api_key"));assertFalse(text.contains("secret"))
    }

    @Test fun diagnosticsWithoutARouteStayUnchanged() {
        val text=PlaybackFailure.describe(wrappedTimeout(),"首帧前读取",null)
        // The stage hint comes from the wrapped asynchronous response call even without a URL.
        assertTrue(text.contains("阶段 等待响应"))
        assertEquals(4,text.lines().size)
        assertFalse(text.contains("请求主机"))
    }

    @Test fun retryBudgetIsOneAttemptBeforeTheFirstFrameOnly() {
        val names=PlaybackFailure.causeNames(wrappedTimeout())
        assertTrue(names.contains("SocketTimeoutException"))
        assertTrue(PlaybackRecovery.shouldRetry(0,PlaybackFailure.errorCodeOf(wrappedTimeout().cause!!),false,names))
        assertFalse(PlaybackRecovery.shouldRetry(1,2001,false,names))
        assertFalse(PlaybackRecovery.shouldRetry(0,2001,true,names))
    }
}
