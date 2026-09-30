package io.github.xudong7587.sunnytv

import android.content.pm.ActivityInfo
import io.github.xudong7587.sunnytv.feature.player.playerOrientation
import io.github.xudong7587.sunnytv.feature.player.fixedPlayerOrientation
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerOrientationTest {
    @Test fun fixedLockOrientationsCoverPhoneAndLandscapeNaturalTablet() {
        val phone=listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)
        val tablet=listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        repeat(8) {assertEquals(phone[it%4],fixedPlayerOrientation(it,false));assertEquals(tablet[it%4],fixedPlayerOrientation(it,true))}
    }

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
