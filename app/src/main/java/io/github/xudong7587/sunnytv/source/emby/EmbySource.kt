package io.github.xudong7587.sunnytv.source.emby

import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.network.*
import kotlinx.coroutines.*
import io.github.xudong7587.sunnytv.source.strm.StrmResolver
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** All Emby URL building and DTO interpretation stays out of UI. User-token access only. */
class EmbySource(val config: SourceConfig, private val http: SafeHttp, private val deviceId: String) {
    private val base = config.baseUrl.toHttpUrl()
    val scope = HeaderScope(config.baseUrl, headers(config.secret, deviceId))
    private val client = http.scopedClient(scope)
    private val fields = "Overview,Genres,PrimaryImageAspectRatio"
    fun url(path: String, query: Map<String, String> = emptyMap()): String {
        val b = base.newBuilder().addPathSegments(path.trimStart('/'))
        query.forEach { (k,v) -> b.addQueryParameter(k,v) }
        return b.build().toString()
    }
    private suspend fun get(path: String, query: Map<String,String> = emptyMap()): String =
        client.bytes(Request.Builder().url(url(path,query)).build()).toString(Charsets.UTF_8)
    private suspend fun post(path: String, body: JSONObject): String = client.bytes(
        Request.Builder().url(url(path)).post(body.toString().toRequestBody(JSON)).build()
    ).toString(Charsets.UTF_8)

