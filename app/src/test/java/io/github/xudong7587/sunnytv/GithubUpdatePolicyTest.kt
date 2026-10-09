package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.feature.update.GithubUpdates
import org.junit.Assert.*
import org.junit.Test

class GithubUpdatePolicyTest {
    @Test fun releaseBrowserStaysOnOfficialRepository() {
        assertTrue(GithubUpdates.allowedPage(GithubUpdates.PAGE))
        assertTrue(GithubUpdates.allowedPage("https://github.com/xudong7587/SunnyTV/releases/tag/v0.1.0-dev34.4"))
        assertFalse(GithubUpdates.allowedPage("https://github.com/other/project/releases"))
        assertFalse(GithubUpdates.allowedPage("http://github.com/xudong7587/SunnyTV/releases"))
        assertFalse(GithubUpdates.allowedPage("https://github.com.evil.invalid/xudong7587/SunnyTV/releases"))
        assertFalse(GithubUpdates.allowedPage("https://user:secret@github.com/xudong7587/SunnyTV/releases"))
    }
}
