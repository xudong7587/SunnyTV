package io.github.xudong7587.sunnytv.feature

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.xudong7587.sunnytv.SunnyApp
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.network.HttpPolicy
import io.github.xudong7587.sunnytv.core.network.safeError
import io.github.xudong7587.sunnytv.source.emby.EmbySource
import kotlinx.coroutines.*
import java.util.UUID

sealed interface Route {
    data object Home : Route
    data object Libraries : Route
    data object Cloud : Route
    data object Settings : Route
    data object Search : Route
    data class Library(val item: MediaEntry) : Route
    data class Detail(val item: MediaEntry) : Route
    data class Folder(val sourceId: String, val path: String, val title: String) : Route
}

fun Route.key(): String = when (this) {
    is Route.Library -> "lib:${item.key}"
    is Route.Detail -> "detail:${item.key}"
    is Route.Folder -> "dav:$sourceId:$path"
    else -> javaClass.simpleName
}

/** Application coordinator. Source implementations and transport policies never depend on Compose. */
class AppModel(application: Application) : AndroidViewModel(application) {
    val app = application as SunnyApp
    var sources by mutableStateOf<List<SourceConfig>>(emptyList()); private set
    var settings by mutableStateOf(app.store.settings()); private set
    var message by mutableStateOf("")
    var busy by mutableStateOf(false); private set
    var route by mutableStateOf<Route>(Route.Home); private set

    val feeds = mutableStateMapOf<String, HomeFeed>()
    val errors = mutableStateMapOf<String, String>()
    val loading = mutableStateMapOf<String, Boolean>()
    val libraryResume = mutableStateMapOf<String, List<MediaEntry>>()
    val pages = mutableStateMapOf<String, MediaPage>()
    val children = mutableStateMapOf<String, List<MediaEntry>>()
    val details = mutableStateMapOf<String, MediaEntry>()
    val folders = mutableStateMapOf<String, List<MediaEntry>>()
    val focusMemory = mutableMapOf<String, String>()

