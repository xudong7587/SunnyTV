package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
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
        language.isBlank()->SubtitleChoices("搜索字幕语言",listOf("chi" to "中文","eng" to "英语","fre" to "法语","jpn" to "日语","kor" to "韩语"),onDismiss) {language=it}
        busy->SubtitleChoices("正在搜索或下载字幕，请稍候…",emptyList(),onDismiss) {}
        notice.isNotBlank()->SubtitleChoices(notice,listOf("retry" to "重新搜索"),onDismiss) {notice="";language=""}
        else->SubtitleChoices("Emby 字幕 · 下载后使用（剧集记住提供者与语言）",
            listOf("language" to "更换语言")+results.mapIndexed {i,s->i.toString() to
                "${s.name} · ${s.provider} · ${s.format}${if(s.hashMatch) " · 精确匹配" else ""}"},onDismiss) {key->
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

/** Standalone native dialog: also works in PlayerActivity without an AppModel composition local. */
@Composable private fun SubtitleChoices(title:String,choices:List<Pair<String,String>>,onDismiss:()->Unit,onChoose:(String)->Unit) {
    Dialog(onDismissRequest=onDismiss) {
        Column(Modifier.width(520.dp).heightIn(max=520.dp).background(SunnyColors.Surface,RoundedCornerShape(18.dp)).padding(22.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(title,color=SunnyColors.Text,fontSize=18.sp)
            LazyColumn(Modifier.weight(1f,false),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(choices,key={it.first}) {(id,label)->SubtitleChoiceRow(label,id==choices.firstOrNull()?.first) {onChoose(id)}}
            }
            SubtitleChoiceRow("关闭",choices.isEmpty(),onDismiss)
        }
    }
}
@Composable private fun SubtitleChoiceRow(label:String,initial:Boolean,onClick:()->Unit) {
    val focus=remember {FocusRequester()}
    var focused by remember {mutableStateOf(false)}
    LaunchedEffect(Unit) {if(initial) {withFrameNanos {};focus.requestFocus()}}
    Text(label,color=SunnyColors.Text,fontSize=15.sp,modifier=Modifier.fillMaxWidth().focusRequester(focus)
        .onFocusChanged {focused=it.isFocused}.background(if(focused) SunnyColors.SurfaceRaised else SunnyColors.Surface,RoundedCornerShape(10.dp))
        .border(1.dp,if(focused) SunnyColors.Accent else SunnyColors.Border,RoundedCornerShape(10.dp)).clickable(onClick=onClick).padding(12.dp))
}
