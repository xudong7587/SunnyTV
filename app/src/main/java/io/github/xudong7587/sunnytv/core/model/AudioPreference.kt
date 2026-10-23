package io.github.xudong7587.sunnytv.core.model

/** A media preference is a language, never a stream index shared between different films. */
object AudioPreference {
    val choices=listOf("default" to "跟随媒体默认", "cmn" to "优先国语", "yue" to "优先粤语",
        "en" to "优先英语", "ja" to "优先日语", "ko" to "优先韩语", "fr" to "优先法语",
        "de" to "优先德语", "es" to "优先西班牙语")
    fun normalize(value:String)=value.takeIf {id->choices.any {it.first==id}} ?: "default"
    fun forMedia(preferences:Map<String,String>,item:MediaEntry,scope:String=""):String = normalize(
        item.takeIf {it.type in setOf("Series","Season")}?.let {preferences[it.key]} ?: preferences[scope] ?:
        item.seriesId.takeIf {it.isNotBlank()}?.let {preferences["${item.sourceId}:$it"]} ?: "default")
    fun matches(preference:String,language:String,title:String=""):Boolean {
        val l=language.lowercase().replace('_','-');val base=l.substringBefore('-');val t=title.lowercase()
        val cantonese=base in setOf("yue","zht") || l in setOf("zh-hk","zh-yue") ||
            t.contains("粤") || t.contains("粵") || t.contains("cantonese")
        val preferred=when(preference.lowercase().replace('_','-').substringBefore('-')) {
            "zh","zho","chi","cmn"->"cmn";"yue"->"yue";"en","eng"->"en";"ja","jpn"->"ja"
            "ko","kor"->"ko";"fr","fra","fre"->"fr";"de","deu","ger"->"de";"es","spa"->"es";else->preference
        }
        return when(preferred) {
            "cmn"->!cantonese && (base in setOf("zh","zho","chi","cmn") ||
                t.contains("国语") || t.contains("國語") || t.contains("普通话") || t.contains("mandarin"))
            "yue"->cantonese
            "en"->base in setOf("en","eng")
            "ja"->base in setOf("ja","jpn")
            "ko"->base in setOf("ko","kor")
            "fr"->base in setOf("fr","fra","fre")
            "de"->base in setOf("de","deu","ger")
            "es"->base in setOf("es","spa")
            else->false
        }
    }
    fun choose(tracks:List<MediaTrack>,preference:String):MediaTrack? = tracks.firstOrNull {
        it.type.equals("Audio",true) && matches(preference,it.language,it.title)
    }
    /** Apply only a preference here; explicit per-film selections have already taken priority. */
    fun apply(request:PlaybackRequest,preference:String):PlaybackRequest {
        val id=normalize(preference)
        val track=choose(request.sourceTracks,id)
        return request.copy(audioPreference=id,
            audioLanguage=track?.language?.ifBlank {id}.orEmpty().ifBlank {if(id=="default") "" else id},
            audioTitle=track?.title.orEmpty(),audioStreamIndex=track?.index ?: -1,
            audioOrdinal=if(track==null) -1 else request.sourceTracks.filter {it.type.equals("Audio",true)}.indexOf(track))
    }
}
