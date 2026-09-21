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
import io.github.xudong7587.sunnytv.core.storage.StartupCache

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

/** Long-press actions on a library or media item. Server-side only; never a local file operation. */
enum class MediaAction(val label:String,val description:String) {
    REFRESH_METADATA("刷新元数据","请服务端重新识别并抓取这个条目的信息与图片"),
    REFRESH_LIBRARY("刷新媒体库","请服务端重新扫描整个媒体库"),
    DELETE("删除","从 Emby 媒体库移除这个条目；服务端仍按账号权限决定是否同时删除文件")
}

fun Route.key(): String = when (this) {
    is Route.Library -> "lib:${item.key}"
    is Route.Detail -> "detail:${item.key}"
    is Route.Folder -> "dav:$sourceId:$path"
    else -> javaClass.simpleName
}

/** Application coordinator. Source implementations and transport policies never depend on Compose. */
class AppModel @JvmOverloads constructor(application: Application, private val restoreSources:Boolean=true,
    initialSources:List<SourceConfig> = emptyList()) : AndroidViewModel(application) {
    val app = application as SunnyApp
    var sources by mutableStateOf(initialSources); private set
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
    val libraryLatestModes = mutableStateMapOf<String, String>()
    var heroCandidates by mutableStateOf<List<MediaEntry>>(emptyList());private set
    /** Bumped on every manual/random carousel rebuild so a focused hero still shows the new sample. */
    var heroRevision by mutableIntStateOf(0); private set
    /** Random server-side recommendation strip shown above a library grid. */
    val recommendations = mutableStateMapOf<String, List<MediaEntry>>()
    /** Server-side delete permission per source; the detail page only offers delete when true. */
    val deletePermission = mutableStateMapOf<String, Boolean>()
    var carouselRefreshing by mutableStateOf(false); private set
    /** Bumped when a library banner finishes a manual refresh, so the page can move to a new item. */
    val carouselRevision = mutableStateMapOf<String,Int>()
    var actionTarget by mutableStateOf<MediaEntry?>(null); private set
    var pendingDelete by mutableStateOf<MediaEntry?>(null); private set
    private var heroJob: Job? = null
    private val artworkFeedSlots=Semaphore(if(app.lean(settings)) 2 else 3)
    private val staleLatest=mutableSetOf<String>()
    /** Recently shown carousel entries: fresh media is preferred so the same posters stop repeating. */
    private val seenHero=linkedSetOf<String>()
    private val seenRecommend=mutableMapOf<String,LinkedHashSet<String>>()
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
                // Only the browsed source is restored from cache; others are never loaded.
                val snapshots=withContext(Dispatchers.IO) {
                    activeEmbySources().mapNotNull {config->app.startup.read(config)?.let {config.id to it}}
                }
                snapshots.forEach {(id,snapshot)->feeds[id]=snapshot.feed;libraryLatest.putAll(snapshot.shelves)}
                staleLatest.addAll(libraryLatest.keys)
                refresh()
                loadDeletePermission()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message = "账号密钥无法读取，请在设置中重置来源后重新登录"
            } finally {sourcesReady=true}
        }
    }

    fun source(id: String): SourceConfig = sources.firstOrNull { it.id == id }
        ?: throw IllegalArgumentException("来源已移除，请返回并重新选择媒体")

    /**
     * Exactly one Emby source is browsed: the first configured one (or the explicitly stored id if
     * it still exists). Every page shows only that source's media; other sources are never merged in.
     */
    val activeSourceId:String get() =
        settings.activeSourceId.takeIf {id->sources.any {it.id==id && it.kind==SourceKind.EMBY}}
            ?: sources.firstOrNull {it.kind==SourceKind.EMBY}?.id.orEmpty()
    /** Media sources are browsed one at a time, never merged. */
    fun activeEmbySources():List<SourceConfig> {
        val active=activeSourceId
        return sources.filter {it.kind==SourceKind.EMBY && (active.isBlank() || it.id==active)}
    }
    fun activeSourceName():String = sources.firstOrNull {it.id==activeSourceId}?.name.orEmpty()

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
        staleLatest.addAll(libraryLatest.keys)
        // Only the browsed source is refreshed; other sources stay cached until they are selected.
        activeEmbySources().forEach { config ->
            launchLoad("feed:${config.id}", replace = true) {
                feeds[config.id] = app.emby(config).home()
                cacheHome(config)
                // Keep old artwork visible while refreshing only already requested shelves.
                feeds[config.id]?.libraries?.filter {it.key in staleLatest}?.forEach {loadLatest(it)}
                loadHero()
            }
        }
    }

    private fun cacheHome(config:SourceConfig) {
        if(!restoreSources) return
        val feed=feeds[config.id] ?: return
        val snapshot=StartupCache.Snapshot(feed,libraryLatest.filterKeys {
            it.startsWith("${config.id}:") && (libraryLatestModes[it] ?: "DateCreated")=="DateCreated"
        }.toMap())
        val generation=app.startup.generation
        launchLoad("startup:${config.id}",replace=true) {
            delay(250)
            withContext(Dispatchers.IO) {app.startup.write(config,snapshot,generation)}
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

    fun loadLatest(item:MediaEntry, sort:String=libraryLatestModes[item.key] ?: "DateCreated") {
        require(sort in listOf("DateCreated","PremiereDate","Random"))
        val changed=(libraryLatestModes[item.key] ?: "DateCreated")!=sort
        libraryLatestModes[item.key]=sort
        if(changed) libraryLatest.remove(item.key)
        if(!changed && libraryLatest.containsKey(item.key) && item.key !in staleLatest) return
        launchLoad("latest:${item.key}",replace=changed || item.key in staleLatest) {
            val entries=artworkFeedSlots.withPermit {
                val source=app.emby(source(item.sourceId))
                if(sort=="DateCreated") source.latest(item.id,10)
                else source.library(item.id,sort=sort,ascending=false,limit=10,
                    mixed=item.collectionType.lowercase() in setOf("mixed","homevideos")).items
            }
            if(libraryLatestModes[item.key]!=sort) return@launchLoad
            entries.forEach {mediaLibraries[it.key]=item.key};libraryLatest[item.key]=entries
            staleLatest.remove(item.key);cacheHome(source(item.sourceId))
        }
    }
    fun loadHero() {
        val preferences=settings
        val available=activeEmbySources().flatMap {feeds[it.id]?.libraries ?: emptyList()}
        val chosen=available.filter {it.key in preferences.heroLibraryKeys}
        // The library list belongs to one source: a selection made on another source must never
        // leave this one with no candidates, so an empty selection falls back to its own libraries.
        val libraries=when {
            preferences.heroAllLibraries -> available
            chosen.isNotEmpty() -> chosen
            else -> available
        }
        if(preferences.heroMode!="random") {heroCandidates=emptyList();heroRevision++;return}
        heroJob=launchLoad("hero",replace=true) {
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
            val pooled=(0 until 7).flatMap {index->rows.mapNotNull {it.getOrNull(index)}}.distinctBy {it.key}
            // Fresh media first, then whatever was shown recently, so the carousel keeps moving on.
            val fresh=pooled.filterNot {it.key in seenHero}
            heroCandidates=(fresh+pooled.filter {it.key in seenHero}).take(7)
            heroCandidates.forEach {seenHero.add(it.key)}
            while(seenHero.size>240) seenHero.remove(seenHero.first())
            if(heroCandidates.isEmpty()) errors["hero"]="所选媒体库暂时没有可用推荐"
            heroRevision++
        }
    }
    /** Re-samples the home carousel and the shelves behind it; used by the top "pull" gesture. */
    fun refreshCarousel() {
        if(carouselRefreshing) return
        carouselRefreshing=true
        viewModelScope.launch {
            try {
                refresh()
                // Wait for the feed rows that rebuild the carousel, then for the carousel itself.
                loads.toMap().filterKeys {it.startsWith("feed:")}.values.forEach {runCatching {it.join()}}
                heroJob?.let {runCatching {it.join()}}
                // A manual refresh must visibly move the banner even when the carousel follows
                // "最新入库 / 继续观看": rotate the pool so the front item changes too.
                if(settings.heroMode!="random" && heroCandidates.size>1) {
                    heroCandidates=heroCandidates.drop(1)+heroCandidates.take(1)
                    heroRevision++
                }
            } finally {carouselRefreshing=false}
        }
    }
    private fun loadDeletePermission() {
        activeEmbySources().forEach {config->
            launchLoad("policy:${config.id}") {
                deletePermission[config.id]=app.emby(config).canDeleteContent()
            }
        }
    }
    fun canDelete(item:MediaEntry):Boolean = itemActions(item).contains(MediaAction.DELETE) &&
        deletePermission[item.sourceId]==true
    /** Keeps entries that were not shown recently in front and remembers what the user has seen. */
    private fun preferFresh(entries:List<MediaEntry>,seen:LinkedHashSet<String>):List<MediaEntry> {
        val fresh=entries.filterNot {it.key in seen}
        val repeat=entries.filter {it.key in seen}
        val ordered=fresh+repeat
        ordered.forEach {seen.add(it.key)}
        while(seen.size>120) seen.remove(seen.first())
        return ordered
    }
    fun loadRecommendations(library:MediaEntry,force:Boolean=false) {
        if(!force && recommendations.containsKey(library.key)) return
        launchLoad("recommend:${library.key}",replace=force) {
            val entries=artworkFeedSlots.withPermit {
                // Seven entries: one large block plus six strips, exactly like the home carousel.
                app.emby(source(library.sourceId)).library(library.id,sort="Random",limit=7,
                    mixed=library.collectionType.lowercase() in setOf("","mixed","homevideos")).items
            }
            entries.forEach {mediaLibraries[it.key]=library.key}
            recommendations[library.key]=preferFresh(entries,seenRecommend.getOrPut(library.key) {linkedSetOf()})
        }
    }
    /** Refreshes a library page carousel together with its first poster page. */
    fun refreshLibraryCarousel(library:MediaEntry) {
        if(carouselRefreshing) return
        carouselRefreshing=true
        recommendations.remove(library.key)
        val jobs=listOf(
            launchLoad("recommend:${library.key}",replace=true) {
                val entries=artworkFeedSlots.withPermit {
                    app.emby(source(library.sourceId)).library(library.id,sort="Random",limit=7,
                        mixed=library.collectionType.lowercase() in setOf("","mixed","homevideos")).items
                }
                entries.forEach {mediaLibraries[it.key]=library.key}
                recommendations[library.key]=preferFresh(entries,seenRecommend.getOrPut(library.key) {linkedSetOf()})
            },
            launchLoad("library:${library.key}",replace=true) {
                val api=app.emby(source(library.sourceId))
                val sorted=librarySort[library.key]?.substringBefore(':') ?: "DateCreated"
                val ascending=librarySort[library.key]?.substringAfter(':')?.toBoolean() ?: (sorted=="SortName")
                val fresh=api.library(library.id,0,sorted,ascending=ascending,
                    mixed=library.collectionType in setOf("","mixed","homevideos") || library.type=="Folder")
                fresh.items.forEach {mediaLibraries[it.key]=mediaLibraries[library.key] ?: library.key}
                pages[library.key]=fresh
                librarySort[library.key]="$sorted:$ascending"
                try {libraryResume[library.key]=api.resume(library.id)}
                catch(e:CancellationException) {throw e}
                catch(_:Exception) {libraryResume[library.key]=emptyList()}
            })
        viewModelScope.launch {
            try {
                jobs.forEach {runCatching {it.join()}}
                carouselRevision[library.key]=(carouselRevision[library.key] ?: 0)+1
            } finally {carouselRefreshing=false}
        }
    }
    fun itemActions(item:MediaEntry):List<MediaAction> {
        if(sources.firstOrNull {it.id==item.sourceId}?.kind != SourceKind.EMBY) return emptyList()
        return when(item.type) {
            "Person","UserView" -> emptyList()
            "CollectionFolder","Folder","BoxSet","Season","Series" -> listOf(MediaAction.REFRESH_LIBRARY,MediaAction.REFRESH_METADATA)
            else -> if(item.isFolder) listOf(MediaAction.REFRESH_LIBRARY,MediaAction.REFRESH_METADATA)
                else listOf(MediaAction.REFRESH_METADATA,MediaAction.DELETE)
        }
    }
    fun showItemActions(item:MediaEntry) { if(itemActions(item).isNotEmpty()) actionTarget=item }
    fun dismissItemActions() { actionTarget=null;pendingDelete=null }
    fun requestDelete(item:MediaEntry) {actionTarget=null;pendingDelete=item}
    fun cancelDelete() {pendingDelete=null}
    fun confirmDelete(item:MediaEntry) {pendingDelete=null;runAction(MediaAction.DELETE,item)}
    fun runAction(action:MediaAction,item:MediaEntry) {
        actionTarget=null
        launchLoad("action:${action.name}:${item.key}",replace=true) {
            try {
                val api=app.emby(source(item.sourceId))
                when(action) {
                    MediaAction.REFRESH_METADATA -> {
                        api.refreshMetadata(item.id,false)
                        details.remove(item.key)
                        message="已请求刷新「${item.title}」的元数据；服务端完成后重新进入即可看到结果"
                    }
                    MediaAction.REFRESH_LIBRARY -> {
                        api.refreshMetadata(item.id,true)
                        message="已请求刷新「${item.title}」媒体库；扫描在服务端后台进行"
                    }
                    MediaAction.DELETE -> {
                        api.deleteItem(item.id)
                        removeFromCaches(item)
                        message="已从媒体库移除「${item.title}」"
                    }
                }
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {message=safeError(e)}
        }
    }
    private fun removeFromCaches(item:MediaEntry) {
        details.remove(item.key)
        children.remove(item.key)
        recommendations.keys.toList().forEach {key->recommendations[key]=recommendations[key].orEmpty().filterNot {it.key==item.key}}
        pages.keys.toList().forEach {key->pages[key]?.let {page->if(page.items.any {it.key==item.key})
            pages[key]=page.copy(items=page.items.filterNot {it.key==item.key},total=(page.total-1).coerceAtLeast(0))}}
        listOf(libraryLatest,libraryResume,folderPreviews,similar).forEach {map->
            map.keys.toList().forEach {key->map[key]=map[key].orEmpty().filterNot {it.key==item.key}}
        }
        feeds.keys.toList().forEach {key->feeds[key]?.let {feed->feeds[key]=feed.copy(
            resume=feed.resume.filterNot {it.key==item.key},latest=feed.latest.filterNot {it.key==item.key},
            nextUp=feed.nextUp.filterNot {it.key==item.key})}}
        heroCandidates=heroCandidates.filterNot {it.key==item.key}
    }
    /** Remote MENU key: reach settings from any page except the player activity. */
    fun openSettingsFromMenu() { if(route!=Route.Settings) navigate(Route.Settings,root=true) }
    /** Leading titles already loaded on this device, used by the search quick-pick. */
    fun knownTitles():List<String> {
        return knownEntries().map {it.title}
    }
    /** Media already loaded on this device, used for pinyin search without extra requests. */
    fun knownEntries():List<MediaEntry> {
        val out=LinkedHashMap<String,MediaEntry>()
        val active=activeSourceId
        fun add(entries:Collection<MediaEntry>) {entries.forEach {out[it.key]=it}}
        activeEmbySources().forEach {config->feeds[config.id]?.let {feed->
            add(feed.libraries); add(feed.latest); add(feed.resume); add(feed.nextUp)
        }}
        libraryLatest.filterKeys {active.isBlank() || it.startsWith("$active:")}.values.forEach {add(it)}
        pages.filterKeys {active.isBlank() || it.startsWith("$active:")||it.startsWith("search:")}.values.forEach {add(it.items)}
        personWorks.filterKeys {active.isBlank() || it.startsWith("$active:")}.values.forEach {add(it.items)}
        if(active.isBlank() || heroCandidates.all {it.sourceId==active}) add(heroCandidates)
        return out.values.toList()
    }
    /** "Up at the very top": refresh the carousel of the page the user is on. */
    fun refreshTopCarousel() {
        if(carouselRefreshing) return
        when(val current=route) {
            Route.Home -> refreshCarousel()
            is Route.Library -> refreshLibraryCarousel(current.item)
            else -> Unit
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
        activeEmbySources().forEach { config ->
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
        // Switching the browsed source re-reads that source only; other caches are kept.
        if(old.activeSourceId!=value.activeSourceId) {heroCandidates=emptyList();refresh()}
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
                // The server just added becomes the one being browsed.
                settings=settings.copy(activeSourceId=config.id)
                withContext(Dispatchers.IO) { app.store.saveSettings(settings) }
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
                if(settings.activeSourceId==id) {
                    settings=settings.copy(activeSourceId="")
                    withContext(Dispatchers.IO) {app.store.saveSettings(settings)}
                }
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
        libraryLatest.clear(); staleLatest.clear(); libraryLatestModes.clear(); folderPages.clear(); folderPreviews.clear(); similar.clear(); personWorks.clear(); mediaLibraries.clear()
        selectedVersion.clear(); selectedAudio.clear(); selectedSubtitle.clear(); selectedSubtitleTrack.clear()
        recommendations.clear(); actionTarget=null; pendingDelete=null; heroJob=null; carouselRefreshing=false
        heroCandidates=emptyList()
    }

    private fun launchLoad(key: String, replace: Boolean = false, block: suspend () -> Unit): Job {
        loads[key]?.takeIf {it.isActive}?.let {if(!replace) return it}
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
        return job
    }
}