    private val stack = mutableListOf<Route>()
    private val loads = mutableMapOf<String, Job>()
    private val librarySort = mutableMapOf<String, String>()
    private var playJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                sources = withContext(Dispatchers.IO) { app.store.sources() }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message = "账号密钥无法读取，请在设置中重置来源后重新登录"
            }
        }
    }

    fun source(id: String): SourceConfig = sources.firstOrNull { it.id == id }
        ?: throw IllegalArgumentException("来源已移除，请返回并重新选择媒体")

    fun navigate(next: Route, root: Boolean = false) {
        if (next == route) return
        if (root) stack.clear() else {
            if (stack.size >= 32) stack.removeAt(0)
            stack.add(route)
        }
        route = next
        when (next) {
            is Route.Library -> if (!pages.containsKey(next.item.key)) loadLibrary(next.item)
            is Route.Detail -> loadDetail(next.item)
            is Route.Folder -> if (!folders.containsKey("${next.sourceId}:${next.path}")) loadFolder(next.sourceId, next.path)
            else -> Unit
        }
    }

    fun back(): Boolean {
        if (busy && playJob?.isActive == true) { playJob?.cancel(); return true }
        if (stack.isNotEmpty()) { route = stack.removeAt(stack.lastIndex); return true }
        if (route != Route.Home) { route = Route.Home; return true }
        return false
    }

    fun refresh() {
        sources.filter { it.kind == SourceKind.EMBY }.forEach { config ->
            launchLoad("feed:${config.id}", replace = true) { feeds[config.id] = app.emby(config).home() }
        }
    }

    fun afterPlayback() {
        refresh()
        // Do not reload the poster wall or reset its scroll position to update one item.
        when (val current = route) {
            is Route.Detail -> loadDetail(current.item)
            is Route.Library -> launchLoad("resume:${current.item.key}", replace = true) {
                libraryResume[current.item.key] = app.emby(source(current.item.sourceId)).resume(current.item.id)
            }
            else -> Unit
        }
    }

    fun loadLibrary(item: MediaEntry, sort: String = "DateCreated", more: Boolean = false) {
        val append = more && librarySort[item.key] == sort
        launchLoad("library:${item.key}", replace = !more) {
            val api = app.emby(source(item.sourceId))
            val old = pages[item.key]
            val fresh = api.library(item.id, if (append) old?.items?.size ?: 0 else 0, sort)
            pages[item.key] = if (append) {
                MediaPage(((old?.items ?: emptyList()) + fresh.items).distinctBy { it.key }, fresh.total)
            } else fresh
            librarySort[item.key] = sort
            if (!append) {
                try { libraryResume[item.key] = api.resume(item.id) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { libraryResume[item.key] = emptyList() }
            }
        }
    }

    fun loadDetail(item: MediaEntry) {
        if (source(item.sourceId).kind != SourceKind.EMBY) return
        launchLoad("detail:${item.key}", replace = true) {
            val api = app.emby(source(item.sourceId))
            val entry = api.item(item.id)
            details[item.key] = entry
            if (entry.type in setOf("Series", "Season")) children[item.key] = api.children(entry)
        }
    }

    fun loadFolder(id: String, path: String) {
        launchLoad("folder:$id:$path", replace = true) { folders["$id:$path"] = app.dav(source(id)).list(path) }
    }

    fun search(query: String) {
        sources.filter { it.kind == SourceKind.EMBY }.forEach { config ->
            val key = "search:${config.id}"
            // A slower old query must never overwrite the result of a newer search.
            loads.remove(key)?.cancel()
            pages.remove(key)
            if (query.isNotBlank()) launchLoad(key, replace = true) {
                pages[key] = app.emby(config).library("", query = query.trim())
            }
        }
    }

    fun open(item: MediaEntry) {
        when {
            source(item.sourceId).kind == SourceKind.EMBY -> navigate(Route.Detail(item))
            item.isFolder -> navigate(Route.Folder(item.sourceId, item.path, item.title))
        }
    }

    fun play(item: MediaEntry, fromStart: Boolean = false, ready: (PlaybackRequest) -> Unit) {
        if (busy) return
        val requestedAt = SystemClock.elapsedRealtime()
        playJob = viewModelScope.launch {
            busy = true
            try {
                val config = source(item.sourceId)
                val request = if (config.kind == SourceKind.EMBY) app.emby(config).playback(item, fromStart)
                else app.dav(config).playback(item, if (fromStart) 0 else app.store.position(item.key))
                ensureActive()
                ready(request.copy(requestedAtMs = requestedAt, sourceReadyAtMs = SystemClock.elapsedRealtime()))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = safeError(e) }
            finally { busy = false }
        }
    }

    fun favorite(item: MediaEntry) {
        launchLoad("favorite:${item.key}") {
            app.emby(source(item.sourceId)).setFavorite(item, !item.favorite)
            details[item.key] = item.copy(favorite = !item.favorite)
        }
    }

    fun saveSettings(value: AppSettings) { settings = value; app.store.saveSettings(value) }

    fun addSource(kind: SourceKind, name: String, base: String, user: String, password: String, onSuccess: () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                val config = if (kind == SourceKind.EMBY) {
                    EmbySource.login(base, name, user, password, app.http, app.store.deviceId)
                } else {
                    SourceConfig(UUID.randomUUID().toString(), kind, name.ifBlank { "CloudDrive2" },
                        HttpPolicy.serverBase(base), username = user, secret = password).also { app.dav(it).list() }
                }
                val updated = sources + config
                withContext(Dispatchers.IO) { app.store.saveSources(updated) }
                sources = updated
                onSuccess()
                refresh()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = safeError(e) }
            finally { busy = false }
        }
    }

    fun removeSource(id: String) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                cancelLoads()
                val updated = sources.filter { it.id != id }
                withContext(Dispatchers.IO) {
                    app.store.saveSources(updated)
                    app.store.removePositions(id)
                    app.removeArtwork(id)
                }
                sources = updated
                clearMediaState()
                stack.clear()
                route = Route.Settings
                refresh()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = safeError(e) }
            finally { busy = false }
        }
    }

    fun resetSources() {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                cancelLoads()
                withContext(Dispatchers.IO) { app.store.resetSources(); app.clearArtwork() }
                sources = emptyList()
                clearMediaState()
                stack.clear()
                route = Route.Settings
                message = "来源已清除，请重新添加"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = safeError(e) }
            finally { busy = false }
        }
    }

    private fun cancelLoads() {
        loads.values.forEach { it.cancel() }
        loads.clear()
        loading.clear()
        playJob?.cancel()
    }

    private fun clearMediaState() {
        feeds.clear(); errors.clear(); pages.clear(); libraryResume.clear()
        details.clear(); children.clear(); folders.clear(); librarySort.clear(); focusMemory.clear()
    }

    private fun launchLoad(key: String, replace: Boolean = false, block: suspend () -> Unit) {
        if (loads[key]?.isActive == true && !replace) return
        loads.remove(key)?.cancel()
        errors.remove(key)
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { errors[key] = safeError(e) }
            finally {
                if (loads[key] == currentCoroutineContext()[Job]) {
                    loads.remove(key)
                    loading.remove(key)
                }
            }
        }
        loads[key] = job
        loading[key] = true
        job.start()
    }
}
