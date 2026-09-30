package io.github.xudong7587.sunnytv.core.network

import io.github.xudong7587.sunnytv.core.model.HeaderScope
import kotlinx.coroutines.*
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

/** Read-only DAV update channel. Uses the same TLS, bounded redirects, scoped auth and UA as sources. */
internal class UpdateTransport(address:String,user:String,password:String) {
    private val base=HttpPolicy.serverBase(address).toHttpUrl()
    private val client=SafeHttp().scopedClient(HeaderScope(base.toString(),mapOf("Authorization" to Credentials.basic(user,password,Charsets.UTF_8))))
        .newBuilder().callTimeout(10,TimeUnit.MINUTES).build()
    suspend fun fetch(name:String,limit:Long,write:(ByteArray,Int)->Unit)=withContext(Dispatchers.IO) {
        require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*"))) {"升级文件名无效"}
        require(limit in 1..268435456) {"升级文件大小限制无效"}
        val call=client.newCall(Request.Builder().url(base.newBuilder().addPathSegment(name).build()).build())
        val cancellation=launch(Dispatchers.IO,start=CoroutineStart.UNDISPATCHED) {
            try {awaitCancellation()} finally {call.cancel()}
        }
        try {call.execute().use {response->
            require(response.isSuccessful) {"WebDAV 读取失败（HTTP ${response.code}）"}
            val body=response.body ?: error("升级文件为空")
            require(body.contentLength()<=limit) {"升级文件过大"}
            body.byteStream().use {input->val buffer=ByteArray(65536);var total=0L
                while(true) {ensureActive();val n=input.read(buffer);if(n<0) break;total+=n;require(total<=limit) {"升级文件过大"};write(buffer,n)}
            }
        }} catch(e:Exception) {ensureActive();throw e} finally {cancellation.cancel()}
    }
}
