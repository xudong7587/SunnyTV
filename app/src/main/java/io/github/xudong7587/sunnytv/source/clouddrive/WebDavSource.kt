package io.github.xudong7587.sunnytv.source.clouddrive

import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.network.*
import io.github.xudong7587.sunnytv.source.strm.StrmResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest

/** CD2's real WebDAV endpoint, NOT its web admin port. All file operations are read-only. */
class WebDavSource(val config: SourceConfig, private val http: SafeHttp) {
    val scope = HeaderScope(config.baseUrl,mapOf("Authorization" to Credentials.basic(config.username,config.secret)))
    val client = http.scopedClient(scope)
    suspend fun list(directory: String = config.baseUrl): List<MediaEntry> = withContext(Dispatchers.IO) {
        require(HttpPolicy.isScoped(directory,config.baseUrl)) { "目录超出已配置 WebDAV 根目录" }
        val body = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:displayname/><d:resourcetype/><d:getcontentlength/><d:getcontenttype/></d:prop></d:propfind>"""
        val data = client.bytes(Request.Builder().url(directory).header("Depth","1")
            .method("PROPFIND",body.toRequestBody("application/xml; charset=utf-8".toMediaType())).build(),8L*1024*1024)
        parse(data,directory)
    }
    private fun parse(bytes: ByteArray, directory: String): List<MediaEntry> =
        DavXmlParser.parse(bytes, directory, config.baseUrl).mapNotNull { node ->
            val ext = node.name.substringAfterLast('.', "").lowercase()
            if (!node.directory && ext !in VIDEO_EXTS && ext != "strm") return@mapNotNull null
            MediaEntry(hash(node.url), config.id, node.name, if (node.directory) "Folder" else "File",
                overview = if (node.directory) "CloudDrive2 文件夹" else "CloudDrive2 · ${ext.uppercase()}",
                path = node.url, isFolder = node.directory)
        }.sortedWith(compareBy<MediaEntry> { !it.isFolder }.thenBy { it.title.lowercase() })

    suspend fun playback(entry: MediaEntry, position: Long = 0): PlaybackRequest {
        val target = StrmResolver(client).resolve(entry.path)
        // scope auth is only attached to CD2 URLs, never to the STRM's remote target.
        return PlaybackRequest(config.id,target,entry.title,position,
            MediaLogic.mime(target.toHttpUrl().pathSegments.last().substringAfterLast('.',"")),scope,
            localKey=entry.key)
    }
    companion object {
        private val VIDEO_EXTS = setOf("mp4","mkv","mov","m4v","ts","m2ts","webm","avi","mpg","mpeg","m3u8","mpd")
        private fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
