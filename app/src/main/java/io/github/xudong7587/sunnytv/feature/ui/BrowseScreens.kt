package io.github.xudong7587.sunnytv.feature.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.*
import kotlinx.coroutines.delay

@Composable fun Hero(item:MediaEntry?,eyebrow:String,tall:Boolean=false,candidates:List<MediaEntry> = emptyList(),
    onSelect:(MediaEntry)->Unit={},onPlay:(MediaEntry,Boolean)->Unit) {
    val model=LocalAppModel.current
    Box(Modifier.fillMaxWidth().height(if(tall) 336.dp else 270.dp)) {
        Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(if(tall) .40f else .70f).padding(end=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(eyebrow.uppercase(),color=SunnyColors.Accent,fontSize=11.sp,letterSpacing=1.5.sp)
                if(item==null) {
                    Text("你的私人影院",color=SunnyColors.Text,fontSize=40.sp,fontWeight=FontWeight.Bold)
                    Text("从熟悉的媒体库开始，让好内容回到大屏。",color=SunnyColors.Secondary,fontSize=15.sp)
                } else {
                    MediaTitle(item, if(tall) 38.sp else 44.sp)
                    Text(item.subtitle + if(item.rating>0) "    ★ %.1f".format(item.rating) else "",color=SunnyColors.Secondary,fontSize=13.sp)
                    Text(item.overview.ifBlank { "来自你的 Emby 媒体库" },color=SunnyColors.Secondary,fontSize=13.sp,lineHeight=21.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        if(item.isPlayable) Action(if(item.positionMs>0) "▶  继续播放" else "▶  播放",id="hero-play",primary=true,
                            onClick={onPlay(item,false)})
                        Action("查看详情",id="hero-detail",onClick={model.navigate(Route.Detail(item))})
                    }
                }
            }
            if(tall) {
                LazyRow(Modifier.weight(.60f),horizontalArrangement=Arrangement.spacedBy(15.dp),contentPadding=PaddingValues(4.dp)) {
                    items(candidates.take(12),key={it.key}) { entry ->
                        MediaCard(entry,autoFocus=entry==candidates.firstOrNull(),onFocus={onSelect(entry)},onClick={model.navigate(Route.Detail(entry))},focusId="hero:${entry.key}")
                    }
                }
            } else Spacer(Modifier.weight(.30f))
        }
    }
}

@Composable fun HomeScreen(onPlay:(MediaEntry,Boolean)->Unit) {
    val model=LocalAppModel.current
    val embySources=model.sources.filter { it.kind==SourceKind.EMBY }
    val feeds=embySources.mapNotNull { model.feeds[it.id] }
    val resume=feeds.flatMap { it.resume }
    val latest=feeds.flatMap { it.latest }
    val hero=resume.firstOrNull() ?: latest.firstOrNull()
    Box(Modifier.fillMaxSize()) {
        Backdrop(hero)
        LazyColumn(contentPadding=PaddingValues(start=40.dp,end=40.dp,bottom=36.dp)) {
            if(embySources.isEmpty()) item {
                EmptyState("每一个夜晚，都值得好好看。","添加 Emby，保留已有海报、媒体库封面和观看进度。\n也可以从 CloudDrive2 的文件开始。","添加媒体来源") { model.navigate(Route.Settings,root=true) }
            } else {
                item { Hero(hero,"WELCOME HOME · 为你继续",onPlay=onPlay) }
                item {
                    SectionTitle("我的媒体库","查看全部  ›") {model.navigate(Route.Libraries,root=true)}
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(3.dp)) {
                        items(feeds.flatMap { it.libraries },key={it.key}) { lib -> LibraryCard(lib) {model.navigate(Route.Library(lib))} }
                    }
                }
                if(model.settings.showResume) item { MediaShelf("继续观看",resume,true,onClick={model.navigate(Route.Detail(it))}) }
                if(model.settings.showNextUp) item { MediaShelf("接着看下一集",feeds.flatMap { it.nextUp },true,onClick={model.navigate(Route.Detail(it))}) }
                embySources.forEach { source ->
                    item(key="latest:${source.id}") {
                        MediaShelf(if(embySources.size>1) "${source.name} · 最新入库" else "最新入库",
                            model.feeds[source.id]?.latest ?: emptyList(),onClick={model.navigate(Route.Detail(it))})
                    }
                    model.errors["feed:${source.id}"]?.let { error -> item { EmptyState(source.name,error,"重试") {model.refresh()} } }
                    model.feeds[source.id]?.warnings?.forEach { warning -> item {Text(warning,color=SunnyColors.Secondary,fontSize=12.sp)} }
                }
            }
        }
    }
}

