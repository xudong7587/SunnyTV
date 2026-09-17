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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    if(LocalCompact.current) {
        Column(Modifier.fillMaxWidth().padding(vertical=14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(eyebrow,color=SunnyColors.Accent,fontSize=11.sp)
            if(item!=null) {
                MediaTitle(item,28.sp)
                Text(item.subtitle,color=SunnyColors.Secondary,fontSize=12.sp)
                Text(item.overview,color=SunnyColors.Secondary,fontSize=13.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(item.isPlayable) Action("播放",id="hero-play",primary=true) {onPlay(item,false)}
                    Action("查看详情",id="hero-detail") {model.navigate(Route.Detail(item))}
                }
            }
            if(tall) LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(4.dp)) {
                items(candidates.take(12),key={it.key}) {entry->MediaCard(entry,onFocus={onSelect(entry)},onClick={model.navigate(Route.Detail(entry))},focusId="hero:${entry.key}")}
            }
        }
        return
    }
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
    val incoming=when(model.settings.heroMode) {"resume"->resume;"random"->model.heroCandidates;else->latest}.distinctBy {it.key}.take(6)
    var candidates by remember {mutableStateOf(incoming)}
    var selected by rememberSaveable {mutableIntStateOf(0)}
    val hero=candidates.getOrNull(selected.coerceIn(0,(candidates.size-1).coerceAtLeast(0)))
    val list=rememberLazyListState()
    var heroFocused by remember {mutableStateOf(false)}
    LaunchedEffect(incoming,heroFocused,model.busy) {if(!heroFocused && !model.busy) {candidates=incoming;selected=selected.coerceIn(0,(incoming.size-1).coerceAtLeast(0))}}
    var paused by rememberSaveable {mutableStateOf(false)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var resumed by remember {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->resumed=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
        lifecycle.addObserver(observer);onDispose {lifecycle.removeObserver(observer)}
    }
    val visible by remember {derivedStateOf {list.firstVisibleItemIndex==0 && list.firstVisibleItemScrollOffset==0}}
    LaunchedEffect(heroFocused,paused,resumed,visible,model.busy,model.message,model.settings.reduceMotion,candidates.map {it.key},selected,model.settings.heroIntervalSeconds) {
        if(Presentation.canRotate(heroFocused,paused,resumed,visible,model.busy,model.message.isNotEmpty(),model.settings.reduceMotion,candidates.size)) {
            delay(model.settings.heroIntervalSeconds*1000L);selected=Presentation.next(selected,1,candidates.size,true)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport=maxHeight
        LazyColumn(state=list,contentPadding=PaddingValues(bottom=36.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(embySources.isEmpty()) item {
                Box(Modifier.padding(top=85.dp,start=40.dp,end=40.dp)) {EmptyState("每一个夜晚，都值得好好看。","添加 Emby，保留已有海报、媒体库封面和观看进度。\n连接后即可查看推荐和观看进度。","添加媒体来源") { model.navigate(Route.Settings,root=true) }}
            } else {
                item(key="home-hero") {
                    val darkPalette=remember(model.settings.accentIndex) {palette(model.settings.copy(darkTheme=true))}
                    CompositionLocalProvider(LocalSunnyPalette provides darkPalette) {
                        Box(Modifier.fillMaxWidth().height(viewport.coerceAtLeast(if(LocalCompact.current) 630.dp else 400.dp)).onFocusChanged {heroFocused=it.hasFocus}.focusGroup()) {
                            CinemaBackdrop(hero)
                            HomeHeroContent(hero,candidates,selected,{selected=it},
                                if(model.settings.showResume) resume else emptyList(),paused,{paused=!paused},onPlay)

                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal=30.dp)) {
                    SectionTitle("我的媒体库","查看全部  ›") {model.navigate(Route.Libraries,root=true)}
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(3.dp)) {
                        items(feeds.flatMap { it.libraries },key={it.key}) { lib -> LibraryCard(lib) {model.navigate(Route.Library(lib))} }
                    }
                    }
                }
                if(model.settings.showNextUp) item { Box(Modifier.padding(horizontal=30.dp)) {MediaShelf("接着看下一集",feeds.flatMap { it.nextUp },true,onClick={model.navigate(Route.Detail(it))})} }
                items(feeds.flatMap {it.libraries}.distinctBy {it.key},key={"library-latest:${it.key}"}) {library ->
                    Box(Modifier.padding(horizontal=30.dp)) {LibraryLatestRow(library)}
                }
                embySources.forEach { source ->
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
    Column(Modifier.fillMaxSize().padding(horizontal=pageSidePadding).padding(top=pageTopPadding)) {
        SectionTitle("所有媒体库")
        if(libs.isEmpty()) EmptyState("这里还没有媒体库","添加 Emby 后，这里会显示你的原生媒体库封面。","管理来源") {model.navigate(Route.Settings)}
        LazyVerticalGrid(columns=GridCells.Adaptive(if(LocalCompact.current) 145.dp else 235.dp),horizontalArrangement=Arrangement.spacedBy(18.dp),
            verticalArrangement=Arrangement.spacedBy(20.dp),contentPadding=PaddingValues(vertical=10.dp)) {
            items(libs,key={it.key}) { lib -> LibraryCard(lib) {model.navigate(Route.Library(lib))} }
        }
    }
}

@Composable fun LibraryScreen(library:MediaEntry,onPlay:(MediaEntry,Boolean)->Unit) {
    val model=LocalAppModel.current; val page=model.pages[library.key]
    var candidate by remember(library.key) {mutableStateOf<MediaEntry?>(null)}
    var displayed by remember(library.key) {mutableStateOf<MediaEntry?>(null)}
    var folderMode by rememberSaveable(library.key) {mutableStateOf(false)}
    var sort by rememberSaveable(library.key) {mutableStateOf("DateCreated")}
    var ascending by rememberSaveable(library.key) {mutableStateOf(false)}
    var chooser by remember {mutableStateOf("")}
    LaunchedEffect(candidate) {delay(240); displayed=candidate}
    val hero=displayed ?: page?.items?.firstOrNull()
    val gridState=rememberLazyGridState()
    val mode=model.settings.artworkMode
    LaunchedEffect(folderMode,library.key) {if(folderMode) model.loadLibraryFolders(library)}
    Box(Modifier.fillMaxSize()) {
        Backdrop(hero ?: library)
        LazyVerticalGrid(state=gridState,columns=GridCells.Adaptive(if(mode=="Poster") 132.dp else if(LocalCompact.current) 160.dp else 230.dp),
            horizontalArrangement=Arrangement.spacedBy(17.dp),verticalArrangement=Arrangement.spacedBy(22.dp),
            contentPadding=PaddingValues(start=pageSidePadding,end=pageSidePadding,top=pageTopPadding,bottom=40.dp)) {
            item(key="library-hero",span={GridItemSpan(maxLineSpan)}) {Hero(hero,"媒体库 / ${library.title}",true,page?.items ?: emptyList(),{candidate=it},onPlay)}
            item(key="library-tools",span={GridItemSpan(maxLineSpan)}) {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    SectionTitle("${library.title} · 全部内容", "${page?.total ?: 0} 个条目")
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(3.dp)) {
                        item {Action("排序：${Presentation.sorts.firstOrNull {it.first==sort}?.second}") {chooser="sort"}}
                        item {Action(if(ascending) "升序排列" else "降序排列",id="library-order",active=ascending) {ascending=!ascending;model.loadLibrary(library,sort,ascending=ascending)}}
                        item {Action("字幕：${Presentation.subtitles.firstOrNull {it.first==model.settings.subtitlePreference}?.second ?: "默认"}") {chooser="subtitle"}}
                        item {Action("视图：$mode") {chooser="view"}}
                        if(library.collectionType in setOf("tvshows","mixed","homevideos") || library.type=="Folder") item {Action(if(folderMode) "按海报" else "按文件夹",active=folderMode) {folderMode=!folderMode}}
                    }
                }
            }
            if(folderMode) {
                val folders=model.folderPages[library.key]
                items(folders?.items ?: emptyList(),key={"folder:${it.key}"},span={GridItemSpan(maxLineSpan)}) {folder->LibraryLatestRow(folder,true)}
                if(folders!=null && folders.items.size<folders.total) item(span={GridItemSpan(maxLineSpan)}) {
                    Action("更多文件夹") {model.loadLibraryFolders(library,true)}
                }
                if(folders?.items?.isEmpty()==true) item(span={GridItemSpan(maxLineSpan)}) {Text("此目录没有可浏览的子文件夹",color=SunnyColors.Secondary)}
                model.errors["folders:${library.key}"]?.let {error->item(span={GridItemSpan(maxLineSpan)}) {EmptyState("目录读取失败",error,"重试") {model.loadLibraryFolders(library)}}}
            } else {
                items(page?.items ?: emptyList(),key={it.key}) {entry->
                    PosterWallCard(entry,mode) {model.navigate(Route.Detail(entry))}
                }
                if(page!=null && page.items.size<page.total) item(span={GridItemSpan(maxLineSpan)}) {
                    Action("加载更多 · ${page.items.size}/${page.total}") {model.loadLibrary(library,sort,true,ascending)}
                }
            }
            model.errors["library:${library.key}"]?.let {error->item(span={GridItemSpan(maxLineSpan)}) {EmptyState("读取失败",error,"重试") {model.loadLibrary(library,sort,ascending=ascending)}}}
        }
    }
    if(chooser.isNotEmpty()) ChoiceDialog(when(chooser) {"sort"->"排序";"subtitle"->"默认字幕";else->"展现方式"},
        when(chooser) {"sort"->Presentation.sorts;"subtitle"->listOf("default" to "跟随媒体默认")+Presentation.subtitles
            else->listOf("Poster" to "海报 · Poster","Thumb" to "背景 · Thumb","Banner" to "横幅 · Banner")},
        when(chooser) {"sort"->sort;"subtitle"->model.settings.subtitlePreference;else->mode},onDismiss={chooser=""}) {value->
        when(chooser) {"sort"->{sort=value;ascending=value=="SortName";model.loadLibrary(library,sort,ascending=ascending)}
            "subtitle"->model.saveSettings(model.settings.copy(subtitlePreference=value))
            else->model.saveSettings(model.settings.copy(artworkMode=value))}
        chooser=""
    }
}

@Composable fun DetailScreen(initial:MediaEntry,onPlay:(MediaEntry,Boolean)->Unit) {
    MediaDetailContent(initial,onPlay)
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
    Column(Modifier.fillMaxSize().padding(horizontal=pageSidePadding).padding(top=pageTopPadding)) {
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
    Column(Modifier.fillMaxSize().padding(horizontal=pageSidePadding).padding(top=pageTopPadding)) {
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
