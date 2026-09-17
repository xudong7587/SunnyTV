package io.github.xudong7587.sunnytv.core.model

/** Exact media choices never fall back to a different same-language file. Preferences can fall back to defaults. */
object SubtitleSelection {
    data class Option(val id:String,val title:String,val language:String,val supported:Boolean=true) {
        val external get()=id.startsWith("emby-sub:")
    }
    fun choose(options:List<Option>,preference:String,explicit:Boolean=false,id:String="",title:String="",ordinal:Int=-1):Int? {
        if(explicit) {
            val exact=if(id.isNotBlank()) options.indexOfFirst {it.id==id} else {
                val names=options.indices.filter {title.isNotBlank() && options[it].title.equals(title,true)}
                names.singleOrNull() ?: options.indices.filter {!options[it].external}.getOrNull(ordinal) ?: -1
            }
            return exact.takeIf {it>=0 && options[it].supported}
        }
        if(preference in setOf("default","none")) return null
        val preferred=options.indexOfFirst {it.supported && Presentation.languageMatches(preference,it.language,it.title)}
        if(preferred>=0) return preferred
        return if(preference=="zh-Hans") options.indexOfFirst {it.supported && Presentation.languageMatches("zh",it.language,it.title)}.takeIf {it>=0} else null
    }
}
