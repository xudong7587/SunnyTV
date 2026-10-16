package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.SubtitleSelection
import org.junit.Assert.*
import org.junit.Test

class SubtitleSelectionTest {
    @Test fun embeddedIdentitySurvivesNestedCloudAndSidecarMerges() {
        val tracks=listOf(SubtitleSelection.Option("0:1:7","内嵌","zh"),
            SubtitleSelection.Option("1:emby-sub:4","外挂","zh"))
        assertEquals(0,SubtitleSelection.choose(tracks,"zh",true,id="0:7"))
        assertEquals(0,SubtitleSelection.choose(listOf(SubtitleSelection.Option("7","内嵌","zh")),"zh",true,id="0:1:7"))
        assertNull(SubtitleSelection.choose(tracks,"zh",true,id="8"))
    }

    @Test fun mergedSourcesKeepExternalIdentityAcrossSourceIndexChanges() {
        val merged=listOf(SubtitleSelection.Option("0:7","内嵌","zh"),SubtitleSelection.Option("1:emby-sub:4","外挂","zh"))
        assertEquals(1,SubtitleSelection.choose(merged,"zh",true,id="emby-sub:4"))
        assertEquals(1,SubtitleSelection.choose(merged,"zh",true,id="2:emby-sub:4"))
        assertNull(SubtitleSelection.choose(merged,"zh",true,ordinal=1))
        assertNull(SubtitleSelection.choose(merged,"zh",true,id="emby-sub:5"))
    }
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

