package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ViewportPolicyTest {
    @Test fun fullyVisibleRowsNeverMove() {
        for (top in 0..200 step 10) for (height in 40..240 step 20) for (offset in top..top+100 step 5) {
            assertEquals(0f,ViewportPolicy.reveal(offset.toFloat(),height.toFloat(),top.toFloat(),600f,260f),0f)
        }
    }
    @Test fun incompleteNextRowMovesExactlyOneStride() {
        assertEquals(220f,ViewportPolicy.reveal(450f,200f,120f,600f,220f),0f)
    }
    @Test fun previouslyHiddenRowMovesDownSymmetrically() {
        assertEquals(-220f,ViewportPolicy.reveal(90f,200f,120f,600f,220f),0f)
    }
    @Test fun fractionalLayoutRoundingDoesNotOscillate() {
        assertEquals(0f,ViewportPolicy.reveal(119.8f,200f,120f,600f,220f),0f)
        assertEquals(0f,ViewportPolicy.reveal(400.2f,200f,120f,600f,220f),0f)
    }
    @Test fun oversizedRowsAndTinyViewportsTerminate() {
        assertEquals(0f,ViewportPolicy.reveal(120f,900f,120f,240f,920f),0f)
        assertEquals(-20f,ViewportPolicy.reveal(100f,900f,120f,240f,920f),0f)
        assertEquals(0f,ViewportPolicy.reveal(100f,90f,240f,120f,110f),0f)
    }
    @Test fun invalidGeometryDoesNotIssueScroll() {
        assertEquals(0f,ViewportPolicy.reveal(Float.NaN,10f,0f,100f),0f)
        assertEquals(0f,ViewportPolicy.reveal(0f,Float.POSITIVE_INFINITY,0f,100f),0f)
        assertEquals(0f,ViewportPolicy.reveal(0f,0f,0f,100f),0f)
    }
    @Test fun leftTwoThirdsSkipsRightHandControlButRightEdgeCanReachIt() {
        assertTrue(ViewportPolicy.skipRightControl(599f,900f))
        assertFalse(ViewportPolicy.skipRightControl(600f,900f))
        assertFalse(ViewportPolicy.skipRightControl(880f,900f))
    }
    @Test fun cacheMigrationClampsUnknownValuesAndNeverUsesGigabytesOfHeap() {
        assertEquals(512,PerformancePolicy.cacheSize(-1))
        assertEquals(512,PerformancePolicy.cacheSize(Int.MAX_VALUE))
        PerformancePolicy.cacheSizesMiB.forEach {assertEquals(it,PerformancePolicy.cacheSize(it))}
        assertEquals(32*1024*1024,PerformancePolicy.memoryBytes(4L*1024*1024*1024,true))
        assertEquals(64*1024*1024,PerformancePolicy.memoryBytes(4L*1024*1024*1024,false))
    }
    @Test fun lowLoadDoesNotMeanAnimationsDisabled() {
        assertTrue(PerformancePolicy.lean("auto",true))
        assertFalse(PerformancePolicy.lean("balanced",true))
        assertTrue(PerformancePolicy.lean("low",false))
        assertEquals(1280,PerformancePolicy.imageWidth(3840,true))
        assertEquals(3840,PerformancePolicy.imageWidth(3840,false))
    }
}
