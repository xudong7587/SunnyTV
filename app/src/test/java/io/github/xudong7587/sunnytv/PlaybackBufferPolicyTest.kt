package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.playback.PlaybackBufferPolicy
import org.junit.Assert.*
import org.junit.Test

class PlaybackBufferPolicyTest {
    private val mib=1024L*1024L
    @Test fun largeHeapCanCoverFifteenSecondsAtNinetyMbps() {
        val budget=PlaybackBufferPolicy.targetBytes(512*mib,16*1024*mib,8*1024*mib,false)
        assertEquals(256*mib,budget.toLong())
        assertTrue(budget*8.0/90_710_000>15)
        assertEquals(512*mib,PlaybackBufferPolicy.targetBytes(2048*mib,16*1024*mib,8*1024*mib,false).toLong())
    }
    @Test fun smallHeapAndSystemPressureStillLimitAllocation() {
        for(heap in listOf(32,64,128,256,512,1024).map {it*mib}) {
            val target=PlaybackBufferPolicy.targetBytes(heap,2048*mib,512*mib,false).toLong()
            assertTrue(target>0 && target<=heap/2 && target<=128*mib)
        }
        assertEquals(32*mib,PlaybackBufferPolicy.targetBytes(1024*mib,2048*mib,128*mib,true).toLong())
    }
    @Test(expected=IllegalArgumentException::class)
    fun invalidHeapIsRejected() {PlaybackBufferPolicy.targetBytes(0)}
}
