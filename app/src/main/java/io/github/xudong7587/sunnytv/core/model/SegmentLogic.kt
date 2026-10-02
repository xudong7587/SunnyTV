package io.github.xudong7587.sunnytv.core.model

object SegmentLogic {
    private fun normalized(name:String)=name.trim().lowercase()
    private fun namedType(name:String):String? {
        val n=normalized(name)
        return when {
            listOf("title sequence","intro","opening","片头","开场","op ").any {n.contains(it)} -> "intro"
            n in setOf("credits","end credits","credit sequence","outro","ending","片尾","演职员","ed") -> "outro"
            else -> null
        }
    }
    fun segments(chapters:List<MediaChapter>,durationMs:Long):List<SkipSegment> {
        if(chapters.isEmpty()) return emptyList()
        val sorted=chapters.filter {it.startMs>=0}.sortedBy {it.startMs}
        val result=mutableListOf<SkipSegment>()
        val introStart=sorted.firstOrNull {it.markerType.equals("IntroStart",true)}
        val introEnd=introStart?.let {start->sorted.firstOrNull {it.startMs>start.startMs && it.markerType.equals("IntroEnd",true)}}
        if(introStart!=null && introEnd!=null && introEnd.startMs-introStart.startMs>=1_000) {
            result+=SkipSegment("intro:" + introStart.startMs,"intro",introStart.startMs,introEnd.startMs)
        }
        val credits=sorted.firstOrNull {it.markerType.equals("CreditsStart",true)}
        if(credits!=null && durationMs>credits.startMs+1_000 && credits.startMs>=durationMs*4/5) {
            result+=SkipSegment("outro:" + credits.startMs,"outro",credits.startMs,durationMs)
        }
        sorted.forEachIndexed {index,chapter->
            val type=namedType(chapter.name) ?: return@forEachIndexed
            // Chapter names are a weak fallback, never an early-film credits cue.
            if(type=="outro" && (durationMs<=0 || chapter.startMs<durationMs*4/5 ||
                durationMs-chapter.startMs>300_000)) return@forEachIndexed
            if(result.any {it.type==type}) return@forEachIndexed
            val end=sorted.getOrNull(index+1)?.startMs ?: durationMs
            if(end>chapter.startMs+1_000) result+=SkipSegment(type + ":" + chapter.startMs,type,chapter.startMs,end)
        }
        return result.distinctBy {it.id}.sortedBy {it.startMs}
    }
    fun active(segments:List<SkipSegment>,positionMs:Long,dismissed:Set<String>):SkipSegment? =
        segments.firstOrNull {it.id !in dismissed && positionMs>=it.startMs && positionMs<it.endMs-300}
}
