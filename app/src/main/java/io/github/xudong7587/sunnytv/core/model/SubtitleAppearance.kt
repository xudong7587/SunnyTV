package io.github.xudong7587.sunnytv.core.model

/**
 * Subtitle look for the player. Colours are intentionally not stored: the font colour and the
 * outline colour always follow the current theme, as requested for the self-use build.
 */
object SubtitleAppearance {
    const val EDGE_NONE = "none"
    const val EDGE_OUTLINE = "outline"
    const val EDGE_SHADOW = "shadow"
    const val POSITION_STANDARD = "standard"
    const val POSITION_LOW = "low"
    const val POSITION_MIDDLE = "middle"
    const val POSITION_HIGH = "high"
    const val POSITION_TOP = "top"
    const val BACKGROUND_NONE = "none"
    const val BACKGROUND_BLACK = "black"
    const val BACKGROUND_THEME = "theme"

    val scales = listOf(.75f, .9f, 1f, 1.15f, 1.3f)
    val scaleNames = listOf("很小", "小", "标准", "大", "很大")
    val edges = listOf(
        EDGE_NONE to "无描边", EDGE_OUTLINE to "描边", EDGE_SHADOW to "投影")
    val positions = listOf(
        POSITION_STANDARD to "标准（底部）", POSITION_LOW to "较低（贴近底边）",
        POSITION_MIDDLE to "屏幕中部", POSITION_HIGH to "偏上", POSITION_TOP to "顶部")
    val backgrounds = listOf(
        BACKGROUND_NONE to "无背景", BACKGROUND_BLACK to "半透明黑", BACKGROUND_THEME to "主题色半透明")

    fun scale(level:Int):Float = scales[level.coerceIn(0, scales.lastIndex)]
    fun edge(value:String):String = edges.firstOrNull {it.first == value}?.first ?: EDGE_OUTLINE
    fun position(value:String):String = positions.firstOrNull {it.first == value}?.first ?: POSITION_STANDARD
    fun background(value:String):String = backgrounds.firstOrNull {it.first == value}?.first ?: BACKGROUND_BLACK
    /** Distance the caption bottom keeps from the bottom edge of the video surface. */
    fun bottomPaddingFraction(position:String):Float = when(position) {
        POSITION_LOW -> .02f
        POSITION_MIDDLE -> .18f
        POSITION_HIGH -> .34f
        POSITION_TOP -> .5f
        else -> .08f
    }
}
