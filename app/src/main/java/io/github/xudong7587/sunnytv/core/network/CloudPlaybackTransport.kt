package io.github.xudong7587.sunnytv.core.network

import io.github.xudong7587.sunnytv.core.model.HeaderScope
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.HttpUrl
import java.io.IOException

/** Cloud HLS never receives Emby auth; only explicitly supplied subtitle URLs may. */
object CloudPlaybackTransport {
    private class Route(val subtitle:Boolean)
    private val cdnDomains=setOf("115.com","115cdn.com","115cdn.net","anxia.com")

    internal fun permittedMedia(url:HttpUrl, playlist:HttpUrl):Boolean {
        if(url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment!=null) return false
        val sessionPath=playlist.encodedPath.substringBeforeLast('/')+"/"
        if(HttpPolicy.isScoped(url.toString(),playlist.newBuilder().encodedPath(sessionPath).query(null).build().toString())) return true
        return url.isHttps && url.port==443 && cdnDomains.any {url.host==it || url.host.endsWith(".$it")}
    }

    fun client(http:SafeHttp, playlistUrl:String, subtitleScope:HeaderScope?, subtitleUrls:List<String>):OkHttpClient {
        val playlist=playlistUrl.toHttpUrlOrNull() ?: throw IOException("云端会话地址无效")
        val permitted=subtitleUrls.mapNotNull {it.toHttpUrlOrNull()?.toString()}.toSet()
        val builder=http.playbackClient.newBuilder()
        builder.interceptors().add(0,Interceptor {chain ->
            val url=chain.request().url.toString()
            val scope=if(subtitleScope!=null && url in permitted && HttpPolicy.isScoped(url,subtitleScope.baseUrl))
                HeaderScope(url,subtitleScope.headers) else null
            chain.proceed(chain.request().newBuilder().tag(HeaderScope::class.java,scope)
                .tag(Route::class.java,Route(url in permitted)).build())
        })
        // Downstream of SafeHttp's redirect loop: check every hop before DNS/connect.
        builder.addInterceptor {chain ->
            if(chain.request().tag(Route::class.java)?.subtitle!=true && !permittedMedia(chain.request().url,playlist))
                throw IOException("115 云端媒体地址超出允许范围")
            chain.proceed(chain.request())
        }
        return builder.build()
    }
}