    suspend fun views(): List<MediaEntry> = parsePage(get("Users/${config.userId}/Views")).items
    suspend fun item(id: String): MediaEntry = withContext(Dispatchers.Default) {parseItem(JSONObject(get("Users/${config.userId}/Items/$id")))}
    suspend fun resume(parent: String = ""): List<MediaEntry> = parsePage(get("Users/${config.userId}/Items/Resume",
        mapOf("Limit" to "16", "MediaTypes" to "Video", "Fields" to fields, "ImageTypeLimit" to "1") + optionalParent(parent))).items
    suspend fun latest(parent: String = "", limit:Int=18): List<MediaEntry> {
        val data = get("Users/${config.userId}/Items/Latest", mapOf("Limit" to limit.coerceIn(1,18).toString(), "Fields" to fields,
            "ImageTypeLimit" to "1", "GroupItems" to "true") + optionalParent(parent))
        return if(data.trimStart().startsWith("[")) parseArray(JSONArray(data)) else parsePage(data).items
    }
    suspend fun nextUp(): List<MediaEntry> = parsePage(get("Shows/NextUp", mapOf(
        "UserId" to config.userId, "Limit" to "16", "Fields" to fields, "ImageTypeLimit" to "1"))).items
    suspend fun home(): HomeFeed = supervisorScope {
        val warnings = java.util.Collections.synchronizedList(mutableListOf<String>())
        suspend fun row(name: String, f: suspend () -> List<MediaEntry>): List<MediaEntry> = try { f() }
        catch(e: CancellationException) { throw e }
        catch(_: Exception) { warnings.add("$name 暂时不可用"); emptyList() }
        val v = async { views() } // Authentication/library failures are not hidden as an empty library.
        val r = async { row("继续观看") { resume() } }
        val l = async { row("最新入库") { latest() } }
        val n = async { row("接着看") { nextUp() } }
        HomeFeed(v.await(), r.await(), l.await(), n.await(), warnings.toList())
    }
    suspend fun library(parent: String, start: Int = 0, sort: String = "DateCreated", query: String = "", favorites: Boolean = false,
        ascending:Boolean=sort=="SortName", foldersOnly:Boolean=false, limit:Int=48, mixed:Boolean=false): MediaPage {
        require(Presentation.sorts.any {it.first==sort}) {"Unsupported sort"}
        val q = mutableMapOf("Recursive" to (!foldersOnly).toString(), "StartIndex" to "${start.coerceAtLeast(0)}",
            "Limit" to limit.coerceIn(1,48).toString(), "SortBy" to sort, "SortOrder" to if(ascending) "Ascending" else "Descending",
            "Fields" to fields, "ImageTypeLimit" to "1")
        if(foldersOnly) q["IsFolder"]="true"
        else q["IncludeItemTypes"]=if(mixed) "Movie,Episode,Video" else "Movie,Series,Video"
        q.putAll(optionalParent(parent))
        if(query.isNotBlank()) q["SearchTerm"] = query
        if(favorites) q["Filters"] = "IsFavorite"
        return parsePage(get("Users/${config.userId}/Items",q))
    }
    suspend fun children(item: MediaEntry): List<MediaEntry> = when(item.type) {
        "Series" -> parsePage(get("Shows/${item.id}/Seasons", mapOf("UserId" to config.userId, "Fields" to fields))).items
        "Season" -> parsePage(get("Shows/${item.seriesId}/Episodes", mapOf("UserId" to config.userId, "SeasonId" to item.id, "Fields" to fields))).items
        else -> emptyList()
    }
    suspend fun setFavorite(item: MediaEntry, value: Boolean):MediaEntry {
        val request = Request.Builder().url(url("Users/${config.userId}/FavoriteItems/${item.id}"))
        if(value) request.post(ByteArray(0).toRequestBody(null)) else request.delete()
        return userDataResult(item,client.bytes(request.build()).toString(Charsets.UTF_8),favorite=value)
    }
    suspend fun setPlayed(item:MediaEntry,value:Boolean):MediaEntry {
        require(item.isPlayable) {"请在单集或影片页面标记已看"}
        val request=Request.Builder().url(url("Users/${config.userId}/PlayedItems/${item.id}"))
        if(value) request.post(ByteArray(0).toRequestBody(null)) else request.delete()
        return userDataResult(item,client.bytes(request.build()).toString(Charsets.UTF_8),played=value)
    }
    private fun userDataResult(item:MediaEntry,body:String,favorite:Boolean=item.favorite,played:Boolean=item.played):MediaEntry {
        val data=body.takeIf {it.isNotBlank()}?.let {JSONObject(it)}
        return item.copy(favorite=data?.optBoolean("IsFavorite",favorite) ?: favorite,
            played=data?.optBoolean("Played",played) ?: played,
            positionMs=if(data?.has("PlaybackPositionTicks")==true) data.optLong("PlaybackPositionTicks")/10_000 else item.positionMs)
    }
    suspend fun similar(id:String):List<MediaEntry> = parsePage(get("Items/$id/Similar",
        mapOf("UserId" to config.userId,"Limit" to "12","Fields" to fields))).items
    fun imageUrl(art: Artwork, width: Int): String {
        val index = art.index?.let { "/$it" }.orEmpty()
        return url("Items/${art.itemId}/Images/${art.type}$index", mapOf("tag" to art.tag,
            "MaxWidth" to width.toString(), "Quality" to "90"))
    }
    suspend fun playback(item: MediaEntry, fromStart: Boolean = false, versionId:String=""): PlaybackRequest {
        val start = if(fromStart) 0 else item.positionMs
        val info = JSONObject(post("Items/${item.id}/PlaybackInfo", JSONObject()
            .put("UserId",config.userId).put("StartTimeTicks", start * 10_000)
            .put("IsPlayback",true).put("AutoOpenLiveStream",false)
            .put("EnableDirectPlay",true).put("EnableDirectStream",true).put("EnableTranscoding",false)))
        if(info.text("ErrorCode") != null) throw SourceException("服务器没有返回可直放的媒体源")
        val sources = info.optJSONArray("MediaSources") ?: throw SourceException("没有媒体源")
        val list = (0 until sources.length()).map { sources.getJSONObject(it) }
        val candidates=if(versionId.isBlank()) list else list.filter {it.text("Id")==versionId}
        val source = candidates.firstOrNull { it.optBoolean("SupportsDirectPlay") || it.optBoolean("SupportsDirectStream") }
            ?: candidates.firstOrNull { !it.has("SupportsDirectPlay") && !it.has("SupportsDirectStream") }
            ?: throw SourceException("服务端未提供可直放/直流的媒体源；首版不自动申请转码")
        if(source.optBoolean("RequiresOpening")) throw SourceException("此媒体源需要专用打开会话，首版暂不支持")
        val sid = source.text("Id").orEmpty()
        val directPath = source.text("Path")
        val streamPath = source.text("DirectStreamUrl")
        val remote = directPath?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        // Respect server-provided HTTP source; never hand /volume/... or /strm/... to Android.
        val needsProviderHeaders = (source.optJSONObject("RequiredHttpHeaders")?.length() ?: 0) > 0
        // Provider Cookie/Referer requirements remain at the server, not on the TV.
        val direct = remote != null && source.optBoolean("SupportsDirectPlay",false) && !needsProviderHeaders
        val stable = when {
            direct -> remote!!
            streamPath != null -> absolute(streamPath)
            else -> url("Videos/${item.id}/stream", mapOf("Static" to "true", "MediaSourceId" to sid,
                "PlaySessionId" to info.text("PlaySessionId").orEmpty()))
        }
        HttpPolicy.validate(stable)
        val resolved = StrmResolver(client).resolve(stable)
        val subs = source.optJSONArray("MediaStreams") ?: JSONArray()
        val subtitles = (0 until subs.length()).map { subs.getJSONObject(it) }.filter {
            it.optString("Type") == "Subtitle" && it.optBoolean("IsExternal") && it.text("DeliveryUrl") != null
        }.mapNotNull { s ->
            val mime = when(s.optString("Codec").lowercase()) {
                "srt", "subrip" -> "application/x-subrip"; "vtt", "webvtt" -> "text/vtt"
                "ass", "ssa" -> "text/x-ssa"; else -> null
            }
            mime?.let { ExternalSubtitle(absolute(s.getString("DeliveryUrl")), it,
                s.optString("Language"), s.optString("DisplayTitle", "字幕")) }
        }
        return PlaybackRequest(config.id, resolved, item.title, start,
            MediaLogic.mime(source.optString("Container")), scope, subtitles,
            item.id, sid, info.text("PlaySessionId").orEmpty(), if(direct) "DirectPlay" else "DirectStream",item.key,
            mediaLogo=item.logo)
    }
    suspend fun report(request: PlaybackRequest, event: String, position: Long, paused: Boolean) {
        require(event in setOf("Playing", "Playing/Progress", "Playing/Stopped"))
        post("Sessions/$event", JSONObject().put("ItemId",request.embyItemId)
            .put("MediaSourceId",request.mediaSourceId).put("PlaySessionId",request.playSessionId)
            .put("PositionTicks",position.coerceAtLeast(0)*10_000).put("IsPaused",paused)
            .put("CanSeek",true).put("PlayMethod",request.playMethod)
            .put("EventName",if(paused) "Pause" else "TimeUpdate"))
    }
    private fun absolute(path: String): String =
        if(path.startsWith("http://") || path.startsWith("https://")) path
        else HttpPolicy.resolveReference(config.baseUrl, path)
    private fun optionalParent(id: String) = if(id.isBlank()) emptyMap() else mapOf("ParentId" to id)
    private suspend fun parsePage(text: String): MediaPage = withContext(Dispatchers.Default) {
        val j = JSONObject(text); val entries = parseArray(j.optJSONArray("Items") ?: JSONArray())
        MediaPage(entries,j.optInt("TotalRecordCount",entries.size))
    }
    private suspend fun parseArray(a: JSONArray) = withContext(Dispatchers.Default) { (0 until a.length()).map { parseItem(a.getJSONObject(it)) } }
    internal fun parseItem(j: JSONObject): MediaEntry {
        val id = j.getString("Id"); val tags = j.optJSONObject("ImageTags") ?: JSONObject()
        fun art(name: String): Artwork? = tags.text(name)?.let { Artwork(id,name,it) }
        val backs = j.optJSONArray("BackdropImageTags")
        val parentBacks = j.optJSONArray("ParentBackdropImageTags")
        val backdrop = if(backs != null && backs.length() > 0) Artwork(id,"Backdrop",backs.getString(0),0)
            else if(parentBacks != null && parentBacks.length() > 0 && j.text("ParentBackdropItemId") != null)
                Artwork(j.getString("ParentBackdropItemId"),"Backdrop",parentBacks.getString(0),0) else null
        val primary = art("Primary") ?: if(j.text("SeriesPrimaryImageTag") != null && j.text("SeriesId") != null)
            Artwork(j.getString("SeriesId"),"Primary",j.getString("SeriesPrimaryImageTag")) else null
        val u = j.optJSONObject("UserData") ?: JSONObject()
        val genres = j.optJSONArray("Genres") ?: JSONArray()
        return MediaEntry(id,config.id,j.optString("Name","未命名"),j.optString("Type","Video"),
            j.text("Overview").orEmpty(),j.optInt("ProductionYear"),j.optDouble("CommunityRating",0.0),
            j.optLong("RunTimeTicks")/10_000,u.optLong("PlaybackPositionTicks")/10_000,
            u.optBoolean("Played"),u.optBoolean("IsFavorite"),(0 until genres.length()).map { genres.getString(it) },
            primary,art("Thumb"),backdrop,art("Logo") ?: j.text("ParentLogoItemId")?.let { logoId ->
                j.text("ParentLogoImageTag")?.let {Artwork(logoId,"Logo",it)} },j.optString("SeriesId"),
            j.optInt("ParentIndexNumber"),j.optInt("IndexNumber"),j.text("Path").orEmpty(),j.optBoolean("IsFolder"),
            banner=art("Banner"),collectionType=j.text("CollectionType").orEmpty(),
            people=j.optJSONArray("People").objects().take(30).map {p->MediaPerson(p.optString("Id"),
                p.optString("Name"),p.optString("Role",p.optString("Type")),p.text("PrimaryImageTag")?.let {Artwork(p.optString("Id"),"Primary",it)})},
            versions=j.optJSONArray("MediaSources").objects().take(20).map {s->
                val streams=s.optJSONArray("MediaStreams").objects()
                val video=streams.firstOrNull {it.optString("Type")=="Video"}
                MediaVersion(s.optString("Id"),s.optString("Name","默认版本"),video?.optInt("Width") ?: 0,
                    video?.optInt("Height") ?: 0,video?.text("VideoRange") ?: "",s.optLong("Size"),s.optLong("Bitrate"),
                    s.optString("Container"),streams.map {t->MediaTrack(t.optInt("Index"),t.optString("Type"),
                        t.optString("Language"),t.optString("DisplayTitle",t.optString("Title",t.optString("Codec"))),t.optString("Codec"))})},
            officialRating=j.text("OfficialRating").orEmpty(),externalLinks=j.optJSONArray("ExternalUrls").objects().mapNotNull {l->
                val link=l.text("Url") ?: return@mapNotNull null
                if(link.startsWith("https://")) MediaLink(l.optString("Name","更多信息"),link) else null})
    }
    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private fun JSONArray?.objects():List<JSONObject> = if(this==null) emptyList() else
            (0 until length()).mapNotNull {optJSONObject(it)}
        private fun JSONObject.text(name: String): String? = if(isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
        fun headers(token: String, device: String): Map<String,String> = buildMap {
            put("X-Emby-Authorization","MediaBrowser Client=\"SunnyTV\", Device=\"Android TV\", DeviceId=\"$device\", Version=\"0.1.0\"")
            if(token.isNotBlank()) put("X-Emby-Token",token)
        }
        suspend fun login(base: String, name: String, username: String, password: String, http: SafeHttp, deviceId: String): SourceConfig {
            val root = HttpPolicy.serverBase(base)
            val client = http.scopedClient(HeaderScope(root,headers("",deviceId)))
            val request = Request.Builder().url(root.toHttpUrl().newBuilder().addPathSegments("Users/AuthenticateByName").build())
                .post(JSONObject().put("Username",username).put("Pw",password).toString().toRequestBody(JSON)).build()
            val j = JSONObject(client.bytes(request).toString(Charsets.UTF_8))
            return SourceConfig(UUID.randomUUID().toString(),SourceKind.EMBY,name.ifBlank { "Emby" },root,
                j.getJSONObject("User").getString("Id"),username,j.getString("AccessToken"))
        }
    }
}