@Composable fun LibrariesScreen() {
    val model=LocalAppModel.current
    val libs=model.sources.filter {it.kind==SourceKind.EMBY}.flatMap {model.feeds[it.id]?.libraries ?: emptyList()}
    Column(Modifier.fillMaxSize().padding(horizontal=40.dp)) {
        SectionTitle("所有媒体库","使用 Emby 原生媒体库图片")
        if(libs.isEmpty()) EmptyState("这里还没有媒体库","添加 Emby 后，这里会显示你的原生媒体库封面。","管理来源") {model.navigate(Route.Settings)}
        LazyVerticalGrid(columns=GridCells.Adaptive(235.dp),horizontalArrangement=Arrangement.spacedBy(18.dp),
            verticalArrangement=Arrangement.spacedBy(20.dp),contentPadding=PaddingValues(vertical=10.dp)) {
            items(libs,key={it.key}) { lib -> LibraryCard(lib) {model.navigate(Route.Library(lib))} }
        }
    }
}

@Composable fun LibraryScreen(library:MediaEntry,onPlay:(MediaEntry,Boolean)->Unit) {
    val model=LocalAppModel.current; val page=model.pages[library.key]
    var candidate by remember(library.key) {mutableStateOf<MediaEntry?>(null)}
    var displayed by remember(library.key) {mutableStateOf<MediaEntry?>(null)}
    var grid by rememberSaveable(library.key) {mutableStateOf(false)}
    var sort by rememberSaveable(library.key) {mutableStateOf("DateCreated")}
    LaunchedEffect(candidate) {delay(240); displayed=candidate}
    val hero=displayed ?: page?.items?.firstOrNull()
    val listState=rememberLazyListState()
    BackHandler(enabled=grid) {grid=false;model.focusMemory[model.route.key()]="hero:${hero?.key}"}
    Box(Modifier.fillMaxSize()) {
        Backdrop(hero ?: library)
        if(!grid) {
            LazyColumn(state=listState,contentPadding=PaddingValues(start=40.dp,end=40.dp,bottom=40.dp)) {
                item {Hero(hero,"媒体库 / ${library.title}",true,page?.items ?: emptyList(),{candidate=it},onPlay)}
                item {
                    SectionTitle("${library.title} · 全部内容", "海报墙  ›") {model.focusMemory[model.route.key()]="grid:${page?.items?.firstOrNull()?.key}";grid=true}
                    Text("${page?.total ?: 0} 个条目  ·  沿用服务端元数据",color=SunnyColors.Secondary,fontSize=12.sp)
                }
                if(model.settings.showResume) item {MediaShelf("继续观看",model.libraryResume[library.key] ?: emptyList(),true,onClick={model.navigate(Route.Detail(it))})}
                item {MediaShelf("最新入库",page?.items ?: emptyList(),onClick={model.navigate(Route.Detail(it))})}
                model.errors["library:${library.key}"]?.let {e -> item {EmptyState("媒体库暂不可用",e,"重新加载") {model.loadLibrary(library)}}}
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal=40.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical=16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(library.title,color=SunnyColors.Text,fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    Action(if(sort=="DateCreated") "排序：最新入库" else "排序：名称") {
                        sort=if(sort=="DateCreated") "SortName" else "DateCreated"; model.loadLibrary(library,sort)
                    }
                    Action("沉浸首页") {grid=false}
                }
                LazyVerticalGrid(columns=GridCells.Adaptive(132.dp),horizontalArrangement=Arrangement.spacedBy(17.dp),
                    verticalArrangement=Arrangement.spacedBy(22.dp),contentPadding=PaddingValues(4.dp)) {
                    items(page?.items ?: emptyList(),key={it.key}) {entry -> MediaCard(entry,onClick={model.navigate(Route.Detail(entry))},focusId="grid:${entry.key}")}
                    if(page!=null && page.items.size<page.total) item(span={GridItemSpan(maxLineSpan)}) {
                        Action("加载更多 · ${page.items.size}/${page.total}") {model.loadLibrary(library,sort,true)}
                    }
                }
            }
        }
    }
}

@Composable fun DetailScreen(initial:MediaEntry,onPlay:(MediaEntry,Boolean)->Unit) {
    val model=LocalAppModel.current
    val item=model.details[initial.key] ?: initial
    val children=model.children[item.key] ?: emptyList()
    Box(Modifier.fillMaxSize()) {
        Backdrop(item)
        LazyColumn(contentPadding=PaddingValues(start=40.dp,end=40.dp,bottom=40.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top=25.dp),horizontalArrangement=Arrangement.spacedBy(30.dp)) {
                    ArtworkView(item,item.primary,Modifier.width(165.dp).aspectRatio(2f/3),500)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        MediaTitle(item,38.sp)
                        Text(item.subtitle + if(item.rating>0) "    ★ %.1f".format(item.rating) else "",color=SunnyColors.Accent,fontSize=14.sp)
                        Text(item.overview.ifBlank {"暂无简介"},color=SunnyColors.Secondary,fontSize=14.sp,lineHeight=24.sp,maxLines=6,overflow=TextOverflow.Ellipsis)
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            if(item.isPlayable) {
                                Action(if(item.positionMs>0) "▶  继续播放" else "▶  播放",id="detail-play",primary=true,autoFocus=true) {onPlay(item,false)}
                                if(item.positionMs>0) Action("从头播放") {onPlay(item,true)}
                            }
                            Action(if(item.favorite) "♥  已收藏" else "♡  收藏",autoFocus=!item.isPlayable) {model.favorite(item)}
                            Action("返回") {model.back()}
                        }
                    }
                }
            }
            if(children.isNotEmpty()) item {MediaShelf(if(item.type=="Series") "选择季" else "剧集",children,item.type=="Season",onClick={model.navigate(Route.Detail(it))})}
            model.errors["detail:${item.key}"]?.let {e -> item {EmptyState("详情暂不可用",e,"重试") {model.loadDetail(item)}}}
        }
    }
}

