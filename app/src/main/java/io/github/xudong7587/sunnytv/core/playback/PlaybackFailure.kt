package io.github.xudong7587.sunnytv.core.playback

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.HttpDataSource
import java.io.EOFException
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.util.concurrent.ExecutionException

/** Categorize causes, never display raw exception messages (which can contain signed URLs). */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object PlaybackFailure {
    private fun causes(error:Throwable):List<Throwable> {
        val result=mutableListOf<Throwable>()
        var current:Throwable?=error
        while(current!=null && result.size<10 && result.none {it===current}) {
            result+=current
            current=current.cause
        }
        return result
    }

    /** Matches the real-TV dev11 failure without depending on Media3's internal loader class. */
    fun isMp4IndexFailure(error:PlaybackException,mime:String?):Boolean {
        if(!mime.equals("video/mp4",ignoreCase=true) || error.errorCode!=2000) return false
        val names=causes(error).map {it.javaClass.simpleName}
        val loader=names.any {it.contains("UnexpectedLoaderException")}
        val bounds=names.any {it=="IndexOutOfBoundsException" || it=="ArrayIndexOutOfBoundsException"}
        return loader && bounds
    }

    /** Cause class names only. Exception messages are never displayed (they can carry signed URLs). */
    fun causeNames(error:Throwable):List<String> = causes(error).map {it.javaClass.simpleName}

    /** The Media3/DataSource error code buried in the chain, for retry decisions. 0 when unknown. */
    fun errorCodeOf(error:Throwable):Int =
        causes(error).filterIsInstance<DataSourceException>().map {it.reason}.firstOrNull {it>0} ?: 0

    fun describe(error:PlaybackException,stage:String,mime:String?,mediaUrl:String?=null,
                 baseUrl:String?=null,retryAttempts:Int=0):String {
        val chain=causes(error)
        val http=chain.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
        val reason=when {
            http!=null -> when(http.responseCode) {
                401,403 -> "Emby 拒绝读取媒体（HTTP ${http.responseCode}），请检查当前用户的播放权限。"
                404 -> "媒体地址不存在（HTTP 404），请确认 Emby 仍能访问本地文件。"
                416 -> "服务器拒绝此播放位置（HTTP 416），可以尝试从头播放。"
                else -> "媒体服务返回 HTTP ${http.responseCode}。"
            }
            chain.any {it is SSLException} -> "TLS 证书或加密连接失败；未跳过证书校验。"
            chain.any {it is UnknownHostException} -> "电视无法解析媒体服务器地址。"
            chain.any {it is SocketTimeoutException} -> "读取媒体超时：媒体服务在限定时间内没有返回数据，也没有断开连接。"
            chain.any {it is ConnectException} -> "电视无法连接媒体服务。"
            chain.any {it is EOFException} -> "媒体数据提前结束，请检查本地文件是否完整，以及 Emby 是否中断了读取。"
            chain.any {it is FileNotFoundException} -> "媒体文件不可读取或已不存在。"
            isMp4IndexFailure(error,mime) -> "MP4 索引或时间编辑表读取失败；SunnyTV 已支持一次兼容模式重试。"
            error.errorCode in setOf(PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) ->
                "当前设备解码器无法播放此媒体。"
            error.errorCode==PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> "保存的播放位置超出媒体范围，可以尝试从头播放。"
            error.errorCode in setOf(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) -> "无法解析媒体封装，请检查本地文件格式与完整性。"
            else -> "媒体读取失败，底层原因尚未分类。请保留下面的诊断编号。"
        }
        val types=chain.drop(1).map {it.javaClass.simpleName.filter {c->c.isLetterOrDigit() || c=='_'}.take(64)}
            .distinct().joinToString(" → ").ifBlank {"未提供底层异常"}
        val format=mime?.takeIf {it.matches(Regex("[a-zA-Z0-9.+/-]{1,80}"))} ?: "自动识别"
        val retry=if(retryAttempts>0) " · 已自动重试 $retryAttempts 次" else ""
        // Which host actually stalled, and which half of the exchange: host only, never path or query.
        val route=PlaybackRecovery.routeLabel(mediaUrl,baseUrl)
        val stageHint=PlaybackRecovery.stageHint(
            chain.filterIsInstance<HttpDataSource.HttpDataSourceException>().firstOrNull()?.type,
            chain.any {it is ExecutionException})
        val facts=listOfNotNull(route?.let {"请求主机 $it"},stageHint?.let {"阶段 $it"}).joinToString(" · ")
        val header="$reason\n错误码 ${error.errorCode} · $stage · $format$retry"
        return if(facts.isBlank()) "$header\n$types" else "$header\n$facts\n$types"
    }
}
