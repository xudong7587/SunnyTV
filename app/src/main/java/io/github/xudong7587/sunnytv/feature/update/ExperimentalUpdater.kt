package io.github.xudong7587.sunnytv.feature.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import io.github.xudong7587.sunnytv.core.storage.ConfigStore
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class ExperimentalUpdater(private val context:Context) {
    private val store=ConfigStore(context)
    suspend fun download():File? {
        require(store.webDavUpdatesEnabled()) {"请先开启 WebDAV 更新"}
        return download {GithubUpdates.latest()}
    }
    @Suppress("DEPRECATION")
    internal suspend fun download(latest:suspend ()->GithubUpdates.Release):File?=downloadMutex.withLock {withContext(Dispatchers.IO) {
        val (address,user,password)=store.updateConfig()
        require(address.isNotBlank()) {"请先保存 WebDAV 升级目录地址"}
        val release=latest()
        val installed=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNATURES)
        val code=if(Build.VERSION.SDK_INT>=28) installed.longVersionCode else installed.versionCode.toLong()
        if(release.versionCode<=code) return@withContext null
        stageDav(release,io.github.xudong7587.sunnytv.core.network.UpdateTransport(address,user,password))
    }}
    /** Both channels use the official asset name. DAV requires no JSON or checksum sidecar. */
    internal suspend fun stageDav(release:GithubUpdates.Release,transport:io.github.xudong7587.sunnytv.core.network.UpdateTransport):File=withContext(Dispatchers.IO) {
        require(release.versionCode in 1..Int.MAX_VALUE.toLong() && release.versionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {"升级版本无效"}
        val name="SunnyTV-v${release.versionName}.apk"
        require(release.apk.substringAfterLast('/')==name) {"官方 APK 文件名无效"}
        val folder=File(context.cacheDir,"updates").apply {mkdirs()}
        val id=java.util.UUID.randomUUID().toString()
        val partial=File(folder,"$id.part.apk");val apk=File(folder,"$id.apk")
        try {
            partial.outputStream().use {output->transport.fetch(name,268435456) {b,n->output.write(b,0,n)}}
            verifyIdentity(partial,release.versionCode,release.versionName)
            require(partial.renameTo(apk)) {"无法保存升级包"};apk
        } finally {partial.delete()}
    }
    @Suppress("DEPRECATION")
    internal fun verifyIdentity(file:File,target:Long,versionName:String?=null) {
        val installed=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNATURES)
            val archive=context.packageManager.getPackageArchiveInfo(file.path,PackageManager.GET_SIGNATURES) ?: error("无法读取 APK")
            val archiveCode=if(Build.VERSION.SDK_INT>=28) archive.longVersionCode else archive.versionCode.toLong()
            require(archive.packageName==context.packageName && archiveCode==target) {"APK 包名或版本不匹配"}
            require(versionName==null || archive.versionName==versionName) {"APK 版本名称与 GitHub 最新版不匹配"}
            require(archive.signatures?.map {it.toCharsString()}?.toSet()==installed.signatures?.map {it.toCharsString()}?.toSet() && !archive.signatures.isNullOrEmpty()) {"APK 签名与已安装版本不同"}
    }
    companion object {private val downloadMutex=Mutex()}
    fun install(file:File) {
        if(Build.VERSION.SDK_INT>=26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")))
            return
        }
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.updates",file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
