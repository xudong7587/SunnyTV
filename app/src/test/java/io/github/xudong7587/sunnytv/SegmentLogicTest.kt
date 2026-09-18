package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.*
import org.junit.Assert.*
import org.junit.Test

class SegmentLogicTest {
    @Test fun markerTypesProduceIntroAndCreditsRanges() {
        val chapters=listOf(
            MediaChapter("marker",10_000,"IntroStart"),
            MediaChapter("marker",70_000,"IntroEnd"),
            MediaChapter("credits",3_000_000,"CreditsStart"))
        val segments=SegmentLogic.segments(chapters,3_180_000)
        assertEquals(listOf("intro","outro"),segments.map {it.type})
        assertEquals(70_000,segments[0].endMs)
        assertEquals(3_180_000,segments[1].endMs)
    }
    @Test fun namedPluginChapterFallsBackToNextChapterBoundary() {
        val chapters=listOf(MediaChapter("Title Sequence",5_000),MediaChapter("Chapter 2",55_000),MediaChapter("片尾",2_900_000))
        val segments=SegmentLogic.segments(chapters,3_000_000)
        assertEquals(55_000,segments.first {it.type=="intro"}.endMs)
        assertEquals(3_000_000,segments.first {it.type=="outro"}.endMs)
    }
    @Test fun activeHonoursDismissalAndEndBoundary() {
        val s=SkipSegment("intro:1","intro",1_000,10_000)
        assertEquals(s,SegmentLogic.active(listOf(s),5_000,emptySet()))
        assertNull(SegmentLogic.active(listOf(s),5_000,setOf(s.id)))
        assertNull(SegmentLogic.active(listOf(s),10_000,emptySet()))
    }
}
