package io.github.xudong7587.sunnytv.core.network

import io.github.xudong7587.sunnytv.core.model.HeaderScope
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

/** One request path for API, images, STRM and video. Redirects are bounded and auth is origin/path scoped. */
class SafeHttp {
    val client: OkHttpClient = create()
    private fun create(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
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

    fun scopedClient(scope: HeaderScope?): OkHttpClient {
        // The tag MUST be applied before the redirect interceptor executes.
        val builder = client.newBuilder()
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
