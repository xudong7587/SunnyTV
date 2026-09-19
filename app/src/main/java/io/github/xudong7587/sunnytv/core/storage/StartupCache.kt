package io.github.xudong7587.sunnytv.core.storage

import android.content.Context
import android.util.AtomicFile
import io.github.xudong7587.sunnytv.core.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Display-only, account/endpoint-scoped stale-while-revalidate snapshot. Call on Dispatchers.IO.
 * Never serialize SourceConfig, credentials, media paths, playback URLs, external links or tracks.
 * JSON whitelist avoids Java object deserialization. Each file and the entire directory are bounded.
 */
class StartupCache(context: Context) {
    data class Snapshot(val feed: HomeFeed, val shelves: Map<String, List<MediaEntry>>)
    private val directory = File(context.cacheDir, "startup-v1")
    @Volatile var generation: Long = 0
        private set
    private fun file(source: SourceConfig): File {
        val scope = "${source.id}\u0000${source.baseUrl}\u0000${source.userId}"
        val hash = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$hash.json")
    }
    @Synchronized fun read(source: SourceConfig): Snapshot? = runCatching {
        val target = file(source)
        if (!target.exists() || target.length() !in 1..MAX_FILE) return null
        val text = AtomicFile(target).openRead().use { input ->
            // File length can change; the reader is also bounded, not only the stat check.
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val n = input.read(buffer); if (n < 0) break
                total += n; if (total > MAX_FILE) return null
                out.write(buffer, 0, n)
            }
            out.toString("UTF-8")
        }
        val json = JSONObject(text)
        val age = System.currentTimeMillis() - json.getLong("saved")
        if (json.optInt("schema") != 1 || age !in 0..MAX_AGE) return null
        fun entries(name: String, limit: Int = 20) = decode(json.optJSONArray(name), source.id, limit)
        val shelves = linkedMapOf<String,List<MediaEntry>>()
        val rows = json.optJSONArray("shelves") ?: JSONArray()
        for (i in 0 until minOf(rows.length(), 8)) {
            val row = rows.optJSONObject(i) ?: continue
            val id = row.optString("id").take(200)
            if (id.isNotBlank()) shelves["${source.id}:$id"] = decode(row.optJSONArray("items"), source.id, 10)
        }
        Snapshot(HomeFeed(entries("libraries",40),entries("resume"),entries("latest"),entries("next")), shelves)
    }.getOrNull()

    @Synchronized fun write(source: SourceConfig, snapshot: Snapshot, expectedGeneration: Long) {
        if (expectedGeneration != generation) return
        runCatching {
            directory.mkdirs()
            val j = JSONObject().put("schema",1).put("saved",System.currentTimeMillis())
                .put("libraries",encode(snapshot.feed.libraries,40)).put("resume",encode(snapshot.feed.resume,20))
                .put("latest",encode(snapshot.feed.latest,20)).put("next",encode(snapshot.feed.nextUp,20))
            val rows = JSONArray()
            snapshot.shelves.entries.take(8).forEach { (key,items) ->
                if (key.startsWith("${source.id}:")) rows.put(JSONObject().put("id",key.removePrefix("${source.id}:").take(200))
                    .put("items",encode(items,10)))
            }
            j.put("shelves",rows)
            val bytes = j.toString().toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_FILE) return
            val atomic = AtomicFile(file(source))
            val stream = atomic.startWrite()
            try { stream.write(bytes);atomic.finishWrite(stream) }
            catch (e: Exception) {atomic.failWrite(stream);throw e}
            var total = directory.listFiles().orEmpty().sumOf {it.length()}
            directory.listFiles().orEmpty().sortedBy {it.lastModified()}.forEach {
                if(total > MAX_TOTAL) {val size=it.length();if(it.delete()) total-=size}
            }
        }
    }
    @Synchronized fun clear() {
        generation++ // A queued pre-clear write must not resurrect private display metadata.
        directory.listFiles().orEmpty().forEach {it.delete()}
    }
    private fun art(a: Artwork?) = a?.let {JSONObject().put("id",it.itemId.take(200))
        .put("type",it.type.take(32)).put("tag",it.tag.take(200)).put("index",it.index)}
    private fun encode(entries: List<MediaEntry>, limit: Int): JSONArray = JSONArray().also { out ->
        entries.take(limit).forEach {e ->
            out.put(JSONObject().put("id",e.id.take(200)).put("title",e.title.take(300)).put("type",e.type.take(32))
                .put("overview",e.overview.take(1200)).put("year",e.year).put("rating",e.rating.takeIf {it.isFinite()} ?: 0.0)
                .put("duration",e.durationMs).put("position",e.positionMs).put("played",e.played).put("favorite",e.favorite)
                .put("folder",e.isFolder).put("collection",e.collectionType.take(32)).put("series",e.seriesId.take(200))
                .put("season",e.season).put("episode",e.episode).put("genres",JSONArray(e.genres.take(3).map {it.take(50)}))
                .put("primary",art(e.primary)).put("thumb",art(e.thumb)).put("backdrop",art(e.backdrop))
                .put("logo",art(e.logo)).put("banner",art(e.banner)))
        }
    }
    private fun decode(array: JSONArray?, source: String, limit: Int): List<MediaEntry> = buildList {
        if(array==null) return@buildList
        for(i in 0 until minOf(array.length(),limit)) {
            val j=array.optJSONObject(i) ?: continue
            val id=j.optString("id").take(200);if(id.isBlank()) continue
            fun art(name:String):Artwork? {
                val a=j.optJSONObject(name) ?: return null
                val type=a.optString("type")
                if(type !in setOf("Primary","Thumb","Backdrop","Logo","Banner")) return null
                return Artwork(a.optString("id").take(200),type,a.optString("tag").take(200),
                    if(a.has("index")) a.optInt("index").coerceAtLeast(0) else null)
            }
            val genres=j.optJSONArray("genres")
            add(MediaEntry(id,source,j.optString("title").take(300),j.optString("type").take(32),
                overview=j.optString("overview").take(1200),year=j.optInt("year"),rating=j.optDouble("rating",0.0),
                durationMs=j.optLong("duration").coerceAtLeast(0),positionMs=j.optLong("position").coerceAtLeast(0),
                played=j.optBoolean("played"),favorite=j.optBoolean("favorite"),
                genres=(0 until minOf(genres?.length() ?: 0,3)).map {genres!!.optString(it).take(50)},
                primary=art("primary"),thumb=art("thumb"),backdrop=art("backdrop"),logo=art("logo"),banner=art("banner"),
                seriesId=j.optString("series").take(200),season=j.optInt("season"),episode=j.optInt("episode"),
                isFolder=j.optBoolean("folder"),collectionType=j.optString("collection").take(32)))
        }
    }
    companion object {
        private const val MAX_FILE=512L*1024
        private const val MAX_TOTAL=8L*1024*1024
        private const val MAX_AGE=7L*24*60*60*1000
    }
}
