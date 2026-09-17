package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.Route

/** Tinted, layered material. No fullscreen live blur or additional full-size image decode. */
@Composable fun Modifier.cinemaGlass(radius:Dp=28.dp):Modifier {
    val shape=RoundedCornerShape(radius)
    val settings=LocalAppModel.current.settings
    return flatShadow(shape,settings.shadowsEnabled && !settings.darkTheme).clip(shape)
        .background(Brush.verticalGradient(listOf(SunnyColors.SurfaceRaised.copy(.84f),SunnyColors.Surface.copy(.72f))))
        .border(.75.dp,Brush.verticalGradient(listOf(Color.White.copy(.28f),SunnyColors.Border.copy(.28f))),shape)
}

@Composable fun CinemaBackdrop(item:MediaEntry?,modifier:Modifier=Modifier) {
    val model=LocalAppModel.current
    val base=SunnyColors.Background
    val compact=LocalCompact.current
    val motion=LocalMotion.current
    Box(modifier.fillMaxSize().background(base)) {
        if(item!=null && model.settings.backdropEnabled) {
            Crossfade(item,animationSpec=motion.fade(400),label="backdrop") {media->
                ArtworkView(media,if(compact) media.primary ?: media.backdrop else media.backdrop ?: media.thumb ?: media.primary,Modifier.fillMaxSize(),1920)
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(base.copy(.65f),base.copy(.04f)))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(*listOf(0f to base.copy(.3f),.25f to Color.Transparent,.72f to Color.Transparent,1f to base).toTypedArray())))
        }
    }
}

