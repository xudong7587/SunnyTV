package io.github.xudong7587.sunnytv.core.storage

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import io.github.xudong7587.sunnytv.core.model.AppSettings
import java.io.File

data class FontPreset(val id:String,val label:String,val assetPath:String?=null)

object FontCatalog {
    val presets=listOf(
        FontPreset("system","系统默认字体"),
        FontPreset("fz_zhenghei","方正正准黑简体","fonts/fz_zhenghei.ttf"),
        FontPreset("fz_youhei","方正悠黑简体 512B","fonts/fz_youhei.ttf"),
        FontPreset("coca_cola_care","可口可乐在乎体 · 文本细","fonts/coca_cola_care.ttf")
    )
    fun preset(id:String)=presets.firstOrNull {it.id==id} ?: presets.first()
    fun available(context:Context,preset:FontPreset):Boolean {
        val asset=preset.assetPath ?: return true
        return runCatching {context.assets.open(asset).use {it.read()>-2};true}.getOrDefault(false)
    }
    fun label(settings:AppSettings):String = when(settings.fontPreset) {
        "custom" -> settings.customFontName.ifBlank {"用户自定义字体"}
        else -> preset(settings.fontPreset).label
    }
    fun resolve(context:Context,settings:AppSettings):FontFamily {
        val typeface=when(settings.fontPreset) {
            "custom" -> {
                if(settings.customFontFile.isBlank()) null else runCatching {
                    val folder=File(context.filesDir,"fonts").canonicalFile
                    val file=File(folder,settings.customFontFile).canonicalFile
                    require(file.parentFile==folder && file.isFile)
                    Typeface.createFromFile(file)
                }.getOrNull()
            }
            "system" -> null
            else -> preset(settings.fontPreset).assetPath?.let {asset->
                runCatching {Typeface.createFromAsset(context.assets,asset)}.getOrNull()
            }
        }
        return typeface?.let {FontFamily(it)} ?: FontFamily.SansSerif
    }
}
