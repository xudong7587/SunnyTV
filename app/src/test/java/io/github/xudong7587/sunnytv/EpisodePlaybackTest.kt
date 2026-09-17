package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.*
import org.junit.Assert.*
import org.junit.Test

class EpisodePlaybackTest {
    private fun episode(id:String,season:Int,number:Int,position:Long=0,played:Boolean=false,last:Long=0)=
        MediaEntry(id,"s",id,"Episode",season=season,episode=number,positionMs=position,played=played,lastPlayedAtMs=last)
    @Test fun startUsesNumericSeasonAndEpisodeOrderRegardlessOfServerArrayOrder() {
        val entries=listOf(episode("s2e1",2,1),episode("s1e10",1,10),episode("s1e2",1,2))
        assertEquals("s1e2",EpisodePlayback.choose(entries,true)?.id)
        assertEquals("s1e2",EpisodePlayback.choose(entries,false)?.id)
        assertEquals("s2e1",EpisodePlayback.choose(entries.filter {it.season==2},true)?.id)
    }
    @Test fun resumeUsesMostRecentlyWatchedUnfinishedEpisodeAndPreservesItsPosition() {
        val entries=listOf(episode("old",1,2,position=90,last=10),episode("recent",2,3,position=200,last=100),episode("first",1,1))
        assertEquals("recent",EpisodePlayback.choose(entries,false)?.id)
        assertEquals(200L,EpisodePlayback.choose(entries,false)?.positionMs)
        assertEquals("first",EpisodePlayback.choose(entries,true)?.id)
    }
    @Test fun completedEpisodeContinuesWithTheNextUnplayedEpisodeAndEmptyListsAreExplicit() {
        val entries=listOf(episode("skipped",1,1),episode("watched",1,2,played=true,last=50),episode("next",1,3))
        assertEquals("next",EpisodePlayback.choose(entries,false)?.id)
        assertNull(EpisodePlayback.choose(emptyList(),false))
        assertNull(EpisodePlayback.choose(listOf(MediaEntry("folder","s","Folder","Season",isFolder=true)),true))
    }
}
