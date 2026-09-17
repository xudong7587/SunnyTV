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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
class AppModel @JvmOverloads constructor(application: Application, private val restoreSources:Boolean=true) : AndroidViewModel(application) {
    val app = application as SunnyApp
    var sources by mutableStateOf<List<SourceConfig>>(emptyList()); private set
    var settings by mutableStateOf(if(restoreSources) app.store.settings() else AppSettings()); private set
    var message by mutableStateOf("")
    var busy by mutableStateOf(false); private set
    var route by mutableStateOf<Route>(Route.Home); private set
    var sourcesReady by mutableStateOf(false); private set

    val feeds = mutableStateMapOf<String, HomeFeed>()
    val errors = mutableStateMapOf<String, String>()
    val loading = mutableStateMapOf<String, Boolean>()
    val libraryResume = mutableStateMapOf<String, List<MediaEntry>>()
    val libraryLatest = mutableStateMapOf<String, List<MediaEntry>>()
    var heroCandidates by mutableStateOf<List<MediaEntry>>(emptyList());private set
    private val artworkFeedSlots=Semaphore(3)
    val folderPages = mutableStateMapOf<String, MediaPage>()
    val folderPreviews = mutableStateMapOf<String, List<MediaEntry>>()
    val similar = mutableStateMapOf<String,List<MediaEntry>>()
    val selectedVersion = mutableStateMapOf<String,String>()
    val selectedAudio = mutableStateMapOf<String,MediaTrack>()
    val selectedSubtitle = mutableStateMapOf<String,String>()
    val selectedSubtitleTrack = mutableStateMapOf<String,MediaTrack>()
    val pages = mutableStateMapOf<String, MediaPage>()
    val personWorks = mutableStateMapOf<String,MediaPage>()
    private val mediaLibraries=mutableMapOf<String,String>()
    val children = mutableStateMapOf<String, List<MediaEntry>>()
    val details = mutableStateMapOf<String, MediaEntry>()
    val folders = mutableStateMapOf<String, List<MediaEntry>>()
    val focusMemory = mutableMapOf<String, String>()

    private val stack = mutableListOf<Route>()
    private val loads = mutableMapOf<String, Job>()
    private val librarySort = mutableMapOf<String, String>()
    private var playJob: Job? = null

