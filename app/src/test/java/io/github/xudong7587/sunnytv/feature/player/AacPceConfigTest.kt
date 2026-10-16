package io.github.xudong7587.sunnytv.feature.player

import org.junit.Assert.*
import org.junit.Test

class AacPceConfigTest {
    @Test fun standardAndCompleteAudioConfigsAreNotRewritten() {
        assertTrue(AacPceConfig.incomplete(byteArrayOf(0x12,0)))
        assertFalse(AacPceConfig.incomplete(byteArrayOf(0x12,0x10)))
        assertFalse(AacPceConfig.incomplete(byteArrayOf(0x12,0x30)))
        assertFalse(AacPceConfig.incomplete(byteArrayOf(0x12,0,0)))
        assertFalse(AacPceConfig.incomplete(byteArrayOf()))
    }
    @Test fun malformedOrMissingPceDoesNotInventChannelLayout() {
        for(frame in listOf(byteArrayOf(),byteArrayOf(0),byteArrayOf(0xa0.toByte()),ByteArray(8) {0xff.toByte()})) {
            assertNull(AacPceConfig.fromFrame(byteArrayOf(0x12,0),frame))
        }
    }
    @Test fun oversizedNetworkFramesAreRejectedBeforeParsing() {
        assertNull(AacPceConfig.fromFrame(byteArrayOf(0x12,0),ByteArray(8193) {0xa0.toByte()}))
    }
}
