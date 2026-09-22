package io.github.xudong7587.sunnytv.core.storage

import android.content.Context
import io.github.xudong7587.sunnytv.core.model.FontCatalog
import java.io.File

/**
 * Every font the user can pick, as a file on the device rather than a file inside the APK.
 *
 * Two sources feed one list: the fonts a build bundles in its assets and the files sitting in the
 * app's own font folder (`files/fonts`, plus the matching folder on external storage). A bundled
 * font is copied into that folder once, on first launch, so replacing a private build with a public
 * one — which no longer bundles anything — keeps every font the user already had: the asset goes
 * away with the old APK, the copy stays. Nothing is read from the repository, so no third-party
 * font file is ever committed or published.
 */
object FontLibrary {
    const val FILE_PREFIX="file:"
    private const val FOLDER="fonts"
    private const val ASSET_FOLDER="fonts"
    private val extensions=setOf("ttf","otf","ttc")

    /** Readable names for the fonts the private builds carry; anything else keeps its file name. */
    private val knownLabels=mapOf(
        "coca-colacarefont-textlight" to "可口可乐字体 · 细体",
        "fzlthjw" to "方正兰亭黑简体",
        "fzltzchjw" to "方正兰亭中黑简体",
        "hyyuanlonghei60j" to "汉仪元隆黑 60 简")

    fun label(fileName:String):String {
        val base=fileName.substringBeforeLast('.')
        return knownLabels[base.lowercase()] ?: base
    }

    private fun folders(context:Context):List<File> = listOfNotNull(
        File(context.filesDir,FOLDER),
        context.getExternalFilesDir(null)?.let {File(it,FOLDER)})

    /** Font files under either folder, private storage first, one entry per file name. */
    fun files(context:Context):List<File> {
        val found=linkedMapOf<String,File>()
        folders(context).forEach {folder->
            folder.listFiles()?.sortedBy {it.name}?.forEach {file->
                if(file.isFile && file.extension.lowercase() in extensions) {
                    val key=file.name.lowercase()
                    if(!found.containsKey(key)) found[key]=file
                }
            }
        }
        return found.values.sortedBy {label(it.name).lowercase()}
    }

    /** The chooser's options: the two built-in choices followed by every font file. */
    fun catalog(context:Context):List<FontCatalog.Entry> =
        FontCatalog.entries + files(context).map {FontCatalog.Entry(FILE_PREFIX+it.name,label(it.name),null)}

    /** Resolves a stored id to a file. A name can never point outside the font folder. */
    fun file(context:Context,id:String):File? {
        if(!id.startsWith(FILE_PREFIX)) return null
        val name=id.removePrefix(FILE_PREFIX)
        if(name.isBlank()) return null
        return folders(context).firstNotNullOfOrNull {folder->
            File(folder,name).takeIf {it.isFile && it.canonicalFile.parentFile==folder.canonicalFile}
        }
    }

    /** Wording for a stored id, used by the settings rows. */
    fun labelFor(context:Context,id:String,fallback:String):String = when {
        id==FontCatalog.SYSTEM -> FontCatalog.label(id)
        id==FontCatalog.CUSTOM -> fallback.ifBlank {FontCatalog.label(id)}
        id.startsWith(FILE_PREFIX) -> label(id.removePrefix(FILE_PREFIX))
        else -> FontCatalog.label(id)
    }

    /**
     * Copies bundled fonts into the font folder once, and records them in a marker file so a font
     * the user removes by hand is not resurrected on the next launch. Runs off the main thread.
     */
    fun adopt(context:Context) {
        val assets=runCatching {context.assets.list(ASSET_FOLDER)?.toList().orEmpty()}.getOrDefault(emptyList())
            .filter {it.substringAfterLast('.',"").lowercase() in extensions}
        if(assets.isEmpty()) return
        val folder=File(context.filesDir,FOLDER).apply {mkdirs()}
        val marker=File(folder,".adopted")
        val adopted=(marker.takeIf {it.isFile}?.readLines()?.filter {it.isNotBlank()}?.toMutableSet()) ?: mutableSetOf()
        var changed=false
        assets.forEach {name->
            if(name in adopted) return@forEach
            val destination=File(folder,name)
            try {
                if(!destination.isFile) context.assets.open("$ASSET_FOLDER/$name").use {input->
                    destination.outputStream().use {output->input.copyTo(output)}
                }
                adopted+=name
                changed=true
            } catch(_:Exception) {
                // A corrupt asset is skipped rather than blocking the rest of the list.
                destination.delete()
            }
        }
        if(changed) runCatching {marker.writeText(adopted.sorted().joinToString("\n")+"\n")}
    }
}
