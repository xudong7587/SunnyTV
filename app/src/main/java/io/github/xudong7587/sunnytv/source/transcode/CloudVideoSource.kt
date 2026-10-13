package io.github.xudong7587.sunnytv.source.transcode

import io.github.xudong7587.sunnytv.core.network.SafeHttp
import io.github.xudong7587.sunnytv.core.network.SourceException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Asset-scoped cloud HLS; no provider cookies or FFmpeg session semantics. */
class CloudVideoSource internal constructor(http: SafeHttp, private val origin: HttpUrl, private val assetToken: String) {
    data class Variant(val id: String, val width: Int, val height: Int, val bandwidth: Long) {
        val badge: String get() = when(maxOf(width,height)) {
            in 3840..Int.MAX_VALUE -> "4K"
            in 2560..3839 -> "2K"
            in 1920..2559 -> "1080P"
            in 1280..1919 -> "720P"
            else -> "${width}×${height}"
        }
        val label: String get() = "115 云端 · $badge · ${String.format(java.util.Locale.ROOT,"%.1f",bandwidth/1000000.0)} Mbps"
        val qualityId: String get() = "115:$id"
    }
    data class Session(val id: String, val token: String, val url: String, val variant: Variant, val delivery: String)
    private val client = http.playbackClient.newBuilder().callTimeout(60,TimeUnit.SECONDS).build()
    private fun endpoint(path: String) = origin.newBuilder().encodedPath("/api/cloud-video/$path").build()

    private suspend fun call(builder: Request.Builder): String = suspendCancellableCoroutine { continuation ->
        val call=client.newCall(builder.build())
        continuation.invokeOnCancellation {call.cancel()}
        call.enqueue(object:Callback {
            override fun onFailure(call:Call,e:IOException) {
                if(continuation.isActive) continuation.resumeWithException(SourceException("115 云端服务连接失败"))
            }
            override fun onResponse(call:Call,response:Response) {
                response.use {
                    try {
                        if(!it.isSuccessful) throw SourceException(when(it.code) {
                            401 -> "115 登录已过期，请在 MediaIndex 重新扫码登录"
                            404,405 -> "此 MediaIndex 尚未提供 115 云端清晰度"
                            403 -> "云端播放授权已失效，请重新打开影片"
                            409 -> "115 暂无可用云端流，请检查登录态、权限或转码状态"
                            429 -> "云端播放服务繁忙，请稍后重试"
                            else -> "115 云端请求失败（HTTP ${it.code}）"
                        })
                        val body=it.body ?: throw SourceException("云端服务响应为空")
                        val bytes=body.byteStream().use {stream ->
                            val output=java.io.ByteArrayOutputStream()
                            val buffer=ByteArray(4096)
                            while(true) {
                                val count=stream.read(buffer)
                                if(count<0) break
                                if(output.size()+count>65536) throw SourceException("云端服务响应过大")
                                output.write(buffer,0,count)
                            }
                            output.toByteArray()
                        }
                        if(continuation.isActive) continuation.resume(bytes.toString(Charsets.UTF_8))
                    } catch(e:Exception) {
                        if(continuation.isActive) continuation.resumeWithException(if(e is SourceException) e else SourceException("云端服务响应无效"))
                    }
                }
            }
        })
    }
    private suspend fun post(path:String,body:JSONObject):JSONObject = JSONObject(call(Request.Builder().url(endpoint(path))
        .post(body.toString().toRequestBody("application/json".toMediaType())))).also {
            require(it.optString("protocol")=="mediaindex-cloud-video-v1")
        }
    private fun variant(json:JSONObject):Variant {
        val id=json.getString("id")
        val width=json.getInt("width");val height=json.getInt("height");val bandwidth=json.getLong("bandwidth")
        require(Regex("[0-9a-f]{32}").matches(id) && width in 1..16384 && height in 1..16384 && bandwidth in 1..1000000000L)
        return Variant(id,width,height,bandwidth)
    }
    suspend fun discover():List<Variant> {
        val array=post("discover",JSONObject().put("assetToken",assetToken)).getJSONArray("variants")
        require(array.length() in 1..32)
        return (0 until array.length()).map {variant(array.getJSONObject(it))}.also {list -> require(list.distinctBy {it.id}.size==list.size)}
    }
    suspend fun create(id:String, delivery:String="direct"):Session {
        require(Regex("[0-9a-f]{32}").matches(id))
        require(delivery in setOf("direct","proxy"))
        val result=post("sessions",JSONObject().put("assetToken",assetToken).put("variantId",id).put("delivery",delivery))
        val sid=result.getString("sessionId");val token=result.getString("sessionToken")
        require(Regex("[0-9a-f]{32}").matches(sid) && Regex("[A-Za-z0-9_-]{32,128}").matches(token))
        val url=origin.resolve(result.getString("playlistUrl")) ?: throw SourceException("云端播放地址无效")
        require(url.scheme==origin.scheme && url.host==origin.host && url.port==origin.port && url.username.isEmpty() && url.password.isEmpty() && url.fragment==null)
        require(url.encodedPath=="/api/cloud-video/sessions/$sid/index.m3u8" && url.queryParameterNames==setOf("st") && url.queryParameterValues("st")==listOf(token))
        require(result.optString("seekMode")=="hls-vod" && result.optString("delivery")==delivery)
        val selected=variant(result.getJSONObject("variant"));require(selected.id==id)
        return Session(sid,token,url.toString(),selected,delivery)
    }
    suspend fun stop(session:Session) {
        call(Request.Builder().url(endpoint("sessions/${session.id}").newBuilder().addQueryParameter("st",session.token).build()).delete())
    }
}
