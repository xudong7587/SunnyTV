package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.feature.update.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AutomaticGithubUpdateTest {
    @Test fun disabledDoesNoNetworkAndEnabledOnlyDownloadsNewer()=runBlocking {
        var checks=0;var downloads=0
        var code=59L
        val latest:suspend ()->GithubUpdates.Release={checks++;GithubUpdates.Release("0.2.7",code,"","")}
        val download:suspend (GithubUpdates.Release)->File={downloads++;File("verified.apk")}
        assertNull(prepareAutomaticGithubUpdate(false,59,latest,download))
        assertEquals(0,checks)
        assertNull(prepareAutomaticGithubUpdate(true,59,latest,download))
        code=58;assertNull(prepareAutomaticGithubUpdate(true,59,latest,download))
        assertEquals(0,downloads)
        code=60;assertEquals(File("verified.apk"),prepareAutomaticGithubUpdate(true,59,latest,download))
        assertEquals(1,downloads)
    }
}
