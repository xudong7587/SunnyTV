package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.feature.ambilight.VideoCrop
import io.github.xudong7587.sunnytv.feature.ambilight.VideoReadbackPacing
import org.junit.Assert.*
import org.junit.Test

class VideoReadbackTest {
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
        assertEquals(5000,VideoReadbackPacing.cooldown(1000,15))
    }
}
