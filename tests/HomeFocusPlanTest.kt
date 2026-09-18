package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.HomeFocusPlan as Plan
import org.junit.Assert.*
import org.junit.Test

class HomeFocusPlanTest {
    private val libraries = listOf("a:lib0", "a:lib1", "b:lib0", "b:lib1")
    @Test fun lazyIndicesMatchOnlyActuallyRenderedRegions() {
        assertEquals(listOf(0,1,2,3,4), Plan.sections(libraries,false).map {it.lazyIndex})
        assertEquals(listOf(0,1,2,3,4,5), Plan.sections(libraries,true).map {it.lazyIndex})
        assertEquals(listOf(0,1), Plan.sections(listOf("a:lib0","a:lib0"),false).map {it.lazyIndex})
    }
    @Test fun everySelectedLibraryContinuesToTheNextVisibleRegion() {
        val sections=Plan.sections(libraries,false)
        for (key in libraries) assertEquals(Plan.latest(libraries.first()), Plan.down(sections,Plan.LIBRARIES,"library:$key"))
    }
    @Test fun nextUpRemainsReachableAndContinuesInPageOrder() {
        val sections=Plan.sections(libraries,true)
        assertEquals(Plan.NEXT_UP,Plan.down(sections,Plan.LIBRARIES,"library:b:lib0"))
        assertEquals(Plan.latest(libraries.first()),Plan.down(sections,Plan.NEXT_UP,"library:b:lib0"))
        assertEquals(Plan.LIBRARIES,Plan.up(sections,Plan.NEXT_UP))
    }
    @Test fun continuousTraversalReachesEveryLazyRowAndDoesNotWrapAtEnd() {
        val sections=Plan.sections(libraries,false)
        for (i in 1 until sections.lastIndex) assertEquals(sections[i+1].key,Plan.down(sections,sections[i].key,"poster"))
        assertNull(Plan.down(sections,sections.last().key,"poster"))
        assertEquals(Plan.HERO,Plan.up(sections,Plan.LIBRARIES))
    }
    @Test fun changedAndEmptyFeedsNeverInventAnUnavailableDestination() {
        assertNull(Plan.down(Plan.sections(emptyList(),false),Plan.LIBRARIES,"library:missing"))
        assertNull(Plan.down(emptyList(),"missing",null))
        assertNull(Plan.up(emptyList(),"missing"))
        assertEquals(Plan.latest(libraries.first()),Plan.down(Plan.sections(libraries,false),Plan.LIBRARIES,"library:removed"))
    }
}
