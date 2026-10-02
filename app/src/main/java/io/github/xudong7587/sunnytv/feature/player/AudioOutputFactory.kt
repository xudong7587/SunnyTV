package io.github.xudong7587.sunnytv.feature.player

import android.content.Context
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/** PCM channel layout: L, R, C, LFE, BL, BR, SL, SR (7 channels use BC, SL, SR). */
@androidx.annotation.OptIn(UnstableApi::class)
object AudioOutputMixing {
    fun matrix(input:Int,maximum:Int):ChannelMixingMatrix {
        require(input in 1..8 && maximum in setOf(2,6))
        if(input<=maximum) return ChannelMixingMatrix(input,input,FloatArray(input*input) {if(it/input==it%input) 1f else 0f})
        if(maximum==2 && input<=6) return ChannelMixingMatrix.createForConstantPower(input,2).scale(.4f)
        val coefficients=FloatArray(input*maximum)
        fun add(channel:Int,output:Int,gain:Float) {coefficients[channel*maximum+output]=gain}
        add(0,0,1f);add(1,1,1f)
        if(maximum==2) {
            add(2,0,.7071f);add(2,1,.7071f)
            add(3,0,.25f);add(3,1,.25f)
            if(input==7) {add(4,0,.5f);add(4,1,.5f);add(5,0,.7071f);add(6,1,.7071f)}
            else {add(4,0,.7071f);add(5,1,.7071f);add(6,0,.7071f);add(7,1,.7071f)}
            return ChannelMixingMatrix(input,2,coefficients).scale(.29f)
        }
        add(2,2,1f);add(3,3,1f)
        if(input==7) {add(4,4,.5f);add(4,5,.5f);add(5,4,.7071f);add(6,5,.7071f)}
        else {add(4,4,.7071f);add(5,5,.7071f);add(6,4,.7071f);add(7,5,.7071f)}
        return ChannelMixingMatrix(input,6,coefficients).scale(.7071f)
    }
    fun processor(maximum:Int)=ChannelMixingAudioProcessor().apply {
        for(channels in 1..8) putChannelMixingMatrix(matrix(channels,maximum))
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
class AudioOutputFactory(context:Context,private val maximum:Int):DefaultRenderersFactory(context) {
    override fun buildAudioSink(context:Context,enableFloatOutput:Boolean,enableAudioOutputPlaybackParams:Boolean):AudioSink {
        if(maximum==0) return checkNotNull(super.buildAudioSink(context,enableFloatOutput,enableAudioOutputPlaybackParams))
        return DefaultAudioSink.Builder(context).setEnableFloatOutput(false)
            .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
            .setAudioProcessors(arrayOf(AudioOutputMixing.processor(maximum))).build()
    }
}
