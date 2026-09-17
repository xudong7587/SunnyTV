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
    fun wideArtwork(item: MediaEntry): Artwork? = item.thumb ?: item.backdrop ?: item.primary

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
