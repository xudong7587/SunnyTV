package io.github.xudong7587.sunnytv.core.network

import io.github.xudong7587.sunnytv.core.model.HeaderScope
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

/** One request path for API, images, STRM and video. Redirects are bounded and auth is origin/path scoped. */
class SafeHttp {
    /** API, image and STRM text: a LAN server that stays silent this long is treated as down. */
    val client: OkHttpClient = create(HttpPolicy.CONNECT_TIMEOUT_SECONDS, HttpPolicy.READ_TIMEOUT_SECONDS)
    /**
     * Video transport. Same redirect handling, header scoping, TLS policy and user agent as [client];
     * only the connect/read budget is larger, because a cloud-drive or STRM source may need to resolve
     * a provider link before it can answer at all. Never used for API, image or STRM text requests.
     */
    val playbackClient: OkHttpClient = create(HttpPolicy.PLAYBACK_CONNECT_TIMEOUT_SECONDS, HttpPolicy.PLAYBACK_READ_TIMEOUT_SECONDS)
    private fun create(connectSeconds: Int, readSeconds: Int): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectSeconds.toLong(), TimeUnit.SECONDS).readTimeout(readSeconds.toLong(), TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS).retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false)
        .addInterceptor { chain ->
            // Applied inside executeRedirects; adding per-scope tags is outermost (see scopedClient below).
            executeRedirects(chain)
        }.build()

    private fun executeRedirects(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val scope = original.tag(HeaderScope::class.java)
        var url = original.url.toString()
        val seen = linkedSetOf<String>()
        var count = 0
        while (true) {
            try { HttpPolicy.validate(url) } catch (e: IllegalArgumentException) { throw IOException(e.message) }
            val b = original.newBuilder().url(url)
            // Remove all previously inherited secrets, then add only this origin's credentials.
            original.headers.names().forEach { name ->
                if (HttpPolicy.cleanHeaders(mapOf(name to "x")).isEmpty()) b.removeHeader(name)
            }
            if (scope != null && HttpPolicy.isScoped(url, scope.baseUrl)) {
                scope.headers.forEach { (key, value) -> b.header(key, value) }
            }
            b.header("User-Agent", HttpPolicy.USER_AGENT)
            val response = chain.proceed(b.build())
            if (response.code !in setOf(301, 302, 303, 307, 308)) return response
            val location = response.header("Location") ?: return response
            response.close()
            if (original.method !in setOf("GET", "HEAD", "PROPFIND")) {
                throw IOException("服务器对写请求进行了跳转，请直接填写最终服务地址")
            }
            val target = try { HttpPolicy.redirect(url, location, seen, count) }
            catch (e: IllegalArgumentException) { throw IOException(e.message) }
            // Never replay WebDAV request bodies to an unrelated origin.
            if (original.method == "PROPFIND" && HttpPolicy.origin(HttpPolicy.validate(url)) != HttpPolicy.origin(HttpPolicy.validate(target))) {
                throw IOException("已阻止跨站 WebDAV 跳转")
            }
            seen += url; url = target; count++
        }
    }

    fun scopedClient(scope: HeaderScope?, playback: Boolean = false): OkHttpClient {
        // The tag MUST be applied before the redirect interceptor executes.
        val builder = (if (playback) playbackClient else client).newBuilder()
        builder.interceptors().add(0, Interceptor { chain ->
            chain.proceed(chain.request().newBuilder().tag(HeaderScope::class.java, scope).build())
        })
        return builder.build()
    }
}

class SourceException(message: String) : IOException(message)
fun safeError(error: Throwable): String = when (error) {
    is SourceException, is IllegalArgumentException -> error.message ?: "请求失败"
    is java.net.SocketTimeoutException -> "连接超时，请检查电视与服务的网络连接"
    is javax.net.ssl.SSLException -> "TLS 证书验证失败；不会跳过证书校验"
    else -> "连接或读取失败，请检查服务地址与权限（诊断不记录凭据）"
}
