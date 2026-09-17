package io.github.xudong7587.sunnytv.feature.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.Text
import coil.request.ImageRequest
import coil.request.SuccessResult
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sample at 64px once per artwork tag. Never decode pixels on the UI thread. */
@Composable private fun posterTint(item:MediaEntry):Color {
    val model=LocalAppModel.current
    val context=LocalContext.current
    val fallback=SunnyColors.Background
    val source=model.sources.firstOrNull {it.id==item.sourceId}
    val art=item.primary ?: item.backdrop
    val dark=model.settings.darkTheme
    val tint by produceState(fallback,item.key,art,dark) {
        if(source==null || art==null) return@produceState
        try {
            val result=model.app.images(source).execute(ImageRequest.Builder(context)
                .data(model.app.emby(source).imageUrl(art,96)).size(64,64).allowHardware(false).build())
            if(result is SuccessResult) value=withContext(Dispatchers.Default) {
                val bitmap=result.drawable.toBitmap(64,64)
                val pixels=IntArray(4096);bitmap.getPixels(pixels,0,64,0,0,64,64)
                val buckets=IntArray(512)
                pixels.forEach {pixel->
                    val r=(pixel ushr 16) and 255;val g=(pixel ushr 8) and 255;val b=pixel and 255
                    if(maxOf(r,g,b)>35 && minOf(r,g,b)<230) buckets[((r/32)*64)+(g/32)*8+b/32]++
                }
                val bin=buckets.indices.maxByOrNull {buckets[it]} ?: 0
                val raw=Color(((bin/64)*32+16)/255f,(((bin/8)%8)*32+16)/255f,((bin%8)*32+16)/255f)
                if(dark) {
                    var shade=lerp(raw,Color.Black,.28f)
                    while(shade.luminance()>.14f) shade=lerp(shade,Color.Black,.08f)
                    shade
                } else lerp(raw,Color.White,.84f)
            }
        } catch(e:kotlinx.coroutines.CancellationException) {throw e}
        catch(_:Exception) {value=fallback}
    }
    return tint
}

