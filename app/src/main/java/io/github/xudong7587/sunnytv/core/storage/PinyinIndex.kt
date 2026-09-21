package io.github.xudong7587.sunnytv.core.storage

import android.icu.text.Transliterator
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Pinyin helpers for the TV search quick-pick. Uses the platform's ICU transliterator, so no pinyin
 * dictionary is bundled and no extra download is needed. Everything degrades to null when the
 * transliterator is unavailable, and the caller then simply shows no quick-pick.
 */
object PinyinIndex {
    private val hanToLatin:Transliterator? by lazy {
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.Q) null else createHanToLatin()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createHanToLatin():Transliterator? =
        runCatching {Transliterator.getInstance("Han-Latin; Latin-ASCII")}.getOrNull()

    /**
     * Han -> Latin for one chunk of text. ICU transliteration is only reachable from API 29 on, so
     * older devices and unusable ICU both return null and the caller shows no quick-pick.
     */
    private fun transliterate(text:String):String? =
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q) transliterateOnQ(text) else null

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun transliterateOnQ(text:String):String? = hanToLatin?.transliterate(text)?.trim()

    /** First pinyin initial of [text], the first letter of a Latin title, or null. */
    fun initial(text:String):String? {
        val first=text.trimStart().firstOrNull() ?: return null
        if(first.code<128 && first.isLetterOrDigit()) return first.uppercaseChar().toString()
        val latin=transliterate(first.toString()).orEmpty()
        return latin.firstOrNull {it.isLetter()}?.uppercaseChar()?.toString()
    }

    data class Suggestion(val initial:String, val label:String, val query:String)

    /** All pinyin initials of [text] as lowercase letters/digits: 速度与激情 -> sdyjq. */
    fun initials(text:String):String {
        val builder=StringBuilder()
        text.trim().forEach {ch->
            when {
                ch.code<128 && ch.isLetterOrDigit() -> builder.append(ch.lowercaseChar())
                ch.code<128 -> Unit
                else -> transliterate(ch.toString()).orEmpty()
                    .firstOrNull {it.isLetterOrDigit()}?.let {builder.append(it.lowercaseChar())}
            }
        }
        return builder.toString()
    }

    /**
     * Remote-friendly match: the typed letters must appear in order inside the title's pinyin
     * initials, so "sdyq" finds 速度与激情 (initials sdyjq). A plain substring match also counts.
     */
    fun matches(title:String, query:String):Boolean {
        val wanted=query.lowercase().filter {it.isLetterOrDigit()}
        if(wanted.isEmpty()) return false
        if(title.lowercase().contains(wanted)) return true
        val initials=initials(title)
        if(initials.isEmpty()) return false
        var index=0
        initials.forEach {ch->if(index<wanted.length && wanted[index]==ch) index++}
        return index==wanted.length
    }

    /**
     * Most frequent leading characters/words found in the user's own media titles, paired with the
     * pinyin initial. Picking one searches for that text, which is what a remote can do quickly.
     */
    fun suggestions(titles:List<String>,limit:Int=12):List<Suggestion> {
        val counts=linkedMapOf<String,Pair<Int,String>>()
        titles.forEach {title->
            val trimmed=title.trim()
            if(trimmed.length<2) return@forEach
            val head=trimmed.substringBefore(' ').take(2)
            if(head.isBlank()) return@forEach
            val initial=initial(trimmed) ?: return@forEach
            val current=counts[head]
            counts[head]=Pair((current?.first ?: 0)+1,initial)
        }
        return counts.entries.sortedByDescending {it.value.first}.take(limit)
            .map {Suggestion(it.value.second,it.key,it.key)}
    }
}