/** Bounded accordion: max six hero cards or ten shelf cards. Requests are independent of animated width. */
@Composable fun AccordionCards(items:List<MediaEntry>,selected:Int,onSelect:(Int)->Unit,
    modifier:Modifier=Modifier,hero:Boolean=false,id:String,onMore:(()->Unit)?=null) {
    val model=LocalAppModel.current
    val entries=items.take(if(hero) 6 else 10)
    if(!LocalCompact.current && !hero) {
        TvAccordionCards(entries,selected,onSelect,modifier,id,onMore)
        return
    }
    val count=entries.size+(if(onMore!=null) 1 else 0)
    val requesters=remember(entries.map {it.key},onMore!=null) {List(count) {FocusRequester()}}
    if(count==0) return
    val rowState=rememberLazyListState()
    var rowFocused by remember {mutableStateOf(false)}
    LaunchedEffect(rowFocused) {if(!rowFocused && !hero) {onSelect(0);rowState.scrollToItem(0)}}
    BoxWithConstraints(modifier.onFocusChanged {rowFocused=it.hasFocus}.focusGroup()) {
        val gap=if(hero) 7.dp else 8.dp
        val height=if(hero) (maxWidth*.45f).coerceIn(180.dp,255.dp) else 165.dp
        val available=maxWidth-gap*(count-1)
        val selectedWidth=if(hero) height else height*16/9
        val smallWidth=if(count==1) available else ((available-selectedWidth)/(count-1)).coerceAtLeast(26.dp)
        @Composable fun Card(index:Int) {
                val active=selected.coerceIn(0,count-1)==index
                val target=if(!hero) {if(active && index<entries.size) selectedWidth else height*2/3}
                    else if(count==1) available else if(active) (available-smallWidth*(count-1)).coerceAtLeast(smallWidth) else smallWidth
                val width by animateDpAsState(target,
                    LocalMotion.current.spring(),label="accordion-width")
                val entry=entries.getOrNull(index)
                FocusTile("$id:${entry?.key ?: "all"}",Modifier.width(width).height(height)
                    .focusRequester(requesters[index]).onPreviewKeyEvent {event ->
                        if(event.type==KeyEventType.KeyDown && (event.key==Key.DirectionRight || event.key==Key.DirectionLeft)) {
                            if(!hero) return@onPreviewKeyEvent (index==count-1 && event.key==Key.DirectionRight) || (index==0 && event.key==Key.DirectionLeft)
                            val next=Presentation.next(index,if(event.key==Key.DirectionRight) 1 else -1,count,hero)
                            if(next!=index) requesters[next].requestFocus()
                            true
                        } else false
                    },active=active,shape=RoundedCornerShape(if(hero) 20.dp else 15.dp),
                    onFocus={onSelect(index)},onClick={if(entry==null) onMore?.invoke() else model.navigate(Route.Detail(entry))}) {focused ->
                    if(entry==null) {
                        Column(Modifier.fillMaxSize().padding(10.dp),verticalArrangement=Arrangement.Center,
                            horizontalAlignment=Alignment.CenterHorizontally) {
                            Text("→",color=SunnyColors.Accent,fontSize=30.sp)
                            if(active) { Text("查看全部",color=SunnyColors.Text,fontSize=17.sp,fontWeight=FontWeight.Bold)
                                Text("进入媒体库",color=SunnyColors.Secondary,fontSize=11.sp,modifier=Modifier.padding(top=8.dp)) }
                        }
                    } else {
                        ArtworkView(entry,if(!hero && active) MediaLogic.wideArtwork(entry) else entry.primary,
                            Modifier.fillMaxSize(),if(hero) 700 else 640)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(.05f),Color.Black.copy(.85f)))))
                        if(entry.rating>0) Text("★ %.1f".format(entry.rating),color=Color.White,fontSize=9.sp,
                            modifier=Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(.6f)).padding(4.dp))
                        Column(Modifier.align(Alignment.BottomStart).padding(if(active) 13.dp else 6.dp)) {
                            Text(entry.title,color=Color.White,fontSize=if(active) 19.sp else 10.sp,lineHeight=if(active) 24.sp else 14.sp,fontWeight=FontWeight.SemiBold,
                                maxLines=if(active) 2 else 3,overflow=TextOverflow.Ellipsis)
                            if(active) Text(entry.subtitle,color=Color.White.copy(.8f),fontSize=10.sp,modifier=Modifier.padding(top=5.dp),maxLines=1)
                        }
                        if(focused) Box(Modifier.fillMaxSize().border(2.dp,SunnyColors.Accent,RoundedCornerShape(if(hero) 20.dp else 15.dp)))
                    }
                }
        }
        if(hero) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(gap),verticalAlignment=Alignment.CenterVertically) {
            repeat(count) {index->key(entries.getOrNull(index)?.key ?: "all") {Card(index)}}
        } else StableLazyRow(Modifier.fillMaxWidth(),state=rowState,horizontalArrangement=Arrangement.spacedBy(gap),contentPadding=PaddingValues(4.dp)) {
            items(count,key={entries.getOrNull(it)?.key ?: "all"}) {Card(it)}
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun LibraryLatestRow(library:MediaEntry,folder:Boolean=false) {
    val model=LocalAppModel.current
    LaunchedEffect(library.key,folder) {if(folder) model.loadFolderPreview(library) else model.loadLatest(library)}
    val entries=(if(folder) model.folderPreviews[library.key] else model.libraryLatest[library.key])
    var selected by rememberSaveable(library.key,folder) {mutableIntStateOf(0)}
    val reveal=remember {BringIntoViewRequester()}
    val scope=rememberCoroutineScope()
    val axis=LocalTvFocusMotion.current
    Column(Modifier.bringIntoViewRequester(reveal).padding(bottom=14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=5.dp,bottom=5.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(if(folder) library.title else "${library.title} · 最新入库",color=SunnyColors.Text,fontSize=19.sp,
                fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,fill=false))
            Action("进入媒体库",id="more:${library.key}",icon="arrow",modifier=Modifier.onFocusChanged {
                if(it.isFocused) {axis.horizontal=false;scope.launch {withFrameNanos {};reveal.bringIntoView()}}
            }) {model.navigate(Route.Library(library))}
        }
        if(entries!=null && entries.isNotEmpty()) AccordionCards(entries,selected,{selected=it},id="latest:${library.key}",
            onMore={model.navigate(Route.Library(library))})
        else if(model.errors["${if(folder) "folder-preview" else "latest"}:${library.key}"]!=null) {
            Action("读取失败 · 重试") {if(folder) model.loadFolderPreview(library) else model.loadLatest(library)}
        } else if(entries==null) Text("正在读取…",color=SunnyColors.Secondary,fontSize=13.sp,modifier=Modifier.padding(vertical=20.dp))
        else Action("暂无媒体 · 查看媒体库") {model.navigate(Route.Library(library))}
    }
}
