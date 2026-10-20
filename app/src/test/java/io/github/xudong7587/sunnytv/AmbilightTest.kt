package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.feature.ambilight.*
import org.junit.Assert.*
import org.junit.Test

class AmbilightTest {
    @Test fun visibleCropUsesVideoBufferCoordinates() {
        assertArrayEquals(intArrayOf(0,0,3840,2160),VideoCrop.bufferRect(1920,1080,0,0,1920,1080,3840,2160))
        assertArrayEquals(intArrayOf(384,0,3456,2160),VideoCrop.bufferRect(2400,1350,240,0,2160,1350,3840,2160))
    }
    private fun hex(s:String)=s.chunked(2).map {it.toInt(16).toByte()}.toByteArray()
    @Test fun capturedStaticColorCommand() {
        assertArrayEquals(hex("535234010004f4460ef4"),Sp658eProtocol.fixed(0xff4d05,255).last())
    }
    @Test fun requestedSavedCornersAndBlueEndpoints() {
        val l=LightLayout();assertEquals(-1,l.direction());assertEquals(listOf(31,15,34,16),l.lengths())
        val colors=intArrayOf(0xff0000,0x00ff00,0x0000ff,0xffff00)
        val p=Sp658eProtocol.edges(l,colors)
        assertArrayEquals(hex("01040000ff0f00ff001fff000010ffff001e0000ff0000000000000000"),p)
    }
    @Test fun reversedAndCrossedLayouts() {
        assertEquals(1,LightLayout(100,0,25,50,75).direction())
        assertNull(LightLayout(100,0,50,25,75).direction())
        assertNull(LightLayout(96,49,18,3,3).direction())
    }
    @Test fun markerHasExactlyOneWhiteAddressAtEveryPosition() {
        for(address in 0 until 600) {
            val p=Sp658eProtocol.marker(600,address)
            val result=mutableListOf<Int>()
            for(slot in 0..6) {val k=1+slot*4;repeat(p[k].toInt() and 255) {
                result+=((p[k+1].toInt() and 255) shl 16) or ((p[k+2].toInt() and 255) shl 8) or (p[k+3].toInt() and 255)
            }}
            assertEquals(600,result.size);assertEquals(listOf(address),result.indices.filter {result[it]!=0})
        }
    }
    @Test(expected=IllegalArgumentException::class) fun eighthRunIsRejected() {
        Sp658eProtocol.runs(IntArray(8) {it})
    }
    @Test fun stateFragmentsAreReassembledAndExpired() {
        val d=Sp658eStateDecoder();val first=byteArrayOf(0x53,2,0,2,0,2,7,8);val last=byteArrayOf(0x53,2,0,2,1,1,9)
        assertNull(d.accept(first,100));assertNull(d.accept(first,101))
        assertArrayEquals(byteArrayOf(0x53,2,0,1,0,0,7,8,9),d.accept(last,102))
        assertNull(d.accept(first,200));assertNull(d.accept(last,4000))
        assertNull(d.accept(last.copyOfRange(0,6),4001))
    }
    @Test fun samplesActualFourEdges() {
        val w=160;val h=90;val p=IntArray(w*h){0x808080}
        for(y in 0 until h)for(x in 0 until w)p[y*w+x]=when {
            y<8->0xff0000;y>=82->0x0000ff;x<13->0xffff00;x>=147->0x00ff00;else->0x808080
        }
        val c=EdgeColors.sample(p,w,h)
        assertEquals(0xff0000,c[0]);assertEquals(0x0000ff,c[2])
        assertTrue((c[1] shr 8 and 255)>200);assertTrue((c[3] shr 16 and 255)>200)
    }
    @Test fun letterboxDoesNotTurnTopAndBottomLightsBlack() {
        val p=IntArray(160*90){i->if(i/160 in 12..77)0x2277cc else 0}
        assertArrayEquals(IntArray(4){0x2277cc},EdgeColors.sample(p,160,90))
    }
}
