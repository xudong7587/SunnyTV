package io.github.xudong7587.sunnytv.core.model

object MediaLogic {
    fun progress(position: Long, duration: Long): Float =
        if (duration <= 0) 0f else (position.toDouble() / duration).coerceIn(0.0, 1.0).toFloat()

    fun remainingMinutes(position: Long, duration: Long): Long {
        val remaining = (duration.coerceAtLeast(0) - position.coerceAtLeast(0)).coerceAtLeast(0)
        return remaining / 60_000 + if (remaining % 60_000 > 0) 1 else 0
    }

    fun seek(current: Long, delta: Long, duration: Long): Long {
        val start = current.coerceAtLeast(0)
        val target = when {
            delta > 0 && start > Long.MAX_VALUE - delta -> Long.MAX_VALUE
            delta < 0 && start < -delta && delta != Long.MIN_VALUE -> 0
            delta == Long.MIN_VALUE -> 0
            else -> (start + delta).coerceAtLeast(0)
        }
        return if (duration > 0) target.coerceAtMost(duration) else target
    }

    fun libraryArtwork(item: MediaEntry): Artwork? = item.primary ?: item.thumb ?: item.backdrop
    fun wideArtwork(item: MediaEntry): Artwork? = if(item.type=="Episode") item.primary ?: item.thumb ?: item.backdrop else item.thumb ?: item.backdrop ?: item.primary

    /**
     * Emby's item query accepts a fixed list of sort keys (Album, … , Runtime, Random); file bitrate
     * and file size are not among them, so those two sorts are ordered on the device instead of
     * being sent to a server that would reject or ignore them.
     */
    fun isLocalSort(sort:String):Boolean = sort in setOf("Bitrate","Size")

    val ugreenSorts=listOf("SortName" to "名称", "CommunityRating" to "评分", "ProductionYear" to "出品年份",
        "Runtime" to "播放时长", "DatePlayed" to "播放日期", "Bitrate" to "比特率", "Size" to "大小", "Random" to "随机")

    fun hasUgreenSortData(items:List<MediaEntry>,sort:String):Boolean = items.isEmpty() || when(sort) {
        "CommunityRating"->items.any {it.rating.isFinite() && it.rating>0}
        "ProductionYear"->items.any {it.year>0}
        "Runtime"->items.any {it.durationMs>0}
        "DatePlayed"->items.any {it.lastPlayedAtMs>0}
        "Size"->items.any {it.versions.any {v->v.size>0}}
        "Bitrate"->items.any {it.versions.any {v->v.bitrate>0}}
        else->true
    }

    /** Complete directory snapshot only. Missing values stay last in either direction. */
    fun ugreenLocalSort(items:List<MediaEntry>,sort:String,ascending:Boolean):List<MediaEntry> {
        require(ugreenSorts.any {it.first==sort})
        fun <T:Comparable<T>> ordered(value:(MediaEntry)->T?):List<MediaEntry> = items.sortedWith {left,right->
            val a=value(left);val b=value(right)
            when {a==null && b==null->0;a==null->1;b==null->-1;ascending->a.compareTo(b);else->b.compareTo(a)}
        }
        return when(sort) {
            "Random"->items.shuffled()
            "CommunityRating"->ordered {it.rating.takeIf {n->n.isFinite() && n>0}}
            "ProductionYear"->ordered {it.year.takeIf {n->n>0}}
            "Runtime"->ordered {it.durationMs.takeIf {n->n>0}}
            "DatePlayed"->ordered {it.lastPlayedAtMs.takeIf {n->n>0}}
            "Size"->ordered {it.versions.maxOfOrNull {v->v.size}?.takeIf {n->n>0}}
            "Bitrate"->ordered {it.versions.maxOfOrNull {v->v.bitrate}?.takeIf {n->n>0}}
            else->{
                val collator=java.text.Collator.getInstance(java.util.Locale.CHINA).apply {strength=java.text.Collator.SECONDARY}
                items.sortedWith {a,b->if(ascending) collator.compare(a.title,b.title) else collator.compare(b.title,a.title)}
            }
        }
    }

    /** Sorts one loaded page by its largest media version. Items without media data keep a stable 0. */
    fun localSort(items:List<MediaEntry>,sort:String,ascending:Boolean):List<MediaEntry> {
        val value:(MediaEntry)->Long = if(sort=="Size") {entry->entry.versions.maxOfOrNull {it.size} ?: 0L}
            else {entry->entry.versions.maxOfOrNull {it.bitrate} ?: 0L}
        val ordered=items.sortedWith(compareBy {value(it)})
        return if(ascending) ordered else ordered.asReversed()
    }

    fun mime(container: String): String? = when (container.lowercase().trimStart('.')) {
        "mp4", "m4v", "mov" -> "video/mp4"
        "mkv", "matroska" -> "video/x-matroska"
        "webm" -> "video/webm"
        "m3u8", "hls" -> "application/x-mpegURL"
        "mpd", "dash" -> "application/dash+xml"
        "ts", "m2ts", "mpegts" -> "video/mp2t"
        else -> null // STRM and extensionless MediaIndex URLs are not necessarily HLS.
    }
}
