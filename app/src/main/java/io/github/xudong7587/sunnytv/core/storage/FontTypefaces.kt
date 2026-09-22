package io.github.xudong7587.sunnytv.core.storage

import android.content.Context
import android.graphics.Typeface
import io.github.xudong7587.sunnytv.core.model.FontCatalog

/**
 * Loads the [Typeface] for a [FontCatalog] entry: a bundled asset, an imported document, or any font
 * file in the app's font folder. Returns null for the system font, an unusable file, or a missing
 * asset, so callers always fall back to the platform font.
 */
fun loadFontTypeface(context:Context, id:String, customFile:String):Typeface? {
    val asset=FontCatalog.entry(id)?.asset
    return runCatching {
        when {
            asset != null -> Typeface.createFromAsset(context.assets,asset)
            id.startsWith(FontLibrary.FILE_PREFIX) -> FontLibrary.file(context,id)?.let {Typeface.createFromFile(it)}
            id == FontCatalog.CUSTOM -> customFile.takeIf {it.isNotBlank()}?.let {FontLibrary.file(context,FontLibrary.FILE_PREFIX+it)}
                ?.let {Typeface.createFromFile(it)}
            else -> null
        }
    }.getOrNull()
}
