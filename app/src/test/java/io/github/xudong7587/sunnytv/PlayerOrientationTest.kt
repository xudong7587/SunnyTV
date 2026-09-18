package io.github.xudong7587.sunnytv

import android.content.pm.ActivityInfo
import io.github.xudong7587.sunnytv.feature.player.playerOrientation
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerOrientationTest {
    @Test fun tvKeepsPortraitVideosInLandscape() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,playerOrientation(true,1080,1920))
    }
    @Test fun phoneUsesPortraitForVerticalVideoWithoutForcingWideVideosLandscape() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,playerOrientation(false))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,playerOrientation(false,1080,1920))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,playerOrientation(false,1920,1080))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,playerOrientation(false,1080,1920,2f))
    }
}
