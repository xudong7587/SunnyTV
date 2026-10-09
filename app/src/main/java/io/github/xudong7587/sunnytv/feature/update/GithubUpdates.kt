package io.github.xudong7587.sunnytv.feature.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.xudong7587.sunnytv.core.network.UpdateTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal object GithubUpdates {
    const val PAGE = "https://github.com/xudong7587/SunnyTV/releases"
    fun allowedPage(value:String):Boolean {
        val url=value.toHttpUrlOrNull() ?: return false
        return url.scheme=="https" && url.host=="github.com" && url.port==443 && url.username.isEmpty() && url.password.isEmpty() &&
            (url.encodedPath=="/xudong7587/SunnyTV/releases" || url.encodedPath.startsWith("/xudong7587/SunnyTV/releases/tag/"))
    }
    suspend fun download(context:Context,selected:String):File=withContext(Dispatchers.IO) {
        val selectedUrl=selected.toHttpUrlOrNull() ?: error("下载地址无效")
        require(selectedUrl.scheme=="https" && selectedUrl.host=="github.com" && selectedUrl.port==443 && selectedUrl.username.isEmpty() && selectedUrl.password.isEmpty() && selectedUrl.query==null && selectedUrl.fragment==null)
        val metadata=ByteArrayOutputStream()
        UpdateTransport("https://api.github.com/repos/xudong7587/SunnyTV/","","").fetch("releases",1048576) {bytes,count->metadata.write(bytes,0,count)}
        val releases=JSONArray(metadata.toString("UTF-8"))
        var expected="";var size=0L
        for(index in 0 until releases.length()) {
            val release=releases.getJSONObject(index)
            if(release.optBoolean("draft")) continue
            val assets=release.optJSONArray("assets") ?: continue
            for(assetIndex in 0 until assets.length()) {
                val asset=assets.getJSONObject(assetIndex)
                if(asset.optString("browser_download_url")==selected) {
                    expected=asset.optString("digest").removePrefix("sha256:")
                    size=asset.optLong("size")
                }
            }
        }
        require(size in 1..268435456) {"请选择官方发行页中的 APK"}
        val name=selectedUrl.pathSegments.last()
        require(name.endsWith(".apk") && name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*")))
        val base=selectedUrl.newBuilder().removePathSegment(selectedUrl.pathSegments.lastIndex).addPathSegment("").build().toString()
        val transport=UpdateTransport(base,"","")
        if(!expected.matches(Regex("[a-fA-F0-9]{64}"))) {
            val checksums=ByteArrayOutputStream()
            transport.fetch("SHA256SUMS.txt",65536) {bytes,count->checksums.write(bytes,0,count)}
            expected=checksums.toString("UTF-8").lineSequence().map {it.trim().split(Regex("\\s+"),limit=2)}
                .firstOrNull {it.size==2 && it[1].removePrefix("*")==name}?.first().orEmpty()
        }
        require(expected.matches(Regex("[a-fA-F0-9]{64}"))) {"官方发行缺少下载校验值"}
        val directory=File(context.cacheDir,"updates").apply {mkdirs()}
        val partial=File(directory,"github-${UUID.randomUUID()}.part.apk")
        try {
            val digest=MessageDigest.getInstance("SHA-256")
            partial.outputStream().use {output->transport.fetch(name,size) {bytes,count->output.write(bytes,0,count);digest.update(bytes,0,count)}}
            require(partial.length()==size && digest.digest().joinToString("") {"%02x".format(it)}.equals(expected,true)) {"升级包校验失败"}
            @Suppress("DEPRECATION")
            val archive=context.packageManager.getPackageArchiveInfo(partial.path,PackageManager.GET_SIGNATURES) ?: error("APK 无效")
            @Suppress("DEPRECATION")
            val target=if(Build.VERSION.SDK_INT>=28) archive.longVersionCode else archive.versionCode.toLong()
            @Suppress("DEPRECATION")
            val installed=context.packageManager.getPackageInfo(context.packageName,0)
            @Suppress("DEPRECATION")
            val current=if(Build.VERSION.SDK_INT>=28) installed.longVersionCode else installed.versionCode.toLong()
            require(target>current) {"该版本不高于已安装版本"}
            ExperimentalUpdater(context).verifyIdentity(partial,target)
            val ready=File(directory,"github-${UUID.randomUUID()}.apk")
            require(partial.renameTo(ready));ready
        } finally {partial.delete()}
    }
}