@Composable fun MediaDetailContent(initial:MediaEntry,onPlay:(MediaEntry,Boolean)->Unit) {
    val model=LocalAppModel.current
    val item=model.details[initial.key] ?: initial
    val context=LocalContext.current
    val tint=posterTint(item)
    val base=LocalSunnyPalette.current
    val themed=base.copy(background=tint,surface=lerp(tint,base.surface,.30f),raised=lerp(tint,base.raised,.55f))
    var panel by remember(item.key) {mutableStateOf("")}
    val version=item.versions.firstOrNull {it.id==model.selectedVersion[item.key]} ?: item.versions.firstOrNull()
    val tracks=version?.tracks ?: emptyList()
    CompositionLocalProvider(LocalSunnyPalette provides themed) {
        Box(Modifier.fillMaxSize().background(tint)) {
            LazyColumn(contentPadding=PaddingValues(bottom=40.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item(key="detail-header") {
                    Box(Modifier.fillMaxWidth().height(320.dp)) {
                        ArtworkView(item,item.backdrop ?: item.primary,Modifier.fillMaxSize(),1920)
                        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(.55f),Color.Transparent))))
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,tint.copy(.3f),tint))))
                        Column(Modifier.align(Alignment.BottomStart).padding(start=38.dp,end=38.dp,bottom=4.dp).widthIn(max=680.dp),
                            verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            MediaTitle(item,38.sp)
                            Text(listOfNotNull(item.year.takeIf {it>0}?.toString(),item.rating.takeIf {it>0}?.let {"★ %.1f".format(it)},
                                item.durationMs.takeIf {it>0}?.let {"${it/60000} 分钟"},item.officialRating.takeIf {it.isNotBlank()}).joinToString("  ·  "),
                                color=SunnyColors.Text,fontSize=15.sp)
                            Text(item.genres.joinToString(" · "),color=SunnyColors.Secondary,fontSize=13.sp)
                        }
                    }
                }
                item(key="detail-actions") {
                    LazyRow(contentPadding=PaddingValues(horizontal=38.dp,vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        if(item.isPlayable) {
                            item {Action(if(item.positionMs>0) "▶  继续播放" else "▶  播放",id="detail-play",primary=true,autoFocus=true) {onPlay(item,false)}}
                            if(item.positionMs>0) item {Action("从头播放") {onPlay(item,true)}}
                            item {Action("≋  音频") {panel="audio"}}
                            item {Action("▱  版本") {panel="version"}}
                            item {Action("CC  字幕") {panel="subtitle"}}
                        }
                        item {Action("ⓘ  媒体信息") {panel="info"}}
                        item {Action(if(item.played) "✓  已看" else "✓  标记已看") {model.played(item)}}
                        item {Action(if(item.favorite) "♥  已收藏" else "♡  收藏",autoFocus=!item.isPlayable) {model.favorite(item)}}
                        item {Action("返回") {model.back()}}
                    }
                }
                item {Text(item.overview.ifBlank {"暂无简介"},color=SunnyColors.Secondary,fontSize=14.sp,lineHeight=23.sp,
                    modifier=Modifier.padding(horizontal=38.dp),maxLines=8,overflow=TextOverflow.Ellipsis)}
                if(item.versions.isNotEmpty()) item {
                    Column(Modifier.padding(horizontal=38.dp)) {
                        SectionTitle("播放资源", "${item.versions.size} 个版本")
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(3.dp)) {
                            items(item.versions,key={it.id}) {v->
                                FocusTile("version:${item.key}:${v.id}",Modifier.width(250.dp),active=v.id==version?.id,
                                    onClick={model.selectedVersion[item.key]=v.id;model.selectedAudio.remove(item.key);model.selectedSubtitleTrack.remove(item.key)}) {
                                    Column(Modifier.fillMaxWidth().cinemaGlass(18.dp).padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                        Text(v.name.ifBlank {"媒体版本"},color=SunnyColors.Text,fontSize=16.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                        Text(listOfNotNull(if(v.width>0) "${v.width}×${v.height}" else null,v.range.takeIf {it.isNotBlank()},v.container.uppercase().takeIf {it.isNotBlank()}).joinToString(" · "),color=SunnyColors.Text,fontSize=12.sp)
                                        Text(listOfNotNull(v.size.takeIf {it>0}?.let {"%.2f GB".format(it/1_000_000_000.0)},v.bitrate.takeIf {it>0}?.let {"%.1f Mbps".format(it/1_000_000.0)}).joinToString(" · "),color=SunnyColors.Secondary,fontSize=12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                val children=model.children[item.key] ?: emptyList()
                if(children.isNotEmpty()) item {Box(Modifier.padding(horizontal=38.dp)) {MediaShelf(if(item.type=="Series") "选择季" else "剧集",children,item.type=="Season",onClick={model.navigate(Route.Detail(it))})}}
                if(item.externalLinks.isNotEmpty()) item {
                    LazyRow(contentPadding=PaddingValues(horizontal=38.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        items(item.externalLinks,key={it.url}) {link->Action(link.name) {
                            try {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link.url)))} catch(_:Exception) {model.message="此设备没有可打开链接的浏览器"}
                        }}
                    }
                }
                if(item.people.isNotEmpty()) item {
                    Column(Modifier.padding(horizontal=38.dp)) {
                        SectionTitle("演员表")
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            items(item.people,key={"${it.id}:${it.name}:${it.role}"}) {person ->
                                Column(Modifier.width(104.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                    ArtworkView(MediaEntry(person.id,item.sourceId,person.name,"Person",primary=person.primary),person.primary,
                                        Modifier.size(88.dp).clip(CircleShape),180,fit=false,fallbackText=person.name.take(1))
                                    Text(person.name,color=SunnyColors.Text,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=9.dp))
                                    Text(person.role,color=SunnyColors.Secondary,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                item {Box(Modifier.padding(horizontal=38.dp)) {MediaShelf("相似推荐",model.similar[item.key] ?: emptyList(),onClick={model.navigate(Route.Detail(it))})}}
                model.errors["detail:${item.key}"]?.let {error->item {Box(Modifier.padding(horizontal=38.dp)) {EmptyState("详情暂不可用",error,"重试") {model.loadDetail(item)}}}}
                listOf("played","favorite").forEach {operation->model.errors["$operation:${item.key}"]?.let {error->item {Text(error,color=SunnyColors.Secondary,modifier=Modifier.padding(horizontal=38.dp))}}}
            }
        }
        when(panel) {
            "audio" -> ChoiceDialog("音频",listOf("default" to "默认音轨")+tracks.filter {it.type=="Audio"}.map {it.index.toString() to it.title},
                model.selectedAudio[item.key]?.index?.toString() ?: "default",{panel=""}) {id->
                val track=tracks.firstOrNull {it.type=="Audio" && it.index.toString()==id}
                if(track==null) model.selectedAudio.remove(item.key) else model.selectedAudio[item.key]=track
                panel=""
            }
            "subtitle" -> ChoiceDialog("字幕",listOf("default" to "跟随媒体默认")+Presentation.subtitles+
                tracks.filter {it.type=="Subtitle"}.map {"track:${it.index}" to it.title},
                model.selectedSubtitleTrack[item.key]?.let {"track:${it.index}"} ?: model.selectedSubtitle[item.key] ?: model.settings.subtitlePreference,{panel=""}) {id->
                val track=tracks.firstOrNull {it.type=="Subtitle" && "track:${it.index}"==id}
                if(track!=null) {model.selectedSubtitleTrack[item.key]=track;model.selectedSubtitle[item.key]=track.language}
                else {model.selectedSubtitleTrack.remove(item.key);model.selectedSubtitle[item.key]=id}
                panel=""
            }
            "version" -> if(item.versions.isNotEmpty()) ChoiceDialog("播放版本",item.versions.map {it.id to it.name},version?.id.orEmpty(),{panel=""}) {id->
                model.selectedVersion[item.key]=id;model.selectedAudio.remove(item.key);model.selectedSubtitleTrack.remove(item.key);panel=""
            } else MessageDialog("服务端尚未提供版本信息，请等待详情加载或重试。") {panel=""}
            "info" -> MessageDialog(buildString {
                append(item.title);append("\n\n")
                if(version!=null) {append("${version.width} × ${version.height} · ${version.container}\n")
                    version.tracks.forEach {append("${it.type} · ${it.title} · ${it.codec}\n")}}
                else append("服务端未提供详细轨道信息")
            }) {panel=""}
        }
    }
}
