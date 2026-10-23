package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.*
import org.junit.Assert.*
import org.junit.Test

class AudioPreferenceTest {
    private fun audio(index:Int,language:String,title:String="")=MediaTrack(index,"Audio",language,title,"aac")
    @Test fun preferencesBelongToEachMediaAndEpisodesCanInheritSeriesWithoutLeaking() {
        val episode=MediaEntry("ep1","s","one","Episode",seriesId="series")
        val map=mapOf("s:series" to "ja","s:ep1" to "en","s:season" to "yue","s:library" to "fr")
        // Old per-episode/movie defaults are ignored; those pages choose an actual track directly.
        assertEquals("ja",AudioPreference.forMedia(map,episode))
        assertEquals("ja",AudioPreference.forMedia(map,episode.copy(id="series",type="Series")))
        assertEquals("ja",AudioPreference.forMedia(map,episode.copy(id="ep2")))
        assertEquals("yue",AudioPreference.forMedia(map,episode.copy(id="ep2"),"s:season"))
        assertEquals("default",AudioPreference.forMedia(map,episode.copy(id="film",type="Movie",seriesId="")))
        assertEquals("default",AudioPreference.forMedia(map,episode.copy(id="ep2",sourceId="other")))
    }
    @Test fun mandarinAndCantoneseAreDistinctDespiteSharedChineseTags() {
        val tracks=listOf(audio(1,"chi","粤语"),audio(3,"zho","国语"),audio(6,"eng"))
        assertEquals(3,AudioPreference.choose(tracks,"cmn")?.index)
        assertEquals(1,AudioPreference.choose(tracks,"yue")?.index)
        assertTrue(AudioPreference.matches("yue","zh-HK"))
        assertFalse(AudioPreference.matches("cmn","zh-HK"))
        assertTrue(AudioPreference.matches("jpn","ja-JP"))
        assertTrue(AudioPreference.matches("en","eng"))
    }
    @Test fun everyEpisodeResolvesItsOwnStreamIndexAndOrdinal() {
        val one=PlaybackRequest("s","https://example.invalid/video","one",sourceTracks=listOf(audio(2,"eng"),audio(8,"jpn")))
        val two=one.copy(title="two",sourceTracks=listOf(audio(13,"ja"),audio(21,"en")))
        val a=AudioPreference.apply(one,"ja");val b=AudioPreference.apply(two,a.audioPreference)
        assertEquals(8,a.audioStreamIndex);assertEquals(1,a.audioOrdinal)
        assertEquals(13,b.audioStreamIndex);assertEquals(0,b.audioOrdinal)
        assertEquals("ja",b.audioPreference)
    }
    @Test fun missingLanguageLeavesDefaultSelectionAndCanResolveLaterFromContainer() {
        val original=PlaybackRequest("s","https://example.invalid/video","one",sourceTracks=listOf(audio(4,"eng")))
        val missing=AudioPreference.apply(original,"ja")
        assertEquals(-1,missing.audioStreamIndex);assertEquals(-1,missing.audioOrdinal)
        assertEquals("ja",missing.audioLanguage)
        assertNull(AudioPreference.choose(original.sourceTracks,"ja"))
        val defaults=AudioPreference.apply(original,"default")
        assertEquals("",defaults.audioLanguage);assertEquals(-1,defaults.audioOrdinal)
        assertEquals("default",AudioPreference.normalize("bad-value"))
    }
}
