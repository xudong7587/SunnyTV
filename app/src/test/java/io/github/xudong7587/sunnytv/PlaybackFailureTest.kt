package io.github.xudong7587.sunnytv

import androidx.media3.common.PlaybackException
import io.github.xudong7587.sunnytv.core.playback.PlaybackFailure
import org.junit.Assert.*
import org.junit.Test
import java.io.EOFException
import java.io.IOException
import java.net.SocketTimeoutException
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
}
