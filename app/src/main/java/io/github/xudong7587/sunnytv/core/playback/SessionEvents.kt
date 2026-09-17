package io.github.xudong7587.sunnytv.core.playback

/** Bounded, thread-safe event mailbox: start -> latest progress -> stop, never progress before start. */
data class SessionEvent(val event: String, val positionMs: Long, val paused: Boolean)

class SessionEvents {
    private var started = false
    private var stopping = false
    private var start: SessionEvent? = null
    private var progress: SessionEvent? = null
    private var stop: SessionEvent? = null

    @Synchronized fun begin(positionMs: Long, paused: Boolean) {
        if (started || stopping) return
        started = true
        start = SessionEvent("Playing", positionMs.coerceAtLeast(0), paused)
    }

    @Synchronized fun progress(positionMs: Long, paused: Boolean) {
        if (started && !stopping) progress = SessionEvent("Playing/Progress", positionMs.coerceAtLeast(0), paused)
    }

    @Synchronized fun stop(positionMs: Long, paused: Boolean = true) {
        if (!started || stopping) return
        stopping = true
        progress = null // The stop position supersedes any untransmitted heartbeat.
        stop = SessionEvent("Playing/Stopped", positionMs.coerceAtLeast(0), paused)
    }

    @Synchronized fun poll(): SessionEvent? {
        start?.let { start = null; return it }
        progress?.let { progress = null; return it }
        stop?.let { stop = null; return it }
        return null
    }
}
