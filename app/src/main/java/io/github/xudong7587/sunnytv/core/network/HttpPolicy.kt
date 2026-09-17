package io.github.xudong7587.sunnytv.core.network

import java.net.URI
import java.util.Locale

/** Pure JVM policy: tested without Android. Never decode/re-encode signed query strings. */
object HttpPolicy {
    const val USER_AGENT = "SunnyTV/0.1.0 (Android TV; Media3)"
    const val MAX_REDIRECTS = 8
    private val sensitive = setOf("authorization", "proxy-authorization", "cookie", "cookie2", "x-emby-token", "x-emby-authorization", "x-mediabrowser-token")

    fun validate(raw: String): URI {
        require(raw.isNotBlank() && raw.none { it.code < 32 || it.code == 127 }) { "地址包含空白或控制字符" }
        val uri = try { URI(raw) } catch (_: Exception) { throw IllegalArgumentException("地址格式无效，请将空格编码为 %20") }
        require(uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https")) { "仅支持 HTTP / HTTPS 地址" }
        require(!uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.port in -1..65535) { "请在单独的账号栏填写凭据" }
        require(uri.rawFragment == null) { "地址不能包含未编码的 #；文件名中的 # 应写成 %23" }
        return uri
    }
    fun origin(uri: URI): String {
        val scheme = uri.scheme.lowercase(Locale.ROOT)
        val port = if (uri.port >= 0) uri.port else if (scheme == "https") 443 else 80
        return "$scheme://${uri.host.lowercase(Locale.ROOT)}:$port"
    }
    fun isScoped(target: String, base: String): Boolean = try {
        val t = validate(target); val b = validate(base)
        val safePaths = !hasAmbiguousSegments(t.rawPath.orEmpty()) && !hasAmbiguousSegments(b.rawPath.orEmpty())
        val p = b.rawPath.orEmpty().let { if (it.endsWith('/')) it else "$it/" }
        safePaths && origin(t) == origin(b) && (t.rawPath == b.rawPath || t.rawPath.orEmpty().startsWith(p))
    } catch (_: IllegalArgumentException) { false }

    fun cleanHeaders(headers: Map<String, String>): Map<String, String> =
        headers.filterKeys { it.lowercase(Locale.ROOT) !in sensitive }

    fun redirect(from: String, location: String, visited: Set<String>, count: Int): String {
        require(count < MAX_REDIRECTS) { "重定向次数超过上限" }
        val a = validate(from)
        val target = a.resolve(location).toString()
        val b = validate(target)
        require(!(a.scheme.equals("https", true) && b.scheme.equals("http", true))) { "已阻止 HTTPS 降级跳转" }
        require(target !in visited && target != from) { "检测到重定向循环" }
        return target
    }
    /** Resolve URI references without globally decoding signed paths or query strings. */
    fun resolveReference(base: String, reference: String): String {
        val result = validate(base).resolve(reference).toString()
        validate(result)
        return result
    }

    private fun hasAmbiguousSegments(path: String): Boolean {
        // Decode only for this security comparison, never mutate the URL sent on the wire.
        var checked = path
        repeat(4) {
            checked = checked.replace(Regex("%25", RegexOption.IGNORE_CASE), "%")
                .replace(Regex("%2e", RegexOption.IGNORE_CASE), ".")
                .replace(Regex("%2f", RegexOption.IGNORE_CASE), "/")
                .replace(Regex("%5c", RegexOption.IGNORE_CASE), "\\")
        }
        if (Regex("%25", RegexOption.IGNORE_CASE).containsMatchIn(checked)) return true
        return checked.contains('\\') || checked.split('/').any { it == "." || it == ".." }
    }

    fun serverBase(raw: String): String {
        val u = validate(raw.trim())
        require(u.rawQuery == null) { "服务器地址不能包含查询参数" }
        require(!hasAmbiguousSegments(u.rawPath.orEmpty())) { "服务器根路径不能包含歧义的点路径或编码分隔符" }
        return u.toString().trimEnd('/') + "/"
    }
}
