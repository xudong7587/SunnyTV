package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.feature.ambilight.*
import io.github.xudong7587.sunnytv.feature.player.*
import org.junit.Assert.*
import org.junit.Test

class LightStabilityTest {
    private fun high(c:Int)=maxOf(c shr 16 and 255,c shr 8 and 255,c and 255)
    @Test fun exposureChangesDoNotChangeBrightnessOrHue() {
        val filter=StableLightColors(0xff8000)
        var t=1000L
        for(v in listOf(0x402000,0x804000,0xff8000,0x201000,0x402000).flatMap {v->List(12){v}}) {
            val result=filter.next(IntArray(4){v},t);t+=100
            result.forEach {assertEquals(255,high(it));assertTrue((it shr 8 and 255) in 125..129)}
        }
    }
    @Test fun blackLightningBlackCannotLatchAnyOscillation() {
        val filter=StableLightColors(0x2060ff);var t=1000L
        repeat(40) {filter.next(IntArray(4){0x2060ff},t);t+=100}
        val before=filter.next(IntArray(4),t);t+=100
        repeat(4) {
            filter.next(IntArray(4){0xffffff},t);t+=100
            repeat(8) {assertArrayEquals(before,filter.next(IntArray(4),t));t+=100}
        }
        repeat(600) {assertArrayEquals(before,filter.next(IntArray(4),t));t+=100}
    }
    @Test fun sustainedColorChangeSettlesWithoutBrightnessPulses() {
        val filter=StableLightColors(0xff0000)
        var last=255
        repeat(80) {i->
            val c=filter.next(IntArray(4){0x0000ff},1000L+i*100)[0]
            assertEquals(255,high(c));assertTrue((c shr 16 and 255)<=last)
            last=c shr 16 and 255
        }
        assertEquals(0,last)
    }
    @Test fun darkEdgesExpandOnlyTowardTheirOwnSector() {
        val w=160;val h=90
        val pixels=IntArray(w*h) {i->val x=i%w;val y=i/w
            when {y in 9..18 && x in 40..120->0xff0000;y in 71..80 && x in 40..120->0x0000ff;else->0}
        }
        val colors=EdgeColors.sample(pixels,w,h)
        assertTrue(colors[0] shr 16 and 255>100);assertEquals(0,colors[0] and 255)
        assertTrue(colors[2] and 255>100);assertEquals(0,colors[2] shr 16 and 255)
    }
    @Test fun reconnectBackoffIsBoundedCancelableAndResetsAfterRecovery() {
        val policy=LightReconnect()
        val delays=List(10) {requireNotNull(policy.nextDelay())}
        assertEquals(1500L,delays.first());assertTrue(delays.zipWithNext().all {it.second>=it.first})
        assertTrue(delays.all {it<=30_000});assertNull(policy.nextDelay())
        policy.reset();assertEquals(1500L,policy.nextDelay());policy.cancel();assertNull(policy.nextDelay())
        policy.reset();assertEquals(1500L,policy.nextDelay())
    }
    @Test fun cropKeepsCircleRoundWhileStretchChangesAspect() {
        val crop=VideoGeometry.bounds(1920,1080,2.4f,true,false)
        val stretch=VideoGeometry.bounds(1920,1080,2.4f,false,true)
        assertEquals(2592,crop.width);assertEquals(1080,crop.height)
        assertEquals(2.4,crop.width.toDouble()/crop.height,.001)
        assertEquals(1920,stretch.width);assertEquals(1080,stretch.height)
        val fit=VideoGeometry.bounds(1920,1080,2.4f,false,false)
        assertEquals(1920,fit.width);assertEquals(800,fit.height)
    }
    @Test fun encodedBarsAreCroppedUniformlyButLightningIsNotABar() {
        val pixels=IntArray(160*90) {i->if(i/160 in 12..77)0x2060ff else 0}
        val area=VideoGeometry.picture(pixels,160,90)
        val bounds=VideoGeometry.bounds(1920,1080,16f/9,true,false,area)
        assertTrue(bounds.width>2500);assertTrue(bounds.height>1450)
        assertEquals(16.0/9,bounds.width.toDouble()/bounds.height,.002)
        assertEquals(PictureArea(),VideoGeometry.picture(IntArray(160*90) {if(it%160==80)0xffffff else 0},160,90))
    }
}
