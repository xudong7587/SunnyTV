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
        listOf(MimeTypes.AUDIO_AC3,MimeTypes.AUDIO_E_AC3,MimeTypes.AUDIO_E_AC3_JOC,
            MimeTypes.AUDIO_DTS,MimeTypes.AUDIO_DTS_HD,MimeTypes.AUDIO_TRUEHD).forEach {
            assertTrue("Missing $it",FfmpegLibrary.supportsFormat(it))
        }
    }
    @Test fun eac3SixChannelsDecodeToNonSilentPcm() = decode("tone.eac3",MimeTypes.AUDIO_E_AC3,6)
    @Test fun dtsSixChannelsDecodeToNonSilentPcm() = decode("tone.dts",MimeTypes.AUDIO_DTS,6)
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
        val decoder=FfmpegAudioDecoder(Format.Builder().setSampleMimeType(mime).setChannelCount(channels)
            .setSampleRate(48000).build(),2,2,packet.size,false)
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
                assertEquals(48000,decoder.sampleRate)
            } finally {buffer.release()}
        } finally {decoder.release()}
    }
}
