package io.github.xudong7587.sunnytv.core.model

/**
 * Font choices for the interface and for subtitles. Pure data: loading a [android.graphics.Typeface]
 * lives in `core/storage/FontTypefaces.kt` so the model stays free of Android types.
 *
 * Only two choices ship here: the platform font and a font the user imports from their own storage.
 * No font file is bundled with the app, so this repository carries no third-party font licence.
 * A stored choice that no longer exists (for example from a private build that bundled extra fonts)
 * is normalised back to [SYSTEM] by `core/storage/ConfigStore.kt`, and a missing asset also falls
 * back to the platform font in `loadFontTypeface`, so stored settings can never break startup.
 */
object FontCatalog {
    const val SYSTEM = "system"
    const val CUSTOM = "custom"

    data class Entry(val id:String, val label:String, val asset:String?)

    val entries = listOf(
        Entry(SYSTEM, "系统默认字体", null),
        Entry(CUSTOM, "用户自定义上传…", null))

    fun entry(id:String):Entry? = entries.firstOrNull {it.id == id}
    fun label(id:String):String = entry(id)?.label ?: entries.first().label
}
