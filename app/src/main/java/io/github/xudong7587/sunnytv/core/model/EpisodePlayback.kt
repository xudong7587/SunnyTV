package io.github.xudong7587.sunnytv.core.model

object EpisodePlayback {
    fun choose(entries:List<MediaEntry>,fromStart:Boolean):MediaEntry? {
        val ordered=entries.filter {it.isPlayable}.sortedWith(compareBy({it.season},{it.episode}))
        if(fromStart) return ordered.firstOrNull()
        val unfinished=ordered.filter {it.positionMs>0 && !it.played}
        if(unfinished.isNotEmpty()) return unfinished.maxByOrNull {it.lastPlayedAtMs}
        val last=ordered.filter {it.played}.maxWithOrNull(compareBy({it.lastPlayedAtMs},{it.season},{it.episode}))
        return last?.let {completed->ordered.drop(ordered.indexOf(completed)+1).firstOrNull {!it.played}}
            ?: ordered.firstOrNull {!it.played} ?: ordered.firstOrNull()
    }
}
