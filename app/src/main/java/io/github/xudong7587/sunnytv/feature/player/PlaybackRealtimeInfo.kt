package io.github.xudong7587.sunnytv.feature.player

import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** Counts bytes actually received by this player's network sources, including HLS segments. */
internal class PlaybackRateMeter {
    private val bytes=AtomicLong()
    private var previousBytes=0L
    private var previousTime:Long?=null
    fun addBytes(count:Int) {if(count>0) bytes.addAndGet(count.toLong())}
    fun sample(nowMs:Long):Double {
        val total=bytes.get();val start=previousTime
        val result=if(start!=null && nowMs>start) (total-previousBytes).coerceAtLeast(0)*1000.0/(nowMs-start) else 0.0
        previousTime=nowMs;previousBytes=total
        return result
    }
}

internal data class PlaybackRealtimeInfo(
    val width:Int=0,val height:Int=0,val frameRate:Float=0f,val videoMime:String?=null,
    val videoBitrate:Long=0,val streamBitrate:Long=0,val audioMime:String?=null,
    val sampleRate:Int=0,val channels:Int=0,val audioBitrate:Long=0,
    val bytesPerSecond:Double=0.0,val bufferedMs:Long=0,
    val videoDecoder:String="",val audioDecoder:String=""
) {
    private fun number(value:Double)=String.format(Locale.ROOT,"%.2f",value)
    private fun rate(value:Long)=if(value>0) "${number(value/1_000_000.0)} Mbps" else "—"
    private fun codec(mime:String?)=when(mime) {
        "video/avc"->"H.264";"video/hevc"->"HEVC";"video/av01"->"AV1"
        "audio/mp4a-latm"->"AAC";"audio/eac3"->"E-AC3";"audio/ac3"->"AC3"
        else->mime?.substringAfter('/')?.uppercase(Locale.ROOT) ?: "—"
    }
    fun text():String=buildList {
        add("视频 ${if(width>0 && height>0) "${width}×${height}" else "—"} · ${codec(videoMime)}"+
            if(frameRate>0 && frameRate.isFinite()) " · ${number(frameRate.toDouble())} fps" else "")
        add(if(videoBitrate>0) "视频码率 ${rate(videoBitrate)}" else "流标称码率 ${rate(streamBitrate)}")
        add("音频 ${codec(audioMime)} · ${if(sampleRate>0) "${number(sampleRate/1000.0)} kHz" else "—"} · ${if(channels>0) "$channels 声道" else "—"}"+
            if(audioBitrate>0) " · ${audioBitrate/1000} kbps" else "")
        val speed=bytesPerSecond.takeIf {it.isFinite() && it>=0} ?: 0.0
        add("下载 ${number(speed/1_000_000)} MB/s · ${number(speed*8/1_000_000)} Mbps")
        add("缓冲 ${number(bufferedMs.coerceAtLeast(0)/1000.0)} s")
        if(videoDecoder.isNotBlank()) add("视频解码 $videoDecoder")
        if(audioDecoder.isNotBlank()) add("音频解码 $audioDecoder")
    }.joinToString("\n")
}
