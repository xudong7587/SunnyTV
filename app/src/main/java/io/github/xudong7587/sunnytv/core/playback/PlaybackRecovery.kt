package io.github.xudong7587.sunnytv.core.playback

import io.github.xudong7587.sunnytv.core.network.HttpPolicy

/**
 * First-frame recovery and host-only route labels.
 *
 * Pure JVM policy on purpose: no Media3 types, so the standalone contract suite covers the wording
 * and the retry budget without an Android SDK. Media3 never retries by itself here — the player asks
 * this object whether one additional attempt is allowed.
 */
object PlaybackRecovery {
    /** The initial attempt plus exactly one automatic retry; a second failure stays fatal. */
    const val MAX_AUTO_RETRIES = 1
    const val RETRY_DELAY_MS = 1_500L
    const val RETRY_NOTICE = "网络读取超时，正在自动重试一次…"
    const val FALLBACK_NOTICE = "直连媒体源打不开，正在改用 Emby 服务端读取…"

    // PlaybackException codes: 2001 network connection failed, 2002 network connection timeout.
    private val RETRYABLE_CODES = setOf(2001, 2002)
    // 2003 content type, 2004 bad HTTP status, 2005 not found, 2006 permissions, 2007 cleartext,
    // 2008 read position, 1004 failed runtime check: retrying the same request cannot fix these.
    private val NON_RETRYABLE_CODES = setOf(1004, 2003, 2004, 2005, 2006, 2007, 2008)
    private val RETRYABLE_CAUSES = setOf("SocketTimeoutException", "ConnectException", "InterruptedIOException")
    private val NON_RETRYABLE_CAUSES = setOf("UnknownHostException", "SSLException", "SSLHandshakeException",
        "SSLPeerUnverifiedException", "FileNotFoundException", "InvalidResponseCodeException")
    // A failed TLS handshake or DNS lookup is identical through Emby, so the fallback would waste time.
    private val FALLBACK_BLOCKING_CAUSES = setOf("UnknownHostException", "SSLException", "SSLHandshakeException",
        "SSLPeerUnverifiedException")

    /**
     * True only for a transient transport failure before the first frame, and only while this player
     * instance has not spent its single automatic retry yet.
     */
    fun shouldRetry(attemptsMade: Int, errorCode: Int, rendered: Boolean, causeNames: List<String>): Boolean {
        if (rendered) return false
        if (attemptsMade >= MAX_AUTO_RETRIES) return false
        if (causeNames.any { it in NON_RETRYABLE_CAUSES }) return false
        if (errorCode in NON_RETRYABLE_CODES) return false
        return errorCode in RETRYABLE_CODES || causeNames.any { it in RETRYABLE_CAUSES }
    }

    /**
     * True when the failure is about *reaching* the media source rather than about its bytes, so
     * asking Emby to read the same item server-side is worth one attempt. Decoding, parsing and
     * container errors are excluded: the server would hand the television the same bytes.
     */
    fun shouldUseServerFallback(errorCode: Int, causeNames: List<String>): Boolean {
        if (causeNames.any { it in FALLBACK_BLOCKING_CAUSES }) return false
        if (errorCode in NON_RETRYABLE_CODES && errorCode != 2004) return false
        return errorCode in setOf(2001, 2002, 2004) ||
            causeNames.any { it in RETRYABLE_CAUSES || it == "InvalidResponseCodeException" || it == "EOFException" }
    }

    /**
     * Which half of the HTTP exchange failed. Media3's HttpDataSourceException uses
     * TYPE_OPEN=1 (waiting for the response) and TYPE_READ=2 (streaming the body); a wrapped
     * ExecutionException always means the asynchronous response call itself failed.
     */
    fun stageHint(httpType: Int?, wrappedResponseCall: Boolean): String? = when {
        httpType == 1 -> "等待响应（连接或首字节）"
        httpType == 2 -> "读取数据流"
        wrappedResponseCall -> "等待响应（连接或首字节）"
        else -> null
    }

    /**
     * Host-only route label: "host:port（Emby 本机）" when the player talks to the configured server
     * itself, otherwise the request went straight to the media source. Never includes path or query.
     */
    fun routeLabel(mediaUrl: String?, baseUrl: String?): String? {
        val media = HttpPolicy.hostLabel(mediaUrl) ?: return null
        val base = HttpPolicy.hostLabel(baseUrl)
        return when {
            base == null -> "$media（独立媒体来源）"
            base == media -> "$media（Emby 本机）"
            else -> "$media（直连媒体源，不是 Emby）"
        }
    }

    /** Wording for an HTTP status the media source returned, so the pure tests cover it. */
    fun statusReason(code: Int): String = when (code) {
        401, 403 -> "媒体源拒绝读取（HTTP $code），请检查当前用户的播放权限。"
        404 -> "媒体地址不存在（HTTP 404），请确认来源端仍能访问该文件。"
        409 -> "媒体源拒绝播放（HTTP 409）：直链或令牌通常已失效，请在来源端（网盘 / Alist / MP）刷新该条目的 STRM。"
        416 -> "服务器拒绝此播放位置（HTTP 416），可以尝试从头播放。"
        else -> "媒体服务返回 HTTP $code。"
    }
}
