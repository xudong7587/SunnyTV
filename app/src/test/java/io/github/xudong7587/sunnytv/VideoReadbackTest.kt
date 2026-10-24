package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.feature.ambilight.VideoCrop
import io.github.xudong7587.sunnytv.feature.ambilight.VideoReadbackPacing
import io.github.xudong7587.sunnytv.feature.ambilight.VideoReadbackQuality
import org.junit.Assert.*
import org.junit.Test

class VideoReadbackTest {
    @Test fun affordableReadbackRestoresDetailAfterOneCheapProbe() {
        val quality=VideoReadbackQuality()
        assertFalse(quality.fine)
        quality.complete(10,10)
        assertTrue(quality.fine)
        repeat(20) {quality.complete(25,100L+it*100)}
        assertTrue(quality.fine);assertFalse(quality.suspended)
    }
    @Test fun expensiveDetailFallsBackAndRecoveryRequiresSustainedHeadroom() {
        val quality=VideoReadbackQuality()
        quality.complete(10,10);quality.complete(80,100)
        assertFalse(quality.fine)
        repeat(6) {quality.complete(10,1000L+it*100)}
        assertFalse("Do not alternate quality every frame",quality.fine)
        quality.complete(10,30_100)
        assertTrue(quality.fine)
        quality.complete(80,30_200)
        repeat(6) {quality.complete(10,31_000L+it*100)}
        quality.complete(20,60_200)
        assertFalse("One slower coarse copy breaks the recovery streak",quality.fine)
        repeat(6) {quality.complete(10,60_300L+it*100)}
        assertTrue(quality.fine)
    }
    @Test fun slowProbeKeepsCoarseAndSevereDetailedStallIsNeverRepeated() {
        val quality=VideoReadbackQuality()
        quality.complete(70,70)
        assertFalse(quality.fine);assertFalse(quality.suspended)
        quality.reset();quality.complete(10,100);quality.complete(300,500)
        assertFalse(quality.fine);assertFalse("Try the cheaper grid before giving up",quality.suspended)
        repeat(20) {quality.complete(5,100_000L+it*100)}
        assertFalse("Never probe the stalling detailed path again in this playback",quality.fine)
        quality.complete(2000,200_000)
        assertTrue(quality.suspended)
        quality.reset();quality.complete(10,210_000)
        assertTrue(quality.fine);assertFalse(quality.suspended)
    }
    @Test fun slowCoarseCopyKeepsFourEdgesAtLowFrequencyRatherThanFreezing() {
        val quality=VideoReadbackQuality()
        quality.complete(300,300)
        assertFalse(quality.suspended);assertFalse(quality.fine);assertTrue(quality.lowFrequency)
        assertEquals(5000L,quality.cooldown(300,10))
        quality.complete(950,6250)
        assertFalse(quality.suspended);assertEquals(8550L,quality.cooldown(950,10))
        repeat(6) {quality.complete(10,40_000L+it*100)}
        assertFalse(quality.lowFrequency)
        assertFalse("Never re-probe a path that stalled",quality.fine)
    }
    @Test fun multiSecondDriverStallStillHoldsUntilNewPlaybackOrExplicitRetry() {
        val quality=VideoReadbackQuality()
        quality.complete(2000,2000);quality.complete(1,100_000)
        assertTrue(quality.suspended);assertFalse(quality.fine)
        quality.reset();quality.complete(1,100_100)
        assertTrue(quality.fine);assertFalse(quality.suspended)
    }
    @Test fun nativeDecoderCropSamplesOnlyTheVisibleSourceCenter() {
        val geometry=io.github.xudong7587.sunnytv.feature.player.VideoGeometry
        val bounds=geometry.bounds(1920,1080,2.4f,true,false)
        val region=geometry.visibleFraction(1920,1080,bounds)
        assertEquals(336f/2592,region[0],.0001f)
        assertEquals(1-336f/2592,region[2],.0001f)
        assertEquals(0f,region[1],0f);assertEquals(1f,region[3],0f)
    }
    @Test fun fourKBufferIn1080ViewStillSamplesAllFourEdges() {
        val width=160;val height=90
        val full=IntArray(width*height) {i->when {i/width<10->0xff0000;i/width>=80->0x0000ff;i%width<10->0xffff00;i%width>=150->0x00ff00;else->0}}
        val bounds=VideoCrop.bufferRect(1920,1080,0,0,1920,1080,width,height)
        val output=IntArray(full.size)
        VideoCrop.visiblePixels(full,output,width,height,bounds)
        assertArrayEquals(full,output)
        assertEquals(0x0000ff,output[85*width+80])
        assertEquals(0x00ff00,output[45*width+155])
    }
    @Test fun zoomSelectsVisibleCenterOfFullBuffer() {
        val pixels=IntArray(160*90) {it%160}
        val bounds=VideoCrop.bufferRect(2560,1440,320,180,2240,1260,160,90)
        val output=IntArray(pixels.size)
        VideoCrop.visiblePixels(pixels,output,160,90,bounds)
        assertEquals(20,output.first());assertEquals(139,output.last())
    }
    @Test fun slowReadbackBacksOffInsteadOfSaturatingVendorVideoHardware() {
        assertEquals(90,VideoReadbackPacing.cooldown(10,10))
        assertEquals(2970,VideoReadbackPacing.cooldown(330,10))
        assertEquals(9000,VideoReadbackPacing.cooldown(1000,15))
    }
    @Test fun allReadbackRatesRespectTheRenderThreadBudget() {
        for(fps in listOf(5,10,15))for(cost in 1L..249L) {
            val idle=VideoReadbackPacing.cooldown(cost,fps)
            assertTrue("copy=$cost fps=$fps must not exceed ten percent duty",cost*10<=cost+idle)
            assertTrue("configured FPS remains a ceiling",cost+idle>=1000/fps)
        }
        assertEquals(315,VideoReadbackPacing.cooldown(35,15))
        assertFalse(VideoReadbackPacing.blocksPlayback(249))
        assertTrue(VideoReadbackPacing.blocksPlayback(250))
        assertTrue(VideoReadbackPacing.blocksPlayback(984))
    }
    @Test fun coarseGridStillAveragesAllFourStripSections() {
        val w=io.github.xudong7587.sunnytv.feature.ambilight.VideoSampleGrid.WIDTH
        val h=io.github.xudong7587.sunnytv.feature.ambilight.VideoSampleGrid.HEIGHT
        val pixels=IntArray(w*h) {i->when {
            i/w==0->0xff0000;i/w==h-1->0x0000ff;i%w==0->0xffff00;i%w==w-1->0x00ff00;else->0x202020
        }}
        val edges=io.github.xudong7587.sunnytv.feature.ambilight.EdgeColors.sample(pixels,w,h)
        assertEquals(0xff0000,edges[0]);assertEquals(0x0000ff,edges[2])
        assertTrue((edges[1] shr 8 and 255)>200 && (edges[1] shr 16 and 255)<40)
        assertTrue((edges[3] shr 16 and 255)>200 && (edges[3] shr 8 and 255)>200)
        assertTrue("Readback payload stays below 4 KiB",w*h*4<4096)
    }
}
