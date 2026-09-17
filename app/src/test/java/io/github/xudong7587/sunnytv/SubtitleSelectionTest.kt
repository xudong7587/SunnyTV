package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.SubtitleSelection
import org.junit.Assert.*
import org.junit.Test

class SubtitleSelectionTest {
    private val options=listOf(
        SubtitleSelection.Option("0","English","en"),
        SubtitleSelection.Option("emby-sub:4","film.zh-Hans.srt","zh-Hans"),
        SubtitleSelection.Option("emby-sub:5","film.zh-commentary.srt","zh"))
    @Test fun preferenceMatchesRealTracksAndMissingLanguageKeepsDefault() {
        assertEquals(1,SubtitleSelection.choose(options,"zh-Hans"))
        assertNull(SubtitleSelection.choose(options,"fr"))
        assertNull(SubtitleSelection.choose(options,"none"))
        assertNull(SubtitleSelection.choose(options,"default"))
    }
    @Test fun sameLanguageExternalFilesKeepExactIdentity() {
        assertEquals(2,SubtitleSelection.choose(options,"zh-Hans",true,id="emby-sub:5"))
        assertNull(SubtitleSelection.choose(options,"zh",true,id="emby-sub:99"))
    }
    @Test fun explicitTrackOverridesDisabledPreferenceAndUnsupportedIsNotReplaced() {
        assertEquals(1,SubtitleSelection.choose(options,"none",true,id="emby-sub:4"))
        val unsupported=options.map {if(it.id=="emby-sub:4") it.copy(supported=false) else it}
        assertNull(SubtitleSelection.choose(unsupported,"zh",true,id="emby-sub:4"))
    }
    @Test fun embeddedOrdinalDoesNotSelectExternalFileWithSameLanguage() {
        assertEquals(0,SubtitleSelection.choose(options,"zh",true,ordinal=0))
        assertNull(SubtitleSelection.choose(options,"zh",true,ordinal=1))
    }
}
