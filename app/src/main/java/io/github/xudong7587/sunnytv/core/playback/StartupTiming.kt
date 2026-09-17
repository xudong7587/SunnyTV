package io.github.xudong7587.sunnytv.core.playback

/** Monotonic timestamps only: no URLs, tokens, wall-clock changes or network I/O. */
data class StartupDurations(
    val totalMs: Long?,
    val sourceMs: Long?,
    val handoffMs: Long?,
    val engineMs: Long?
)

object StartupTiming {
    fun between(start: Long, end: Long): Long? =
        if (start >= 0 && end >= start) end - start else null

    /** A resumed/retried player passes -1 request marks, not the previous session's marks. */
    fun measure(request: Long, source: Long, engine: Long, frame: Long): StartupDurations {
        val engineMs = between(engine, frame)
        val ordered = request >= 0 && source >= request && engine >= source && frame >= engine
        return if (ordered) StartupDurations(frame - request, source - request, engine - source, engineMs)
        else StartupDurations(null, null, null, engineMs)
    }
}
