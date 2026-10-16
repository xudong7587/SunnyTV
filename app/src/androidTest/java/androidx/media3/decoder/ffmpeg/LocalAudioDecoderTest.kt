package androidx.media3.decoder.ffmpeg

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LocalAudioDecoderTest {
    @Test fun nativeDecodersArePresent() {
        assertTrue(FfmpegLibrary.isAvailable())
        listOf(MimeTypes.AUDIO_AAC,MimeTypes.AUDIO_AC3,MimeTypes.AUDIO_E_AC3,MimeTypes.AUDIO_E_AC3_JOC,
            MimeTypes.AUDIO_DTS,MimeTypes.AUDIO_DTS_HD,MimeTypes.AUDIO_TRUEHD).forEach {
            assertTrue("Missing $it",FfmpegLibrary.supportsFormat(it))
        }
    }
    @Test fun eac3SixChannelsDecodeToNonSilentPcm() = decode("tone.eac3",MimeTypes.AUDIO_E_AC3,6)
    @Test fun dtsSixChannelsDecodeToNonSilentPcm() = decode("tone.dts",MimeTypes.AUDIO_DTS,6)
    @Test fun aacStereo44100DecodesRawHlsSamples() = decodeAac("tone-stereo.aac",2,byteArrayOf(0x12,0x10))
    @Test fun aacSixChannels44100DecodesRawHlsSamples() = decodeAac("tone-surround.aac",6,byteArrayOf(0x12,0x30))
    @Test fun aacEightChannels44100DecodesRawHlsSamples() = decodeAac("tone-eight.aac",8)
    @Test fun aacEightChannelsWithInBandPceDecodesRawHlsSamples() = decodeAac("tone-pce-eight.aac",8,byteArrayOf(0x12,0x00),0)
    @Test fun aacSixChannelsWithInBandPceDecodesRawHlsSamples() = decodeAac("tone-pce-surround.aac",6,byteArrayOf(0x12,0x00),0)

    private fun decodeAac(asset:String,channels:Int,config:ByteArray?=null,declaredChannels:Int=channels) {
        val adts=InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use {it.readBytes()}
        assertEquals(0xff,adts[0].toInt() and 0xff)
        assertEquals(0xf0,adts[1].toInt() and 0xf0)
        val header=if(adts[1].toInt() and 1==1) 7 else 9
        val length=((adts[3].toInt() and 3) shl 11) or ((adts[4].toInt() and 0xff) shl 3) or ((adts[5].toInt() and 0xe0) shr 5)
        assertTrue(length>header && length<=adts.size)
        // HLS's ADTS extractor supplies raw AAC plus AudioSpecificConfig to the renderer.
        val objectType=((adts[2].toInt() and 0xc0) shr 6)+1
        val frequencyIndex=(adts[2].toInt() and 0x3c) shr 2
        val channelConfig=((adts[2].toInt() and 1) shl 2) or ((adts[3].toInt() and 0xc0) shr 6)
        val asc=(objectType shl 11) or (frequencyIndex shl 7) or (channelConfig shl 3)
        val actualConfig=byteArrayOf((asc shr 8).toByte(),asc.toByte())
        if(config!=null) assertArrayEquals(config,actualConfig)
        decodePacket(adts.copyOfRange(header,length),MimeTypes.AUDIO_AAC,channels,44100,listOf(actualConfig),declaredChannels)
    }
    @Test fun centerOnlySixAndEightChannelsReachBothStereoSpeakers() {
        for(channels in listOf(3,5,6,7,8)) {
            val processor=io.github.xudong7587.sunnytv.feature.player.AudioOutputMixing.processor(2)
            val format=processor.configure(androidx.media3.common.audio.AudioProcessor.AudioFormat(48000,channels,androidx.media3.common.C.ENCODING_PCM_16BIT))
            assertEquals(2,format.channelCount)
            processor.flush()
            val input=java.nio.ByteBuffer.allocateDirect(channels*2).order(java.nio.ByteOrder.nativeOrder())
            repeat(channels) {input.putShort(if(it==2) 12000.toShort() else 0.toShort())};input.flip()
            processor.queueInput(input)
            val output=processor.output.order(java.nio.ByteOrder.nativeOrder())
            assertEquals(4,output.remaining())
            assertTrue("center lost from left",output.short>0)
            assertTrue("center lost from right",output.short>0)
            processor.reset()
        }
    }
    @Test fun fivePointOnePreservesCenterAndStereoIsNotUpmixed() {
        val matrix=io.github.xudong7587.sunnytv.feature.player.AudioOutputMixing.matrix(8,6)
        assertTrue(matrix.getMixingCoefficient(2,2)>0)
        val stereo=io.github.xudong7587.sunnytv.feature.player.AudioOutputMixing.matrix(2,6)
        assertEquals(2,stereo.outputChannelCount)
        assertTrue(stereo.isIdentity)
    }
    private fun decode(asset:String,mime:String,channels:Int) {
        val packet=InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use {it.readBytes()}
        decodePacket(packet,mime,channels,48000)
    }
    private fun decodePacket(packet:ByteArray,mime:String,channels:Int,rate:Int,initialization:List<ByteArray> = emptyList(),declaredChannels:Int=channels) {
        val decoder=FfmpegAudioDecoder(Format.Builder().setSampleMimeType(mime).setChannelCount(declaredChannels)
            .setSampleRate(rate).setInitializationData(initialization).build(),2,2,packet.size,false)
        try {
            val input=checkNotNull(decoder.dequeueInputBuffer())
            input.ensureSpaceForWrite(packet.size);input.data!!.put(packet);input.flip()
            decoder.queueInputBuffer(input)
            val deadline=System.nanoTime()+5_000_000_000L
            var output=decoder.dequeueOutputBuffer()
            while(output==null && System.nanoTime()<deadline) {Thread.sleep(10);output=decoder.dequeueOutputBuffer()}
            assertNotNull("No PCM output",output)
            val buffer=output!!
            try {
                val pcm=checkNotNull(buffer.data)
                assertTrue("Empty PCM",pcm.remaining()>0)
                var nonzero=false
                while(pcm.hasRemaining()) if(pcm.get().toInt()!=0) nonzero=true
                assertTrue("Silent PCM",nonzero)
                assertEquals(channels,decoder.channelCount)
                assertEquals(rate,decoder.sampleRate)
            } finally {buffer.release()}
        } finally {decoder.release()}
    }
}