    init {
        if(restoreSources) viewModelScope.launch {
            try {
                sources = withContext(Dispatchers.IO) { app.store.sources() }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message = "账号密钥无法读取，请在设置中重置来源后重新登录"
            } finally {sourcesReady=true}
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
        libraryLatest.clear()
        sources.filter { it.kind == SourceKind.EMBY }.forEach { config ->
            launchLoad("feed:${config.id}", replace = true) { feeds[config.id] = app.emby(config).home();loadHero() }
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

    fun loadLatest(item:MediaEntry) {
        if(libraryLatest.containsKey(item.key)) return
        launchLoad("latest:${item.key}") {
            val entries=artworkFeedSlots.withPermit {app.emby(source(item.sourceId)).latest(item.id,10)}
            entries.forEach {mediaLibraries[it.key]=item.key};libraryLatest[item.key]=entries
        }
    }
    fun loadHero() {
        val preferences=settings
        val libraries=feeds.values.flatMap {it.libraries}.filter {preferences.heroAllLibraries || it.key in preferences.heroLibraryKeys}
        if(preferences.heroMode!="random") {heroCandidates=emptyList();return}
        launchLoad("hero",replace=true) {
            if(libraries.isEmpty()) {heroCandidates=emptyList();return@launchLoad}
            // Sample at most six libraries and six items each; never enumerate an entire library.
            val sampled=coroutineScope {libraries.shuffled().take(6).map {library->async {
                artworkFeedSlots.withPermit {
                    try {app.emby(source(library.sourceId)).library(library.id,sort="Random",limit=7,
                        mixed=library.collectionType in setOf("","mixed","homevideos")).items}
                    catch(e:CancellationException) {throw e}
                    catch(_:Exception) {emptyList()}
                }
            }}.awaitAll()}
            val rows=sampled.map {it.shuffled()}
            // Round-robin selection keeps small libraries represented in the six visible choices.
            heroCandidates=(0 until 7).flatMap {index->rows.mapNotNull {it.getOrNull(index)}}.distinctBy {it.key}.take(7)
            if(heroCandidates.isEmpty()) errors["hero"]="所选媒体库暂时没有可用推荐"
        }
    }
    fun loadLibraryFolders(item:MediaEntry,more:Boolean=false) {
        launchLoad("folders:${item.key}",replace=!more) {
            val old=folderPages[item.key]
            val next=app.emby(source(item.sourceId)).library(item.id,if(more) old?.items?.size ?: 0 else 0,
                "SortName",foldersOnly=true)
            next.items.forEach {mediaLibraries[it.key]=mediaLibraries[item.key] ?: item.key}
            folderPages[item.key]=if(more) MediaPage(((old?.items ?: emptyList())+next.items).distinctBy {it.key},next.total) else next
        }
    }
    fun loadFolderPreview(item:MediaEntry) {
        if(folderPreviews.containsKey(item.key)) return
        launchLoad("folder-preview:${item.key}") {
            val entries=app.emby(source(item.sourceId)).library(item.id,limit=10,mixed=true).items
            mediaLibraries[item.key]?.let {library->entries.forEach {mediaLibraries[it.key]=library}}
            folderPreviews[item.key]=entries
        }
    }
    fun loadLibrary(item: MediaEntry, sort: String = "DateCreated", more: Boolean = false,ascending:Boolean=sort=="SortName") {
        val sortKey="$sort:$ascending"
        val append = more && librarySort[item.key] == sortKey
        launchLoad("library:${item.key}", replace = !more) {
            val api = app.emby(source(item.sourceId))
            val old = pages[item.key]
            val fresh = api.library(item.id, if (append) old?.items?.size ?: 0 else 0, sort,
                ascending=ascending,mixed=item.collectionType in setOf("","mixed","homevideos") || item.type=="Folder")
            fresh.items.forEach {mediaLibraries[it.key]=mediaLibraries[item.key] ?: item.key}
            pages[item.key] = if (append) {
                MediaPage(((old?.items ?: emptyList()) + fresh.items).distinctBy { it.key }, fresh.total)
            } else fresh
            librarySort[item.key] = sortKey
            if (!append) {
                try { libraryResume[item.key] = api.resume(item.id) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { libraryResume[item.key] = emptyList() }
            }
        }
    }

    fun loadDetail(item: MediaEntry) {
        if (sources.firstOrNull {it.id==item.sourceId}?.kind != SourceKind.EMBY) return
        if(item.type=="Person") {loadPerson(item);return}
        launchLoad("detail:${item.key}", replace = true) {
            val api = app.emby(source(item.sourceId))
            val entry = api.item(item.id)
            details[item.key] = entry
            if (entry.type in setOf("Series", "Season")) {
                val entries=api.children(entry)
                mediaLibraries[item.key]?.let {library->entries.forEach {mediaLibraries[it.key]=library}}
                children[item.key] = entries
            }
            launchLoad("similar:${item.key}",replace=true) {similar[item.key]=api.similar(item.id)}
        }
    }

    fun loadPerson(person:MediaEntry) {
        launchLoad("person:${person.key}",replace=true) {
            details[person.key]=app.emby(source(person.sourceId)).person(person.title)
            loadPersonWorks(person)
        }
    }
    fun loadPersonWorks(person:MediaEntry,more:Boolean=false) {
        launchLoad("person-works:${person.key}",replace=!more) {
            val resolved=details[person.key] ?: person
            val old=personWorks[person.key]
            val next=app.emby(source(person.sourceId)).personWorks(resolved.id,if(more) old?.items?.size ?: 0 else 0)
            personWorks[person.key]=if(more) MediaPage((old?.items.orEmpty()+next.items).distinctBy {it.key},next.total) else next
        }
    }
    private fun subtitlePreference(item:MediaEntry):String {
        val library=mediaLibraries[item.key] ?: stack.filterIsInstance<Route.Library>().lastOrNull()?.item?.key
        return settings.librarySubtitlePreferences[library] ?: settings.subtitlePreference
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
                val playable=if(config.kind==SourceKind.EMBY && item.type in setOf("Series","Season")) app.emby(config).playableEpisode(item,fromStart) else item
                val request = if (config.kind == SourceKind.EMBY) app.emby(config).playback(playable, fromStart,selectedVersion[playable.key].orEmpty())
                else app.dav(config).playback(item, if (fromStart) 0 else app.store.position(item.key))
                ensureActive()
                val chosenSubtitle=selectedSubtitleTrack[playable.key]
                val version=details[playable.key]?.versions?.firstOrNull {it.id==request.mediaSourceId}
                val embedded=(version?.tracks ?: details[playable.key]?.tracks ?: playable.tracks).filter {it.type=="Subtitle" && !it.external}
                ready(request.copy(requestedAtMs = requestedAt, sourceReadyAtMs = SystemClock.elapsedRealtime(),
                    audioLanguage=selectedAudio[item.key]?.language.orEmpty(),audioTitle=selectedAudio[item.key]?.title.orEmpty(),
                    subtitlePreference=if(selectedSubtitle[playable.key]=="none") "none" else subtitlePreference(item),
                    subtitleTitle=chosenSubtitle?.title.orEmpty(),
                    explicitSubtitle=chosenSubtitle!=null,
                    subtitleTrackId=chosenSubtitle?.takeIf {it.external}?.let {"emby-sub:${it.index}"}.orEmpty(),
                    subtitleOrdinal=if(chosenSubtitle!=null && !chosenSubtitle.external) embedded.indexOfFirst {it.index==chosenSubtitle.index} else -1))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = safeError(e) }
            finally { busy = false }
        }
    }

    fun favorite(item: MediaEntry) {
        launchLoad("favorite:${item.key}") {
            val current=details[item.key] ?: item
            val changed=app.emby(source(item.sourceId)).setFavorite(current, !current.favorite)
            updateUserData(changed)
        }
    }
    fun played(item:MediaEntry) {
        // A series/season write can update every episode and emit a notification per item.
        // This control intentionally operates on individual playable media only.
        if(!item.isPlayable) return
        launchLoad("played:${item.key}") {
            val current=details[item.key] ?: item
            val changed=app.emby(source(item.sourceId)).setPlayed(current,!current.played)
            updateUserData(changed)
        }
    }

    private fun updateUserData(changed:MediaEntry) {
        fun MediaEntry.updated()=if(key==changed.key) copy(favorite=changed.favorite,played=changed.played,positionMs=changed.positionMs) else this
        details[changed.key]=changed
        pages.keys.toList().forEach {k->pages[k]?.let {pages[k]=it.copy(items=it.items.map {entry->entry.updated()})}}
        listOf(children,libraryLatest,libraryResume,similar,folderPreviews).forEach {map->
            map.keys.toList().forEach {k->map[k]=map[k].orEmpty().map {it.updated()}}
        }
        feeds.keys.toList().forEach {k->feeds[k]?.let {f->feeds[k]=f.copy(resume=f.resume.map {it.updated()},latest=f.latest.map {it.updated()},nextUp=f.nextUp.map {it.updated()})}}
        heroCandidates=heroCandidates.map {it.updated()}
    }

    fun saveSettings(value: AppSettings) {
        val old=settings;settings=value;if(restoreSources) app.store.saveSettings(value)
        if(old.heroMode!=value.heroMode || old.heroAllLibraries!=value.heroAllLibraries || old.heroLibraryKeys!=value.heroLibraryKeys) loadHero()
    }

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
        libraryLatest.clear(); folderPages.clear(); folderPreviews.clear(); similar.clear(); personWorks.clear(); mediaLibraries.clear()
        selectedVersion.clear(); selectedAudio.clear(); selectedSubtitle.clear(); selectedSubtitleTrack.clear()
        heroCandidates=emptyList()
    }

    private fun launchLoad(key: String, replace: Boolean = false, block: suspend () -> Unit) {
        if (loads[key]?.isActive == true && !replace) return
        loads.remove(key)?.cancel()
        errors.remove(key)
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                errors[key] = safeError(e)
                if(key.startsWith("favorite:") || key.startsWith("played:")) message=errors[key].orEmpty()
            }
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
