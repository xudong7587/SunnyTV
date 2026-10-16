package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.playback.PlaybackRecovery
import org.junit.Assert.*
import org.junit.Test

class CloudPlaybackRetryTest {
    @Test fun interruptedResponseAndSegmentsRecoverWithABoundedBackoff() {
        val causes=listOf("HttpDataSourceException","IOException","ExecutionException","EOFException")
        assertEquals(listOf(500L,1000L,2000L,4000L), (1..4).map {
            PlaybackRecovery.cloudRetryDelay(it,2001,null,causes)
        })
        assertNull(PlaybackRecovery.cloudRetryDelay(5,2001,null,causes))
        assertEquals(500L,PlaybackRecovery.cloudRetryDelay(1,2002,null,listOf("SocketTimeoutException")))
        assertEquals(500L,PlaybackRecovery.cloudRetryDelay(1,2004,503,listOf("InvalidResponseCodeException")))
    }
    @Test fun permanentFailuresAndParserEofAreNotRetried() {
        for(status in listOf(401,403,404,416))
            assertNull(PlaybackRecovery.cloudRetryDelay(1,2004,status,listOf("InvalidResponseCodeException")))
        for(cause in listOf("SSLHandshakeException","UnknownHostException","ParserException"))
            assertNull(PlaybackRecovery.cloudRetryDelay(1,2001,null,listOf(cause,"EOFException")))
        assertNull(PlaybackRecovery.cloudRetryDelay(1,0,null,listOf("EOFException")))
    }
}
