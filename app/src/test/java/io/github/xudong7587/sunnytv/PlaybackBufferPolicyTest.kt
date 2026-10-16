package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.playback.PlaybackBufferPolicy
import org.junit.Assert.*
import org.junit.Test

class PlaybackBufferPolicyTest {
    private val mib=1024L*1024L

    @Test fun highBitrateReadAheadExceedsTheOldFourSecondLimit() {
        val standard=PlaybackBufferPolicy.targetBytes(256*mib)
        val seconds=standard*8.0/79_130_000
        assertTrue(seconds>8 && seconds<10)
        assertTrue(PlaybackBufferPolicy.targetBytes(512*mib)>standard)
    }

    @Test fun largeAndSmallHeapsKeepMemoryHeadroom() {
        for(heap in listOf(32,64,128,256,512,1024).map {it*mib}) {
            val target=PlaybackBufferPolicy.targetBytes(heap)
            assertTrue(target>0 && target<=heap/3)
            assertTrue(target<=256*mib)
        }
        assertEquals(256*mib,PlaybackBufferPolicy.targetBytes(Long.MAX_VALUE).toLong())
    }

    @Test(expected=IllegalArgumentException::class)
    fun invalidHeapIsRejected() {PlaybackBufferPolicy.targetBytes(0)}
}
