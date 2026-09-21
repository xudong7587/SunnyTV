package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.DisplayModeCandidate
import io.github.xudong7587.sunnytv.core.model.DisplayModePolicy
import org.junit.Assert.*
import org.junit.Test

class DisplayModePolicyTest {
    private val modes=listOf(
        DisplayModeCandidate(1,1920,1080,60f),
        DisplayModeCandidate(2,1920,1080,120f),
        DisplayModeCandidate(3,3840,2160,30f),
        DisplayModeCandidate(4,3840,2160,60f)
    )
    @Test fun autoClearsWindowPreference() {assertNull(DisplayModePolicy.select("auto",modes))}
    @Test fun fhdPrefersFastestExact1080Mode() {assertEquals(2,DisplayModePolicy.select("1080p",modes)?.id)}
    @Test fun nativePrefersLargestThenFastestMode() {assertEquals(4,DisplayModePolicy.select("native",modes)?.id)}
    @Test fun invalidPreferenceFallsBackToAuto() {assertNull(DisplayModePolicy.select("mystery",modes))}
}
