package io.github.xudong7587.sunnytv.core.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun OkHttpClient.bytes(request: Request, limit: Long = 4L * 1024 * 1024): ByteArray =
    suspendCancellableCoroutine { cont ->
        val call = newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val bytes = response.use { r ->
                        if (!r.isSuccessful) throw SourceException(when(r.code) {
                            401, 403 -> "认证或访问权限不足（HTTP ${r.code}）"
                            409 -> "播放资产或令牌失效（HTTP 409），请检查来源令牌或直链是否已过期"
                            416 -> "请求位置超出媒体范围（HTTP 416）"
                            else -> "服务返回 HTTP ${r.code}"
                        })
                        val body = r.body ?: throw SourceException("服务器返回空响应")
                        if (body.contentLength() > limit) throw SourceException("响应体超过安全上限")
                        val source = body.source()
                        source.request(limit + 1)
                        if (source.buffer.size > limit) throw SourceException("响应体超过安全上限")
                        source.readByteArray()
                    }
                    if (cont.isActive) cont.resume(bytes)
                } catch(e: Exception) { if(cont.isActive) cont.resumeWithException(e) }
            }
        })
    }
