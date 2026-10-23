package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.feature.ambilight.Sp658eProtocol
import io.github.xudong7587.sunnytv.feature.ambilight.LightOutputLimit
import org.junit.Assert.*
import org.junit.Test

class LightCommandTest {
    @Test fun fullWhiteIsLimitedButStableWarmColorAndBlackArePreserved() {
        assertEquals(0x3d3d3d,LightOutputLimit.fixed(0xffffff,255))
        assertEquals(0xffffff,LightOutputLimit.fixed(0xffffff,61))
        assertEquals(0xff4d05,LightOutputLimit.fixed(0xff4d05,110))
        assertEquals(0,LightOutputLimit.fixed(0,255))
    }
    @Test fun staticAndVideoShareTheSameLoadEnvelopeWithoutMutatingInput() {
        val original=Sp658eProtocol.runs(IntArray(96) {0xffffff})
        val result=LightOutputLimit.slots(original,255)
        assertEquals(96,result[1].toInt() and 255)
        assertEquals(61,result[2].toInt() and 255)
        assertEquals(61,result[3].toInt() and 255)
        assertEquals(61,result[4].toInt() and 255)
        assertEquals(255,original[2].toInt() and 255)
    }
    @Test fun movingStaticPaletteDoesNotRestartEffect() {
        for(color in listOf(0xff8000,0xffe0bb,0xffffff,0xcce0ff)) {
            val packets=Sp658eProtocol.update(color,null,255,1,255)
            assertEquals(1,packets.size)
            assertArrayEquals(Sp658eProtocol.fixed(color,255).last(),packets.single())
        }
    }
    @Test fun videoRefreshOnlyWritesColorSlots() {
        val slots=Sp658eProtocol.runs(IntArray(96) {0xffffff})
        repeat(100) {
            val packets=Sp658eProtocol.update(null,slots,64,7,64)
            assertEquals(listOf(0x63),packets.map {it[1].toInt() and 255})
        }
        assertEquals(listOf(0x52,0x63),Sp658eProtocol.update(null,slots,128,7,64).map {it[1].toInt() and 255})
    }
    @Test fun reconnectAndModeSwitchStillInitializeEffect() {
        val slots=Sp658eProtocol.runs(IntArray(96) {0xff8000})
        for(mode in listOf(-1,1))assertEquals(listOf(0x52,0x63,0x53),Sp658eProtocol.update(null,slots,64,mode,64).map {it[1].toInt() and 255})
        for(mode in listOf(-1,7))assertEquals(listOf(0x53,0x52),Sp658eProtocol.update(0,null,255,mode,255).map {it[1].toInt() and 255})
    }
}
