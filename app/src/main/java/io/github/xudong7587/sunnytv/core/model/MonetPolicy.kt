package io.github.xudong7587.sunnytv.core.model

object MonetPolicy {
    const val FALLBACK_SEED=0xFF6750A4L
    val sources=listOf("system" to "系统动态色", "palette" to "当前主题色")
    val styles=listOf("tonal_spot" to "柔和 · Tonal Spot", "neutral" to "中性 · Neutral",
        "vibrant" to "鲜明 · Vibrant", "expressive" to "表现 · Expressive",
        "monochrome" to "黑白 · Monochrome", "fidelity" to "忠实 · Fidelity",
        "content" to "原色 · Content", "rainbow" to "彩虹 · Rainbow", "fruit_salad" to "果彩 · Fruit Salad")
    fun source(id:String)=sources.firstOrNull {it.first==id}?.first ?: "system"
    fun style(id:String)=styles.firstOrNull {it.first==id}?.first ?: "tonal_spot"
}
