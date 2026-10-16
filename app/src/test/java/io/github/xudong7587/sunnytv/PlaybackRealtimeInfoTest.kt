package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.feature.player.PlaybackRateMeter
import io.github.xudong7587.sunnytv.feature.player.PlaybackRealtimeInfo
import org.junit.Assert.*
import org.junit.Test

class PlaybackRealtimeInfoTest {
    @Test fun countsOnlyNewBytesAndReturnsZeroDuringIdle() {
        val meter=PlaybackRateMeter();assertEquals(0.0,meter.sample(1000),0.0)
        meter.addBytes(1_250_000);assertEquals(1_250_000.0,meter.sample(2000),0.0)
        assertEquals(0.0,meter.sample(3000),0.0)
        meter.addBytes(1_000_000);assertEquals(500_000.0,meter.sample(5000),0.0)
        assertEquals(0.0,PlaybackRateMeter().sample(6000),0.0)
    }
    @Test fun differentiatesBitrateFromDownloadSpeedAndHandlesUnknownFormats() {
        val info=PlaybackRealtimeInfo(width=3840,height=2160,frameRate=23.976f,videoMime="video/hevc",
            streamBitrate=20_000_000,audioMime="audio/mp4a-latm",sampleRate=44100,channels=8,
            bytesPerSecond=1_250_000.0,bufferedMs=12345,audioDecoder="ffmpegAAC").text()
        assertTrue(info.contains("3840×2160 · HEVC · 23.98 fps"))
        assertTrue(info.contains("流标称码率 20.00 Mbps"))
        assertTrue(info.contains("下载 1.25 MB/s · 10.00 Mbps"))
        assertTrue(info.contains("44.10 kHz · 8 声道"));assertTrue(info.contains("缓冲 12.35 s"))
        val empty=PlaybackRealtimeInfo(bytesPerSecond=Double.NaN,bufferedMs=-1).text()
        assertTrue(empty.contains("视频 —"));assertTrue(empty.contains("下载 0.00 MB/s"));assertFalse(empty.contains("NaN"))
    }
}
