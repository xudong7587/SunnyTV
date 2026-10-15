package io.github.xudong7587.sunnytv.feature.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.xudong7587.sunnytv.core.network.UpdateTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

internal object GithubUpdates {
    const val PAGE = "https://github.com/xudong7587/SunnyTV/releases"
    const val LATEST_PAGE = "$PAGE/latest"
    private const val LATEST_DOWNLOAD = "$LATEST_PAGE/download/"
    fun allowedPage(value:String):Boolean {
        val url=value.toHttpUrlOrNull() ?: return false
        return url.scheme=="https" && url.host=="github.com" && url.port==443 && url.username.isEmpty() && url.password.isEmpty() &&
            url.query==null && url.fragment==null &&
            (url.encodedPath=="/xudong7587/SunnyTV/releases" || url.encodedPath=="/xudong7587/SunnyTV/releases/latest" || url.encodedPath.startsWith("/xudong7587/SunnyTV/releases/tag/"))
    }
    data class Release(val versionName:String,val versionCode:Long,val page:String,val apk:String)
    internal fun latestFeedPage(text:String):String {
        require(text.toByteArray(Charsets.UTF_8).size<=1048576 && !Regex("<!\\s*(DOCTYPE|ENTITY)",RegexOption.IGNORE_CASE).containsMatchIn(text)) {"官方发行订阅无效"}
        val pages=mutableListOf<Pair<String,String>>()
        var depth=0;var elements=0;var entryDepth=0;var page="";var updated="";var reading=false
        val value=StringBuilder()
        val handler=object:DefaultHandler() {
            override fun resolveEntity(publicId:String?,systemId:String?):InputSource = throw SAXException("不允许外部实体")
            override fun startElement(uri:String,localName:String,qName:String,attributes:Attributes) {
                depth++;elements++;require(depth<=32 && elements<=10000) {"官方发行订阅过大"}
                if(depth==1) require(uri=="http://www.w3.org/2005/Atom" && localName=="feed")
                if(uri!="http://www.w3.org/2005/Atom") return
                if(depth==2 && localName=="entry") {entryDepth=depth;page="";updated=""}
                if(entryDepth>0 && depth==entryDepth+1 && localName=="link") {
                    val href=attributes.getValue("href").orEmpty()
                    if(allowedPage(href) && href.startsWith("$PAGE/tag/v") && href.substringAfter("$PAGE/tag/").matches(Regex("[A-Za-z0-9._-]+"))) page=href
                }
                if(entryDepth>0 && depth==entryDepth+1 && localName=="updated") {reading=true;value.clear()}
            }
            override fun characters(chars:CharArray,start:Int,length:Int) {
                if(reading) {require(value.length+length<=128);value.append(chars,start,length)}
            }
            override fun endElement(uri:String,localName:String,qName:String) {
                if(reading && depth==entryDepth+1 && localName=="updated") {updated=value.toString().trim();reading=false}
                if(entryDepth==depth && localName=="entry") {
                    if(page.isNotBlank() && updated.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z"))) pages+=updated to page
                    entryDepth=0
                }
                depth--
            }
        }
        val factory=SAXParserFactory.newInstance().apply {
            isNamespaceAware=true
            runCatching {setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)}
            runCatching {setFeature("http://xml.org/sax/features/external-general-entities",false)}
            runCatching {setFeature("http://xml.org/sax/features/external-parameter-entities",false)}
        }
        factory.newSAXParser().xmlReader.apply {contentHandler=handler;entityResolver=handler;errorHandler=handler}
            .parse(InputSource(StringReader(text)))
        return pages.maxByOrNull {it.first}?.second ?: error("未找到官方发行版本")
    }
    private fun transportForAsset(value:String):Pair<UpdateTransport,String> {
        val url=value.toHttpUrlOrNull() ?: error("官方升级地址无效")
        require(url.scheme=="https" && url.host=="github.com" && url.port==443 && url.username.isEmpty() && url.password.isEmpty() && url.query==null && url.fragment==null &&
            url.encodedPath.startsWith("/xudong7587/SunnyTV/releases/download/")) {"官方升级地址无效"}
        val name=url.pathSegments.last()
        require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*")))
        val base=url.newBuilder().removePathSegment(url.pathSegments.lastIndex).addPathSegment("").build().toString()
        return UpdateTransport(base,"","") to name
    }
    suspend fun latest():Release=withTimeout(45000) {withContext(Dispatchers.IO) {
        // GitHub's latest asset alias follows the officially selected stable release, without REST quota.
        // SafeHttp retains strict TLS, scoped headers and bounded redirects on the download route.
        val text=ByteArrayOutputStream()
        UpdateTransport(LATEST_DOWNLOAD,"","").fetch("BUILD_INFO.txt",16384) {b,n->text.write(b,0,n)}
        parseLatestInfo(text.toString("UTF-8"))
    }}
    internal fun parseLatestInfo(text:String):Release {
        val version=text.lineSequence().firstOrNull {it.startsWith("version=")}?.removePrefix("version=").orEmpty()
        require(version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {"官方最新正式版编号无效"}
        val tag="v$version"
        return parseReleaseInfo(tag,"$PAGE/tag/$tag","$PAGE/download/$tag/SunnyTV-$tag.apk",text)
    }
    internal fun parseReleaseInfo(tag:String,page:String,apk:String,text:String):Release {
        val info=text.lineSequence().mapNotNull {line->line.indexOf('=').takeIf {it>0}?.let {line.substring(0,it) to line.substring(it+1)}}.toMap()
        val version=info["version"].orEmpty();val code=info["versionCode"]?.toLongOrNull() ?: 0
        require(version.matches(Regex("[A-Za-z0-9._-]+")) && tag=="v$version" && code in 1..Int.MAX_VALUE.toLong() &&
            info["applicationId"]=="io.github.xudong7587.sunnytv.debug" && info["debuggable"]=="false") {"官方发行版本信息不完整"}
        require(allowedPage(page) && page=="$PAGE/tag/$tag") {"官方发行页面无效"}
        val (_,name)=transportForAsset(apk)
        require(name=="SunnyTV-v$version.apk" && apk=="https://github.com/xudong7587/SunnyTV/releases/download/$tag/$name") {"官方升级版本不匹配"}
        return Release(version,code,page,apk)
    }
    suspend fun download(context:Context,selected:String):File=withContext(Dispatchers.IO) {
        val release=latest()
        require(selected==release.apk) {"官方最新版本已变化，请重新检查后下载"}
        val (transport,name)=transportForAsset(selected)
        val checksums=ByteArrayOutputStream()
        transport.fetch("SHA256SUMS.txt",65536) {bytes,count->checksums.write(bytes,0,count)}
        val expected=checksums.toString("UTF-8").lineSequence().map {it.trim().split(Regex("\\s+"),limit=2)}
            .firstOrNull {it.size==2 && it[1].removePrefix("*")==name}?.first().orEmpty()
        require(expected.matches(Regex("[a-fA-F0-9]{64}"))) {"官方发行缺少下载校验值"}
        val directory=File(context.cacheDir,"updates").apply {mkdirs()}
        val partial=File(directory,"github-${UUID.randomUUID()}.part.apk")
        try {
            val digest=MessageDigest.getInstance("SHA-256")
            partial.outputStream().use {output->transport.fetch(name,268435456) {bytes,count->output.write(bytes,0,count);digest.update(bytes,0,count)}}
            require(partial.length()>0 && digest.digest().joinToString("") {"%02x".format(it)}.equals(expected,true)) {"升级包校验失败"}
            @Suppress("DEPRECATION")
            val archive=context.packageManager.getPackageArchiveInfo(partial.path,PackageManager.GET_SIGNATURES) ?: error("APK 无效")
            @Suppress("DEPRECATION")
            val target=if(Build.VERSION.SDK_INT>=28) archive.longVersionCode else archive.versionCode.toLong()
            @Suppress("DEPRECATION")
            val installed=context.packageManager.getPackageInfo(context.packageName,0)
            @Suppress("DEPRECATION")
            val current=if(Build.VERSION.SDK_INT>=28) installed.longVersionCode else installed.versionCode.toLong()
            require(target==release.versionCode && target>current) {"该版本不高于已安装版本"}
            ExperimentalUpdater(context).verifyIdentity(partial,target)
            val ready=File(directory,"github-${UUID.randomUUID()}.apk")
            require(partial.renameTo(ready));ready
        } finally {partial.delete()}
    }
}
