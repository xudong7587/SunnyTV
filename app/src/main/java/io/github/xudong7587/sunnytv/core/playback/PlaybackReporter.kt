package io.github.xudong7587.sunnytv.core.playback

import io.github.xudong7587.sunnytv.core.model.PlaybackRequest
import io.github.xudong7587.sunnytv.source.emby.EmbySource
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** A short-lived serial reporter that may finish sending a stop after Activity.onStop(). */
class PlaybackReporter(
    private val request: PlaybackRequest,
    private val source: suspend () -> EmbySource?,
    private val onFailure: suspend () -> Unit
) {
    private val events = SessionEvents()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            try {
                val api = try { source() } catch (e: CancellationException) { throw e }
                catch (_: Exception) { onFailure(); null }
                if (api == null) return@launch
                // A conflated wakeup does not conflate the critical start/stop mailbox slots.
                for (signal in wake) flush(api)
                flush(api)
            } finally { scope.cancel() }
        }
    }

    private suspend fun flush(api: EmbySource) {
        while (true) {
            val event = events.poll() ?: return
            try {
                withTimeout(4_000) { api.report(request, event.event, event.positionMs, event.paused) }
            } catch (e: TimeoutCancellationException) {
                onFailure()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onFailure() // Never log the request URL, token or server response body.
            }
        }
    }

    fun start(positionMs: Long, paused: Boolean) { events.begin(positionMs, paused); wake.trySend(Unit) }
    fun progress(positionMs: Long, paused: Boolean) { events.progress(positionMs, paused); wake.trySend(Unit) }
    fun close(positionMs: Long, started: Boolean) {
        if (started) events.stop(positionMs)
        wake.trySend(Unit)
        wake.close()
    }
}
