package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.feature.player.PlayerGesturePolicy
import io.github.xudong7587.sunnytv.core.model.MediaEntry
import io.github.xudong7587.sunnytv.core.model.Presentation
import org.junit.Assert.*
import org.junit.Test

class PlayerGestureTest {
    @Test fun touchRegionsIncludeBothScreenEdges() {
        assertEquals(0, PlayerGesturePolicy.region(0f, 900))
        assertEquals(1, PlayerGesturePolicy.region(450f, 900))
        assertEquals(2, PlayerGesturePolicy.region(900f, 900))
    }
    @Test fun seekCannotLeaveMediaBounds() {
        assertEquals(0L, PlayerGesturePolicy.seekTarget(10_000, -1f, 90_000))
        assertEquals(90_000L, PlayerGesturePolicy.seekTarget(10_000, 1f, 90_000))
        assertEquals(40_000L, PlayerGesturePolicy.seekTarget(10_000, .25f, 90_000))
        assertEquals(0L, PlayerGesturePolicy.seekTarget(10_000, .25f, -1))
    }
    @Test fun untypedMixedLibraryOffersFoldersButMovieLibraryDoesNot() {
        val library=MediaEntry("library", "test", "混合库", "CollectionFolder", isFolder=true)
        assertTrue(Presentation.supportsFolders(library))
        assertTrue(Presentation.supportsFolders(library.copy(collectionType="mixed")))
        assertFalse(Presentation.supportsFolders(library.copy(collectionType="movies")))
    }
}
