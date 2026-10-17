package io.github.xudong7587.sunnytv.core.model

/** Stable display ordering only; never changes the selected subtitle. */
object SubtitleLanguageOrder {
    fun priority(language:String,label:String):Int {
        val code=language.lowercase(java.util.Locale.ROOT).replace('_','-')
        val name=label.lowercase(java.util.Locale.ROOT)
        return when {
            code in setOf("zh-hans","zh-cn","zh-sg","chs") || name.contains("简体") || name.contains("簡體") || name.contains("simplified") -> 0
            code in setOf("zh-hant","zh-tw","zh-hk","zh-mo","cht") || name.contains("繁体") || name.contains("繁體") || name.contains("traditional") -> 2
            code in setOf("zh","zho","chi","cmn","yue") || code.startsWith("zh-") || name.contains("中文") || name.contains("chinese") -> 1
            else -> 3
        }
    }
}
