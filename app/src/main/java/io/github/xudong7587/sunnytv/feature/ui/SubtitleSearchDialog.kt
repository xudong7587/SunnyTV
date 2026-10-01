package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.runtime.*
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.storage.ConfigStore
import io.github.xudong7587.sunnytv.source.emby.EmbySource
import kotlinx.coroutines.*

/** Uses installed server providers. Provider credentials and downloaded files stay at Emby. */
@Composable fun SubtitleSearchDialog(api:EmbySource,store:ConfigStore,item:MediaEntry,sourceId:String,
    onDismiss:()->Unit,onDownloaded:(MediaTrack)->Unit) {
    var language by remember(item.key) {mutableStateOf("")}
    var results by remember(item.key) {mutableStateOf<List<RemoteSubtitle>>(emptyList())}
    var busy by remember {mutableStateOf(false)}
    var notice by remember {mutableStateOf("")}
    val scope=rememberCoroutineScope()
    LaunchedEffect(item.key,sourceId,language) {
        if(language.isBlank()) return@LaunchedEffect
        busy=true;notice=""
        try {results=withContext(Dispatchers.IO) {api.searchSubtitles(item.id,sourceId,language)}
            if(results.isEmpty()) notice="未找到字幕，请确认 Emby 已安装并配置字幕插件，或更换语言。"
        } catch(e:CancellationException) {throw e}
        catch(_:Exception) {notice="字幕搜索失败，请检查字幕插件及账号权限。"}
        finally {busy=false}
    }
    when {
        language.isBlank()->ChoiceDialog("搜索字幕语言",listOf("chi" to "中文","eng" to "英语","fre" to "法语","jpn" to "日语","kor" to "韩语"),"chi",onDismiss) {language=it}
        busy->MessageDialog("正在搜索或下载字幕，请稍候…",onDismiss)
        notice.isNotBlank()->MessageDialog(notice) {notice="";language=""}
        else->ChoiceDialog("Emby 字幕 · 下载后使用（剧集记住提供者与语言）",
            listOf("language" to "更换语言")+results.mapIndexed {i,s->i.toString() to
                "${s.name} · ${s.provider} · ${s.format}${if(s.hashMatch) " · 精确匹配" else ""}"},"language",onDismiss) {key->
            if(key=="language") language="" else results.getOrNull(key.toIntOrNull() ?: -1)?.let {result->
                scope.launch {
                    busy=true
                    try {
                        val track=withContext(Dispatchers.IO) {
                            val index=api.downloadSubtitle(item.id,sourceId,result)
                            api.downloadedSubtitle(item.id,sourceId,index)
                        }
                        if(track==null) notice="字幕已下载，但 Emby 尚未返回新轨道，请返回详情页刷新后选择。"
                        else {
                            store.saveSeriesSubtitle(item.sourceId,item.seriesId,SeriesSubtitleChoice(result.provider,language,result.format))
                            onDownloaded(track)
                        }
                    } catch(e:CancellationException) {throw e}
                    catch(_:Exception) {notice="字幕下载失败，请检查插件及字幕保存权限。"}
                    finally {busy=false}
                }
            }
        }
    }
}
