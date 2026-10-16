package io.github.xudong7587.sunnytv.feature.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaChunkExtractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.text.SubtitleParser
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.util.concurrent.ConcurrentHashMap

/** Preserve in-band AAC layout before SampleQueue can discard the priming frame. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PceHlsExtractorFactory(
    private val missingConfig:()->Unit,
    private val completeConfig:()->Unit
):HlsExtractorFactory {
    private val delegate=DefaultHlsExtractorFactory()
    private data class Key(val id:Int,val language:String?,val label:String?,val formatId:String?,val config:List<Byte>)
    private val configs=ConcurrentHashMap<Key,AacPceConfig.Config>()

    override fun createExtractor(uri:Uri,format:Format,muxedCaptionFormats:List<Format>?,timestampAdjuster:TimestampAdjuster,
        responseHeaders:Map<String,List<String>>,sniffingExtractorInput:ExtractorInput,playerId:PlayerId):HlsMediaChunkExtractor =
        Wrapped(delegate.createExtractor(uri,format,muxedCaptionFormats,timestampAdjuster,responseHeaders,sniffingExtractorInput,playerId))
    override fun setSubtitleParserFactory(factory:SubtitleParser.Factory):HlsExtractorFactory {delegate.setSubtitleParserFactory(factory);return this}
    override fun experimentalParseSubtitlesDuringExtraction(value:Boolean):HlsExtractorFactory {delegate.experimentalParseSubtitlesDuringExtraction(value);return this}
    override fun experimentalSetCodecsToParseWithinGopSampleDependencies(value:Int):HlsExtractorFactory {delegate.experimentalSetCodecsToParseWithinGopSampleDependencies(value);return this}
    override fun getOutputTextFormat(format:Format):Format = delegate.getOutputTextFormat(format)

    private inner class Wrapped(private val extractor:HlsMediaChunkExtractor):HlsMediaChunkExtractor by extractor {
        override fun init(output:ExtractorOutput) {
            extractor.init(object:ExtractorOutput by output {
                override fun track(id:Int,type:Int):TrackOutput {
                    val track=output.track(id,type)
                    return if(type==C.TRACK_TYPE_AUDIO) ConfigOutput(id,track) else track
                }
            })
        }
        override fun recreate():HlsMediaChunkExtractor = Wrapped(extractor.recreate())
    }

    private inner class ConfigOutput(private val id:Int,private val output:TrackOutput):TrackOutput by output {
        private var pending:Format?=null
        private var key:Key?=null
        private val packet=ByteArrayOutputStream()
        override fun format(format:Format) {
            val init=format.initializationData.singleOrNull()
            if(format.sampleMimeType==MimeTypes.AUDIO_AAC && format.drmInitData==null && init!=null && AacPceConfig.incomplete(init)) {
                val k=Key(id,format.language,format.label,format.id,init.toList())
                key=k
                val known=configs[k]
                if(known!=null) {pending=null;output.format(withConfig(format,known));completeConfig();return}
                pending=format
                output.format(format)
                missingConfig()
            } else {pending=null;key=null;output.format(format)}
        }
        private fun withConfig(format:Format,config:AacPceConfig.Config)=format.buildUpon()
            .setInitializationData(listOf(config.bytes)).setChannelCount(config.channels).build()
        private fun capture(bytes:ByteArray,offset:Int,length:Int,part:Int) {
            if(pending!=null && part==TrackOutput.SAMPLE_DATA_PART_MAIN && packet.size()+length<=8192) packet.write(bytes,offset,length)
        }
        override fun sampleData(data:ParsableByteArray,length:Int,sampleDataPart:Int) {
            capture(data.data,data.position,length,sampleDataPart)
            output.sampleData(data,length,sampleDataPart)
        }
        override fun sampleData(data:ParsableByteArray,length:Int)=sampleData(data,length,TrackOutput.SAMPLE_DATA_PART_MAIN)
        override fun sampleData(input:DataReader,length:Int,allowEndOfInput:Boolean):Int =
            sampleData(input,length,allowEndOfInput,TrackOutput.SAMPLE_DATA_PART_MAIN)
        override fun sampleData(input:DataReader,length:Int,allowEndOfInput:Boolean,sampleDataPart:Int):Int {
            if(pending==null) return output.sampleData(input,length,allowEndOfInput,sampleDataPart)
            val bytes=ByteArray(minOf(length,8192))
            val count=input.read(bytes,0,bytes.size)
            if(count<0) {if(allowEndOfInput) return -1;throw EOFException()}
            capture(bytes,0,count,sampleDataPart)
            output.sampleData(ParsableByteArray(bytes),count,sampleDataPart)
            return count
        }
        override fun sampleMetadata(timeUs:Long,flags:Int,size:Int,offset:Int,cryptoData:TrackOutput.CryptoData?) {
            val format=pending
            if(format!=null && cryptoData==null) {
                val bytes=packet.toByteArray()
                val start=bytes.size-offset-size
                if(start>=0 && size>0) {
                    val config=AacPceConfig.fromFrame(format.initializationData[0],bytes.copyOfRange(start,start+size))
                    if(config!=null) {
                        key?.let {if(configs.size<16) configs[it]=config}
                        pending=null
                        output.format(withConfig(format,config))
                        completeConfig()
                    }
                }
            }
            packet.reset()
            output.sampleMetadata(timeUs,flags,size,offset,cryptoData)
        }
    }
}
