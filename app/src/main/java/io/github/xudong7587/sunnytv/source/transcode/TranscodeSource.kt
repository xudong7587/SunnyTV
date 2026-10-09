package io.github.xudong7587.sunnytv.source.transcode

import io.github.xudong7587.sunnytv.core.network.HttpPolicy
import io.github.xudong7587.sunnytv.core.network.SafeHttp
import io.github.xudong7587.sunnytv.core.network.SourceException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Only the pre-302 MediaIndex entry is recognized; never infer an API from a CDN URL. */
class TranscodeSource private constructor(private val http: SafeHttp, private val origin: HttpUrl, private val assetToken: String) {
    data class Session(val id: String, val token: String, val url: String, val startMs: Long, val durationMs: Long, val vod: Boolean = false, val sourceBitrate: Long = 0)
    companion object {
        val qualities = listOf("original" to "原画", "4k" to "4K / 2160P", "1440p" to "2K / 1440P", "1080p" to "1K / 1080P", "720p" to "0.75K / 720P")
        private val bitrateOptions = mapOf("4k" to listOf(20,10), "1440p" to listOf(10,6), "1080p" to listOf(8,4), "720p" to listOf(4,2))
        val qualityOptions = qualities + qualities.drop(1).flatMap { (id,label) -> bitrateOptions.getValue(id).map { rate -> "$id@$rate" to "$label · $rate Mbps" } }
        fun qualityLabel(id: String): String = qualityOptions.firstOrNull { it.first == id }?.second ?: "原画"
        val menuQualityOptions = qualityOptions.filter { it.first == "original" || '@' in it.first }
        fun selectedQualityBadge(id: String): String = when(id.substringBefore('@')) {
            "4k" -> "4K"
            "1440p" -> "2K"
            "1080p" -> "1080P"
            "720p" -> "720P"
            else -> "原片"
        }
        fun retryPendingSegment(httpCode: Int?, errorCount: Int, isSegment: Boolean): Boolean =
            httpCode == 504 && errorCount in 1..2 && isSegment
        fun resolutionBadge(width: Int, height: Int): String = when(maxOf(width,height)) {
            in 7680..Int.MAX_VALUE -> "8K"
            in 3840..7679 -> "4K"
            in 2560..3839 -> "2K"
            in 1920..2559 -> "1K"
            in 1280..1919 -> "0.75K"
            in 1..1279 -> "SD"
            else -> "HD"
        }
        fun from(http: SafeHttp, stableUrl: String, apiOriginOverride: String = ""): TranscodeSource? {
            val url = stableUrl.toHttpUrlOrNull() ?: return null
            if (url.query != null || url.fragment != null || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
            val match = Regex("^/api/play/([A-Za-z0-9_-]{1,512})$").matchEntire(url.encodedPath) ?: return null
            runCatching { HttpPolicy.validate(stableUrl) }.getOrElse { return null }
            val origin = if(apiOriginOverride.isBlank()) url.newBuilder().encodedPath("/").build() else {
                val override = apiOriginOverride.toHttpUrlOrNull() ?: return null
                if(override.encodedPath!="/" || override.query!=null || override.fragment!=null || override.username.isNotEmpty() || override.password.isNotEmpty()) return null
                runCatching {HttpPolicy.validate(apiOriginOverride)}.getOrElse {return null}
                override
            }
            return TranscodeSource(http, origin, match.groupValues[1])
        }
    }
    private fun endpoint(path: String): HttpUrl = origin.newBuilder().encodedPath("/api/transcode/$path").build()
    // No Emby/CD2 credential scope is attached to these requests.
    private val client = http.playbackClient.newBuilder().callTimeout(40, TimeUnit.SECONDS).build()
    private suspend fun request(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(SourceException("转码服务连接失败"))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!it.isSuccessful) {
                            val detail = runCatching { JSONObject(it.body?.string().orEmpty()).optString("detail") }.getOrDefault("")
                            val known = setOf("当前转码器不支持此 HDR 视频，请使用原画", "暂不支持此杜比视界视频的色调映射，请使用原画",
                                "转码器不支持此视频编码，请使用原画", "服务器 GPU 不支持此视频解码，请使用原画", "转码器暂不支持保留此字幕格式")
                            throw SourceException(if(detail in known) detail else when(it.code) {
                            429 -> "转码服务正在使用中，请稍后重试"
                            403 -> "此播放链接的授权已失效"
                            else -> "转码服务暂时不可用"
                            })
                        }
                        val text = it.body?.string().orEmpty()
                        if (continuation.isActive) continuation.resume(text)
                    } catch (e: SourceException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    } catch (_: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(SourceException("转码请求未完成"))
                    }
                }
            }
        })
    }
    suspend fun capabilities(): List<String> {
        val result = JSONObject(request(Request.Builder().url(endpoint("capabilities")).build()))
        if (result.optString("protocol") != "mediaindex-transcode-v1" || !result.optBoolean("available")) return emptyList()
        val profiles = result.optJSONArray("profiles") ?: return emptyList()
        val available = (0 until profiles.length()).map { profiles.getString(it) }.filter { it in qualities.map { q -> q.first } }
        val bitrates = result.optJSONObject("bitrates")
        return available + available.flatMap { profile ->
            val advertised = bitrates?.optJSONArray(profile)
            val supported = advertised?.let { array -> (0 until array.length()).map { array.optInt(it) }.toSet() }.orEmpty()
            bitrateOptions[profile].orEmpty().filter { it * 1000000 in supported }.map { "$profile@$it" }
        }
    }
    suspend fun create(profile: String, startMs: Long): Session {
        require(profile in qualityOptions.map { it.first } && profile != "original")
        val resolution = profile.substringBefore('@')
        val body = JSONObject().put("assetToken", assetToken).put("profile", resolution).put("startPositionMs", startMs).put("delivery","vod")
        if ('@' in profile) body.put("videoBitrate", profile.substringAfter('@').toInt() * 1000000)
        val result = JSONObject(request(Request.Builder().url(endpoint("sessions"))
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()))
        val id = result.getString("sessionId")
        val token = result.getString("sessionToken")
        require(Regex("[0-9a-f]{32}").matches(id) && Regex("[A-Za-z0-9_-]{32,128}").matches(token))
        val playlist = origin.resolve(result.getString("playlistUrl")) ?: throw SourceException("转码返回地址无效")
        require(playlist.scheme == origin.scheme && playlist.host == origin.host && playlist.port == origin.port)
        require(playlist.username.isEmpty() && playlist.password.isEmpty() && playlist.fragment==null)
        require(playlist.encodedPath == "/api/transcode/sessions/$id/index.m3u8" && playlist.queryParameter("st") == token)
        require(playlist.queryParameterNames==setOf("st") && playlist.queryParameterValues("st").size==1)
        val duration = result.getLong("durationMs")
        val start = result.getLong("startPositionMs")
        require(duration > 0 && start == startMs && start < duration)
        return Session(id, token, playlist.toString(), start, duration, result.optString("seekMode")=="hls-vod", result.optLong("sourceBitrate").coerceAtLeast(0))
    }
    suspend fun control(session: Session, stop: Boolean = false) {
        val url = endpoint("sessions/${session.id}").newBuilder().addQueryParameter("st", session.token).build()
        request(Request.Builder().url(url).apply { if(stop) delete() }.build())
    }
    suspend fun pause(session: Session) {
        val url = endpoint("sessions/${session.id}/pause").newBuilder().addQueryParameter("st", session.token).build()
        request(Request.Builder().url(url).post("".toRequestBody()).build())
        val body=JSONObject().put("assetToken",assetToken).toString().toRequestBody("application/json".toMediaType())
        request(Request.Builder().url(endpoint("buffer/prefetch")).post(body).build())
    }
    suspend fun bufferNotice(session:Session,positionMs:Long):String {
        if(session.sourceBitrate<=0) return ""
        val body=JSONObject().put("assetToken",assetToken).put("sourceBitrate",session.sourceBitrate)
            .put("positionSeconds",positionMs.coerceAtLeast(0)/1000.0)
            .put("remainingSeconds",((session.durationMs-positionMs).coerceAtLeast(0)/1000.0))
        val result=JSONObject(request(Request.Builder().url(endpoint("buffer/status")).post(body.toString().toRequestBody("application/json".toMediaType())).build()))
        if(result.optString("state")!="source_bandwidth_limited") return ""
        val wait=result.optLong("estimatedWaitSeconds")
        val seconds=result.optDouble("estimatedContinuousSeconds").toLong()
        return "115 读取速度低于原片码率；原片缓存粗估支持约 $seconds 秒播放。" +
            (if(wait in 1..120 && result.optLong("additionalBufferBytes")<=64*1024*1024) "可暂停约 ${((wait+59)/60)} 分钟继续缓存。" else "暂停可增加有限缓存，也可选择较小的源文件；降低转码码率主要节省客户端流量。")
    }
}
