package io.github.xudong7587.sunnytv.feature.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.launch
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
    // Room reserved inside a row so the focus shadow is not clipped, without indenting the row's
    // content away from the section heading.
    val detailRowGutter=FocusShadowGutter
    val context=LocalContext.current
    val tint by animateColorAsState(posterTint(item),LocalMotion.current.fade(400),label="detail-tint")
    val base=LocalSunnyPalette.current
    val themed=base.copy(background=tint,surface=lerp(tint,base.surface,.30f),raised=lerp(tint,base.raised,.55f))
    var panel by remember(item.key) {mutableStateOf("")}
    val version=item.versions.firstOrNull {it.id==model.selectedVersion[item.key]} ?: item.versions.firstOrNull()
    val tracks=version?.tracks ?: item.tracks
    val list=rememberLazyListState()
    val scope=rememberCoroutineScope()
    val motion=LocalMotion.current
    var contentMoving by remember(item.key) {mutableStateOf(false)}
    fun moveContent(index:Int,requester:FocusRequester?=null,alignTop:Boolean=false) {
        if(contentMoving) return
        contentMoving=true
        scope.launch {
            try {
                list.revealItem(index,motion,alignTop=alignTop)
                if(requester!=null) repeat(20) {
                    withFrameNanos { }
                    if(runCatching {requester.requestFocus()}.getOrDefault(false)) return@launch
                }
            } finally {contentMoving=false}
        }
    }
    val lowerContentFocus=remember(item.key) {FocusRequester()}
    var childSelected by remember(item.key) {mutableIntStateOf(0)}
    val episodeLayout=model.settings.episodeLayouts[item.key] ?: "horizontal"
    val numberColumns=if(LocalCompact.current) 4 else 10
    val children=model.children[item.key].orEmpty()
    val similar=model.similar[item.key].orEmpty()
    // One requester is attached to exactly one first reachable control below the action row.
    val lowerTarget=when {
        item.versions.isNotEmpty() -> "versions"
        children.any {it.type=="Episode"} -> "episodes"
        children.isNotEmpty() -> "seasons"
        item.people.isNotEmpty() -> "people"
        similar.isNotEmpty() -> "similar"
        else -> ""
    }
    val similarFocus=remember(item.key) {FocusRequester()}
    val peopleFocus=remember(item.key) {FocusRequester()}
    val peopleEntryFocus=if(lowerTarget=="people") lowerContentFocus else peopleFocus
    val similarEntryFocus=if(lowerTarget=="similar") lowerContentFocus else similarFocus
    val childRows=if(children.isEmpty()) 0 else if(children.none {it.type=="Episode"}) 1 else 1+when(episodeLayout) {
        "vertical" -> children.size
        "numbers" -> (children.size+numberColumns-1)/numberColumns
        else -> 1
    }
    val similarIndex=3+(if(item.versions.isNotEmpty()) 1 else 0)+childRows+(if(item.people.isNotEmpty()) 1 else 0)
    // The first control of the page: the pinned navigation's Down lands here and "Up" from the
    // sections below always has one concrete tile to return to.
    val entryActionId=when {
        item.isPlayable -> "detail-play"
        item.type in setOf("Series","Season") -> "detail-resume"
        else -> "detail-favorite"
    }
    val entryActionFocus=remember(item.key) {FocusRequester()}
    // First section under the action row: Up there scrolls the row back in and focuses it, instead of
    // skipping to the pinned navigation and leaving the buttons behind.
    val upToActions=Modifier.onPreviewKeyEvent {event->
        if(event.type==KeyEventType.KeyDown && event.key==Key.DirectionUp) {moveContent(2,entryActionFocus);true} else false
    }
    val bridge=LocalNavigationBridge.current
    DisposableEffect(bridge,item.key,entryActionId) {
        bridge?.contentEntryId=entryActionId
        // Focusing the pinned navigation asks the page to show its top again.
        bridge?.revealTop={list.animateScrollToItem(0)}
        onDispose {if(bridge?.contentEntryId==entryActionId) bridge.contentEntryId=null;bridge?.revealTop=null}
    }
    CompositionLocalProvider(LocalSunnyPalette provides themed) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // dev22: the media page keeps the same fixed, full-screen backdrop as the library page,
            // so the artwork is shown complete with only a soft fade at the bottom.
            Backdrop(item)
            // On a phone the header only takes the room between the pinned bar and the action row,
            // so those buttons sit right at the bottom instead of being pushed off screen. The
            // title/logo stays at the bottom-left of its own block, as before.
            val headerHeight=if(LocalHandset.current)
                (maxHeight-pageTopPadding-96.dp).coerceIn(170.dp,262.dp) else 340.dp
            StableVerticalViewport(hold={contentMoving}) {
            LazyColumn(modifier=Modifier.testTag("detail:viewport"),state=list,contentPadding=PaddingValues(bottom=40.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item(key="detail-header") {
                    Box(Modifier.fillMaxWidth().height(headerHeight)) {
                        // The artwork itself is the fixed page background; the header only carries copy.
                        Box(Modifier.fillMaxWidth().height(220.dp).align(Alignment.TopCenter)
                            .background(Brush.verticalGradient(listOf(tint.copy(.22f),Color.Transparent))))
                        Column(Modifier.align(Alignment.BottomStart).padding(start=pageSidePadding,end=pageSidePadding,bottom=4.dp).widthIn(max=680.dp),
                            verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            MediaTitle(item,if(LocalCompact.current) 29.sp else 38.sp)
                            Text(listOfNotNull(item.year.takeIf {it>0}?.toString(),item.rating.takeIf {it>0}?.let {"★ %.1f".format(it)},
                                item.durationMs.takeIf {it>0}?.let {"${it/60000} 分钟"},item.officialRating.takeIf {it.isNotBlank()}).joinToString("  ·  "),
                                color=SunnyColors.Text,fontSize=15.sp)
                            Text(item.genres.joinToString(" · "),color=SunnyColors.Secondary,fontSize=13.sp)
                        }
                    }
                }
                item(key="detail-actions") {
                    // Up from the first content row hands focus back to the pinned navigation. The
                    // row used to swallow Up to scroll the header into view, which left no way back
                    // to the top bar once the user had come down from it.
                    val moveIntoContent:Modifier.()->Modifier={onPreviewKeyEvent {
                        if(it.type!=KeyEventType.KeyDown) false else when(it.key) {
                            Key.DirectionUp -> {bridge?.enterNavigation();true}
                            Key.DirectionDown -> if(lowerTarget.isNotEmpty()) {
                                moveContent(3,lowerContentFocus)
                                true
                            } else false
                            else -> false
                        }
                    }}
                    StableLazyRow(modifier=Modifier.moveIntoContent().focusGroup(),contentPadding=PaddingValues(horizontal=pageSidePadding,vertical=18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        if(item.isPlayable) {
                            item {Action(if(item.positionMs>0) "▶  继续播放" else "▶  播放",id="detail-play",primary=true,autoFocus=true,
                                modifier=Modifier.focusRequester(entryActionFocus).moveIntoContent()) {onPlay(item,false)}}
                            if(item.positionMs>0) item {Action("从头播放",icon="restart",modifier=Modifier.moveIntoContent()) {onPlay(item,true)}}
                            item {Action("≋  音频",modifier=Modifier.moveIntoContent()) {panel="audio"}}
                            item {Action("▱  版本",modifier=Modifier.moveIntoContent()) {panel="version"}}
                            item {Action("CC  字幕",modifier=Modifier.moveIntoContent()) {panel="subtitle"}}
                        }
                        if(item.isPlayable) item {Action(if(item.played) "已看" else "标记已看",id="detail-played",active=item.played,modifier=Modifier.moveIntoContent()) {panel="played"}}
                        if(item.type in setOf("Series","Season")) {
                            // Resume first: entering a series should offer what the user came back for.
                            item {Action("继续播放",id="detail-resume",primary=true,autoFocus=true,icon="play",
                                modifier=Modifier.focusRequester(entryActionFocus).moveIntoContent()) {onPlay(item,false)}}
                            item {Action("从头播放",id="detail-start",primary=false,icon="restart",modifier=Modifier.moveIntoContent()) {onPlay(item,true)}}
                        }
                        item {Action(if(item.favorite) "已收藏" else "收藏",id="detail-favorite",active=item.favorite,
                            autoFocus=!item.isPlayable && item.type !in setOf("Series","Season"),
                            modifier=Modifier.then(if(entryActionId=="detail-favorite") Modifier.focusRequester(entryActionFocus) else Modifier).moveIntoContent()) {model.favorite(item)}}
                        if(model.canDelete(item)) item {Action("删除",id="detail-delete",icon="trash",
                            modifier=Modifier.moveIntoContent()) {model.requestDelete(item)}}
                        item {Action("返回",modifier=Modifier.moveIntoContent()) {model.back()}}
                    }
                }
                // Acceptance round 3: plain body colour, so the description stays legible over art.
                item {Text(item.overview.ifBlank {"暂无简介"},color=SunnyColors.Text,fontSize=14.sp,lineHeight=23.sp,
                    modifier=Modifier.padding(horizontal=pageSidePadding),maxLines=8,overflow=TextOverflow.Ellipsis)}
                if(item.versions.isNotEmpty()) item {
                    // dev22 acceptance: heading and cards share one left edge. The row keeps a small
                    // gutter inside its own bounds so the focus shadow is not clipped.
                    Column(Modifier.padding(horizontal=(pageSidePadding-detailRowGutter).coerceAtLeast(0.dp))
                        .then(if(lowerTarget=="versions") upToActions else Modifier)) {
                        Box(Modifier.padding(start=detailRowGutter)) {SectionTitle("播放资源", "${item.versions.size} 个版本")}
                        StableLazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp),reserveFocusSpace=false,
                            contentPadding=PaddingValues(horizontal=detailRowGutter,vertical=18.dp)) {
                            itemsIndexed(item.versions,key={ _,v->v.id}) {index,v->
                                FocusTile("version:${item.key}:${v.id}",Modifier.width(250.dp).then(if(lowerTarget=="versions" && index==0) Modifier.focusRequester(lowerContentFocus) else Modifier),active=v.id==version?.id,
                                    onClick={model.selectedVersion[item.key]=v.id;model.selectedAudio.remove(item.key);model.selectedSubtitleTrack.remove(item.key)}) {
                                    Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                        Text(v.name.ifBlank {"媒体版本"},color=SunnyColors.Text,fontSize=16.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                        Text(listOfNotNull(if(v.width>0) "${v.width}×${v.height}" else null,v.range.takeIf {it.isNotBlank()},v.container.uppercase().takeIf {it.isNotBlank()}).joinToString(" · "),color=SunnyColors.Text,fontSize=12.sp)
                                        Text(listOfNotNull(v.size.takeIf {it>0}?.let {"%.2f GB".format(it/1_000_000_000.0)},v.bitrate.takeIf {it>0}?.let {"%.1f Mbps".format(it/1_000_000.0)}).joinToString(" · "),color=SunnyColors.Secondary,fontSize=12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                if(children.isNotEmpty()) {
                    if(children.any {it.type=="Episode"}) {
                        item {Column(Modifier.padding(horizontal=pageSidePadding).then(if(lowerTarget=="episodes") upToActions else Modifier),
                            verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            SectionTitle("剧集")
                            EpisodeLayoutButtons(item,episodeLayout,if(lowerTarget=="episodes") lowerContentFocus else null)
                        }}
                        when(episodeLayout) {
                            "vertical" -> items(children,key={entry->"episode:${entry.key}"}) {entry->Box(Modifier.padding(horizontal=pageSidePadding)) {EpisodeListCard(entry)}}
                            "numbers" -> items(children.chunked(numberColumns),key={"numbers:${it.first().key}"}) {row->
                                Row(Modifier.padding(horizontal=pageSidePadding).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    row.forEach {entry->EpisodeNumber(entry,children.indexOf(entry),Modifier.weight(1f))}
                                    repeat(numberColumns-row.size) {Spacer(Modifier.weight(1f))}
                                }
                            }
                            else -> item {Box(Modifier.padding(horizontal=(pageSidePadding-detailRowGutter).coerceAtLeast(0.dp))) {
                                EpisodeHorizontal(children,gutter=detailRowGutter)}}
                        }
                    } else item {Column(Modifier.padding(horizontal=pageSidePadding).then(if(lowerTarget=="seasons") upToActions else Modifier)) {
                        SectionTitle("选择季","查看全部") {model.navigate(Route.Library(item))}
                        AccordionCards(children,childSelected,{childSelected=it},if(lowerTarget=="seasons") Modifier.focusRequester(lowerContentFocus) else Modifier,id="children:${item.key}",onMore={model.navigate(Route.Library(item))})
                    }}
                }
                if(item.people.isNotEmpty()) item {
                    Column(Modifier.padding(horizontal=(pageSidePadding-detailRowGutter).coerceAtLeast(0.dp))
                        .then(if(lowerTarget=="people") upToActions else Modifier)) {
                        Box(Modifier.padding(start=detailRowGutter)) {SectionTitle("演员表")}
                        StableLazyRow(modifier=Modifier.onPreviewKeyEvent {event->
                            if(event.type==KeyEventType.KeyDown && event.key==Key.DirectionDown && similar.isNotEmpty()) {
                                moveContent(similarIndex,similarEntryFocus);true
                            } else false
                        },horizontalArrangement=Arrangement.spacedBy(16.dp),reserveFocusSpace=false,
                            contentPadding=PaddingValues(start=detailRowGutter,end=detailRowGutter,
                                top=FocusShadowTopGutter,bottom=FocusShadowBottomGutter)) {
                            itemsIndexed(item.people,key={ _,person->"${person.id}:${person.name}:${person.role}"}) {index,person ->
                                Column(Modifier.width(104.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                    val actor=MediaEntry(person.id.ifBlank {"person:${person.name}"},item.sourceId,person.name,"Person",primary=person.primary)
                                    FocusTile("person:${actor.key}",Modifier.size(88.dp).then(if(index==0) Modifier.focusRequester(peopleEntryFocus) else Modifier),shape=CircleShape,onClick={model.navigate(Route.Detail(actor))}) {
                                        ArtworkView(actor,person.primary,Modifier.fillMaxSize(),180,fit=false,fallbackText=person.name.take(1))
                                    }
                                    Text(person.name,color=SunnyColors.Text,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=9.dp))
                                    Text(person.role,color=SunnyColors.Secondary,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                item {Box(Modifier.padding(horizontal=(pageSidePadding-detailRowGutter).coerceAtLeast(0.dp)).onPreviewKeyEvent {event->
                    if(event.type==KeyEventType.KeyDown && event.key==Key.DirectionUp && item.people.isNotEmpty()) {
                        moveContent(similarIndex-1,peopleEntryFocus);true
                    } else false
                }) {MediaShelf("相似推荐",similar,firstFocusRequester=similarEntryFocus,focusGutter=detailRowGutter,
                    onClick={model.navigate(Route.Detail(it))})}}
                model.errors["detail:${item.key}"]?.let {error->item {Box(Modifier.padding(horizontal=pageSidePadding)) {EmptyState("详情暂不可用",error,"重试") {model.loadDetail(item)}}}}
                listOf("played","favorite").forEach {operation->model.errors["$operation:${item.key}"]?.let {error->item {Text(error,color=SunnyColors.Secondary,modifier=Modifier.padding(horizontal=pageSidePadding))}}}
            }
            }
        }
        when(panel) {
            "played" -> ChoiceDialog(if(item.played) "取消这部影片/单集的已看状态？" else "将这部影片/单集标记为已看？",
                listOf("cancel" to "取消","confirm" to "确认"),"cancel",{panel=""}) {choice->
                panel="";if(choice=="confirm") model.played(item)
            }
            "audio" -> ChoiceDialog("音频",listOf("default" to "默认音轨")+tracks.filter {it.type=="Audio"}.map {it.index.toString() to it.title},
                model.selectedAudio[item.key]?.index?.toString() ?: "default",{panel=""}) {id->
                val track=tracks.firstOrNull {it.type=="Audio" && it.index.toString()==id}
                if(track==null) model.selectedAudio.remove(item.key) else model.selectedAudio[item.key]=track
                panel=""
            }
            "subtitle" -> ChoiceDialog(if(tracks.none {it.type=="Subtitle"}) "此版本暂无 Emby 已识别的字幕" else "此媒体的实际字幕",
                listOf("default" to "自动选择（按字幕偏好）","none" to "关闭字幕")+
                tracks.filter {it.type=="Subtitle"}.map {"track:${it.index}" to "${it.title} · ${if(it.external) "外挂" else "内嵌"}"},
                model.selectedSubtitleTrack[item.key]?.let {"track:${it.index}"} ?: model.selectedSubtitle[item.key] ?: "default",{panel=""}) {id->
                val track=tracks.firstOrNull {it.type=="Subtitle" && "track:${it.index}"==id}
                if(track!=null) {model.selectedSubtitleTrack[item.key]=track;model.selectedSubtitle.remove(item.key);version?.let {model.selectedVersion[item.key]=it.id}}
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
