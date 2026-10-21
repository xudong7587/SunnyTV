package io.github.xudong7587.sunnytv.core.network

import io.github.xudong7587.sunnytv.core.model.HeaderScope
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** UGREEN LAN certificate continuity. No credentials are sent during certificate discovery. */
internal object UgreenLanTls {
    fun isLanAddress(base: String): Boolean {
        val url = base.toHttpUrl()
        if (!url.isHttps) return false
        // Literal RFC1918 IPv4 only: never resolve arbitrary domains to decide TLS trust.
        val parts = url.host.split('.').map { it.toIntOrNull() ?: return false }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 || (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168)
    }

    suspend fun inspect(base: String, client: OkHttpClient): String {
        require(isLanAddress(base))
        val capture = object : X509TrustManager {
            @Volatile var certificate: X509Certificate? = null
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                throw CertificateException("Client certificates unsupported")
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                val leaf = chain.firstOrNull() ?: throw CertificateException("Missing certificate")
                leaf.checkValidity()
                certificate = leaf
                // Deliberately abort the handshake; do not send HTTP or an account password.
                throw CertificateException("Certificate inspection only")
            }
        }
        val context = context(capture)
        val probe = client.newBuilder().sslSocketFactory(context.socketFactory, capture).build()
        return suspendCancellableCoroutine { cont ->
            val call = probe.newCall(Request.Builder().url(base).build())
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!cont.isActive) return
                    val certificate = capture.certificate
                    if (certificate == null) cont.resumeWithException(e)
                    else cont.resume(certificate.encoded.toByteString().base64())
                }
                override fun onResponse(call: Call, response: Response) {
                    response.close()
                    if (cont.isActive) cont.resumeWithException(IOException("证书检查失败"))
                }
            })
        }
    }

    fun configure(builder: OkHttpClient.Builder, scope: HeaderScope) {
        require(isLanAddress(scope.baseUrl)) { "内网证书仅适用于 HTTPS 私有 IP 地址" }
        val origin = scope.baseUrl.toHttpUrl()
        val encoded = scope.lanTlsCertificate.decodeBase64()?.toByteArray()
            ?: throw IllegalArgumentException("NAS 证书无效，请重新添加服务")
        val known = CertificateFactory.getInstance("X.509")
            .generateCertificate(encoded.inputStream()) as X509Certificate
        val system = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(null as KeyStore?)
        }.trustManagers.filterIsInstance<X509TrustManager>().single()
        fun matches(cert: X509Certificate) = cert.encoded.contentEquals(known.encoded)
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers() = system.acceptedIssuers + known
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                system.checkClientTrusted(chain, authType)
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.firstOrNull()?.let(::matches) == true) chain[0].checkValidity()
                else system.checkServerTrusted(chain, authType)
            }
        }
        val defaultVerifier = builder.build().hostnameVerifier
        builder.sslSocketFactory(context(trust).socketFactory, trust)
            .hostnameVerifier { host, session ->
                val leaf = session.peerCertificates.firstOrNull() as? X509Certificate
                val pinned = leaf?.let(::matches) == true
                when {
                    host == origin.host && session.peerPort == origin.port -> pinned
                    pinned -> false // The remembered certificate is valid for exactly one origin.
                    else -> defaultVerifier.verify(host, session)
                }
            }
    }

    private fun context(manager: X509TrustManager) = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(manager), null)
    }
}
