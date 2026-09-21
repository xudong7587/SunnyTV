package io.github.xudong7587.sunnytv.core.storage

import android.content.Context
import android.graphics.Typeface
import io.github.xudong7587.sunnytv.core.model.FontCatalog
import java.io.File

/**
 * Loads the [Typeface] for a [FontCatalog] entry. Returns null for the system font, an unusable
 * custom file, or a missing asset, so callers always fall back to the platform font.
 */
fun loadFontTypeface(context:Context, id:String, customFile:String):Typeface? {
    val asset = FontCatalog.entry(id)?.asset
    return runCatching {
        when {
            asset != null -> Typeface.createFromAsset(context.assets, asset)
            id == FontCatalog.CUSTOM -> customFile.takeIf {it.isNotBlank()}?.let {name->
                val folder = File(context.filesDir, "fonts").canonicalFile
                val file = File(folder, name).canonicalFile
                if(file.parentFile != folder || !file.isFile) null else Typeface.createFromFile(file)
            }
            else -> null
        }
    }.getOrNull()
}