@Composable fun CloudScreen(onPlay:(MediaEntry,Boolean)->Unit) {
    val model=LocalAppModel.current; val sources=model.sources.filter {it.kind==SourceKind.CLOUDDRIVE}
    Column(Modifier.padding(horizontal=40.dp)) {
        SectionTitle("我的云盘","CloudDrive2 · WebDAV 只读接入")
        if(sources.isEmpty()) EmptyState("让云端内容来到大屏","填写 CD2 的 WebDAV 地址；不是管理后台地址。","添加 CloudDrive2") {model.navigate(Route.Settings)}
        sources.forEach { source ->
            FocusTile("source:${source.id}",Modifier.fillMaxWidth().padding(vertical=6.dp),onClick={model.navigate(Route.Folder(source.id,source.baseUrl,source.name))}) {
                Row(Modifier.fillMaxWidth().padding(22.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("▱",color=SunnyColors.Accent,fontSize=36.sp)
                    Column(Modifier.weight(1f).padding(start=20.dp)) {Text(source.name,color=SunnyColors.Text,fontSize=21.sp); Text("浏览目录 · 视频与 STRM",color=SunnyColors.Secondary,fontSize=13.sp)}
                    Text("进入  ›",color=SunnyColors.Accent,fontSize=14.sp)
                }
            }
        }
    }
}

@Composable fun FolderScreen(route:Route.Folder,onPlay:(MediaEntry,Boolean)->Unit) {
    val model=LocalAppModel.current; val entries=model.folders["${route.sourceId}:${route.path}"]
    Column(Modifier.fillMaxSize().padding(horizontal=40.dp)) {
        SectionTitle(route.title,"重新读取") {model.loadFolder(route.sourceId,route.path)}
        Text("CloudDrive2 · 只读模式；不移动、不重命名、不删除文件",color=SunnyColors.Secondary,fontSize=12.sp)
        model.errors["folder:${route.sourceId}:${route.path}"]?.let {e->EmptyState("读取目录失败",e)}
        if(entries==null) Text("正在读取目录…",color=SunnyColors.Secondary,modifier=Modifier.padding(24.dp))
        if(entries?.isEmpty()==true) EmptyState("这里没有可播放内容","仅显示文件夹、视频及 STRM 文件。")
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=18.dp)) {
            items(entries ?: emptyList(),key={it.key}) {entry ->
                FocusTile("file:${entry.key}",Modifier.fillMaxWidth(),onClick={if(entry.isFolder) model.open(entry) else onPlay(entry,false)}) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(if(entry.isFolder) "▱" else "▶",color=SunnyColors.Accent,fontSize=22.sp)
                        Text(entry.title,color=SunnyColors.Text,fontSize=16.sp,modifier=Modifier.weight(1f).padding(horizontal=18.dp),maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(if(entry.isFolder) "文件夹  ›" else "播放",color=SunnyColors.Secondary,fontSize=12.sp)
                    }
                }
            }
        }
    }
}

@Composable fun SearchScreen(onPlay:(MediaEntry,Boolean)->Unit) {
    val model=LocalAppModel.current; var text by rememberSaveable {mutableStateOf("")}
    Column(Modifier.fillMaxSize().padding(horizontal=40.dp)) {
        SectionTitle("搜索你的媒体库")
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Field("片名或关键词",text,{text=it},Modifier.weight(1f))
            Action("搜索",primary=true) {if(text.isNotBlank()) model.search(text.trim())}
        }
        LazyColumn(contentPadding=PaddingValues(bottom=30.dp)) {
            model.sources.filter {it.kind==SourceKind.EMBY}.forEach {source -> item {
                MediaShelf(source.name,model.pages["search:${source.id}"]?.items ?: emptyList(),onClick={model.navigate(Route.Detail(it))})
                model.errors["search:${source.id}"]?.let {Text(it,color=SunnyColors.Secondary)}
            } }
        }
    }
}
