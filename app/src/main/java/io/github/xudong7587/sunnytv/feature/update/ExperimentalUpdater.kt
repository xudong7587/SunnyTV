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
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class ExperimentalUpdater(private val context:Context) {
    private val store=ConfigStore(context)
    @Suppress("DEPRECATION")
    suspend fun download():File?=downloadMutex.withLock {withContext(Dispatchers.IO) {
        val (address,user,password)=store.updateConfig()
        require(address.isNotBlank()) {"请先保存 WebDAV 升级目录地址"}
        val transport=io.github.xudong7587.sunnytv.core.network.UpdateTransport(address,user,password)
        val bytes=java.io.ByteArrayOutputStream()
        transport.fetch("latest.json",65536) {b,n->bytes.write(b,0,n)}
        val manifest=JSONObject(bytes.toString("UTF-8"))
        require(manifest.getString("applicationId")==context.packageName) {"升级包不适用于当前应用"}
        val installed=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNATURES)
        val code=if(Build.VERSION.SDK_INT>=28) installed.longVersionCode else installed.versionCode.toLong()
        val target=manifest.getLong("versionCode");require(target in 1..Int.MAX_VALUE.toLong()) {"升级版本无效"}
        if(target<=code) return@withContext null
        val size=manifest.getLong("size");require(size in 1..268435456) {"升级包大小无效"}
        require(manifest.getString("apk").endsWith(".apk")) {"升级文件必须是 APK"}
        val expected=manifest.getString("sha256");require(expected.matches(Regex("[a-fA-F0-9]{64}"))) {"升级校验值无效"}
        val folder=File(context.cacheDir,"updates").apply {mkdirs()}
        val partial=File(folder,"download.part.apk");val apk=File(folder,"latest.apk")
        try {
            val digest=MessageDigest.getInstance("SHA-256")
            partial.outputStream().use {output->transport.fetch(manifest.getString("apk"),size) {b,n->output.write(b,0,n);digest.update(b,0,n)}}
            require(partial.length()==size && digest.digest().joinToString("") {"%02x".format(it)}.equals(expected,true)) {"升级包校验失败"}
            verifyIdentity(partial,target)
            if(apk.exists()) require(apk.delete()) {"无法替换缓存升级包"}
            require(partial.renameTo(apk)) {"无法保存升级包"};apk
        } finally {partial.delete()}
    }
    }
    @Suppress("DEPRECATION")
    internal fun verifyIdentity(file:File,target:Long) {
        val installed=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNATURES)
            val archive=context.packageManager.getPackageArchiveInfo(file.path,PackageManager.GET_SIGNATURES) ?: error("无法读取 APK")
            val archiveCode=if(Build.VERSION.SDK_INT>=28) archive.longVersionCode else archive.versionCode.toLong()
            require(archive.packageName==context.packageName && archiveCode==target) {"APK 包名或版本不匹配"}
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
