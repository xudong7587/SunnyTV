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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.launch
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
                    if(item.isPlayable || item.type in setOf("Series","Season")) Action("播放",id="hero-play",primary=true) {onPlay(item,false)}
                    Action("查看详情",id="hero-detail") {model.navigate(Route.Detail(item))}
                }
            }
            if(tall) StableLazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(4.dp)) {
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
                        if(item.isPlayable || item.type in setOf("Series","Season")) Action(if(item.positionMs>0) "▶  继续播放" else "▶  播放",id="hero-play",primary=true,
                            onClick={onPlay(item,false)})
                        Action("查看详情",id="hero-detail",onClick={model.navigate(Route.Detail(item))})
                    }
                }
            }
            if(tall) {
                StableLazyRow(Modifier.weight(.60f),horizontalArrangement=Arrangement.spacedBy(15.dp),contentPadding=PaddingValues(4.dp)) {
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
    val libraries=feeds.flatMap { it.libraries }.distinctBy { it.key }
    val resume=feeds.flatMap { it.resume }
    val latest=feeds.flatMap { it.latest }
    val nextUp=if(model.settings.showNextUp) feeds.flatMap { it.nextUp }.distinctBy {it.key} else emptyList()
    val incoming=when(model.settings.heroMode) {"resume"->resume;"random"->model.heroCandidates;else->latest}.distinctBy {it.key}.take(7)
    var candidates by remember {mutableStateOf(incoming)}
    var selected by rememberSaveable {mutableIntStateOf(0)}
    val hero=candidates.getOrNull(selected.coerceIn(0,(candidates.size-1).coerceAtLeast(0)))
    val list=rememberLazyListState()
    val bridge=LocalNavigationBridge.current
    val scope=rememberCoroutineScope()
    val compact=LocalCompact.current
    val librariesFocus=remember {FocusRequester()}
    val motion=LocalMotion.current
    val axis=LocalTvFocusMotion.current
    val navigation=remember(list,scope,axis) {HomeFocusNavigator(list,scope,axis)}
    val sections=remember(libraries.map {it.key},nextUp.isNotEmpty()) {
        HomeFocusPlan.sections(libraries.map {it.key},nextUp.isNotEmpty())
    }
    DisposableEffect(bridge,list) {bridge?.revealTop={list.scrollToItem(0)};onDispose {bridge?.revealTop=null}}
    var heroFocused by remember {mutableStateOf(false)}
    LaunchedEffect(incoming,heroFocused,model.busy) {if(!heroFocused && !model.busy) {candidates=incoming;selected=selected.coerceIn(0,(incoming.size-1).coerceAtLeast(0))}}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var resumed by remember {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->resumed=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
        lifecycle.addObserver(observer);onDispose {lifecycle.removeObserver(observer)}
    }
    val visible by remember {derivedStateOf {list.firstVisibleItemIndex==0 && list.firstVisibleItemScrollOffset==0}}
    LaunchedEffect(heroFocused,resumed,visible,model.busy,model.message,motion.enabled,candidates.map {it.key},selected,model.settings.heroIntervalSeconds) {
        if(Presentation.canRotate(heroFocused,false,resumed,visible,model.busy,model.message.isNotEmpty(),!motion.enabled,candidates.size)) {
            delay(model.settings.heroIntervalSeconds*1000L);selected=Presentation.next(selected,1,candidates.size,true)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport=maxHeight
        val heroHeight=viewport.coerceAtLeast(if(compact) 630.dp else 400.dp)
        val heroExtentPx=with(LocalDensity.current) {(heroHeight+6.dp).toPx()}
        val navigationInset=with(LocalDensity.current) {if(LocalNavVisible.current) 84.dp.roundToPx() else 8.dp.roundToPx()}
        SideEffect {
            navigation.sections=sections
            navigation.motion=motion
            navigation.topInsetPx=navigationInset
            navigation.revealLibraries={list.moveHomePage(1,heroExtentPx,motion)}
            navigation.revealHero={list.moveHomePage(0,heroExtentPx,motion);withFrameNanos {};bridge?.enterContent()}
        }
        CompositionLocalProvider(LocalHomeFocusNavigator provides navigation) {
        StableVerticalViewport(hold={!compact && (heroFocused || navigation.moving)}) {
        LazyColumn(modifier=Modifier.testTag("home:vertical"),state=list,contentPadding=PaddingValues(bottom=36.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            if(embySources.isEmpty()) item {
                Box(Modifier.padding(top=85.dp,start=40.dp,end=40.dp)) {EmptyState("每一个夜晚，都值得好好看。","添加 Emby，保留已有海报、媒体库封面和观看进度。\n连接后即可查看推荐和观看进度。","添加媒体来源") { model.navigate(Route.Settings,root=true) }}
            } else {
                item(key="home-stage") {
                    // Keep the first row composed while crossing the hero boundary.
                    Column {
                        val colors=remember(model.settings.accentIndex,model.settings.darkTheme) {palette(model.settings)}
                        CompositionLocalProvider(LocalSunnyPalette provides colors) {
                            Box(Modifier.fillMaxWidth().height(heroHeight).graphicsLayer().testTag("home:hero")
                                .onFocusChanged {heroFocused=it.hasFocus}.focusGroup()) {
                                CinemaBackdrop(hero)
                                HomeHeroContent(hero,candidates,selected,{selected=it},
                                    if(model.settings.showResume) resume else emptyList(),onExitDown={
                                        scope.launch {
                                            axis.horizontal=false
                                            list.moveHomePage(1,heroExtentPx,motion)
                                            withFrameNanos {};librariesFocus.requestFocus()
                                        }
                                    },onPlay=onPlay)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        HomeFocusRegion(HomeFocusPlan.LIBRARIES) {
                            Column(Modifier.padding(horizontal=30.dp).padding(top=if(compact) 0.dp else 68.dp).focusGroup()) {
                                SectionTitle("我的媒体库","查看全部  ›") {model.navigate(Route.Libraries,root=true)}
                                StableLazyRow(modifier=Modifier.focusRequester(librariesFocus).focusGroup(),
                                    horizontalArrangement=Arrangement.spacedBy(16.dp),
                                    contentPadding=PaddingValues(vertical=18.dp),reserveFocusSpace=false) {
                                    items(libraries,key={it.key}) {lib->LibraryCard(lib) {model.navigate(Route.Library(lib))}}
                                }
                            }
                        }
                    }
                }
                if(nextUp.isNotEmpty()) item(key="home-next-up") {
                    HomeFocusRegion(HomeFocusPlan.NEXT_UP) {
                        Box(Modifier.padding(horizontal=30.dp)) {MediaShelf("接着看下一集",nextUp,true,onClick={model.navigate(Route.Detail(it))})}
                    }
                }
                items(libraries,key={"library-latest:${it.key}"}) {library ->
                    HomeFocusRegion(HomeFocusPlan.latest(library.key)) {
                        Box(Modifier.padding(horizontal=30.dp)) {LibraryLatestRow(library)}
                    }
                }
                embySources.forEach {source->
                    model.errors["feed:${source.id}"]?.let {error->item {EmptyState(source.name,error,"重试") {model.refresh()}}}
                    if(compact) model.feeds[source.id]?.warnings?.forEach {warning->item {Text(warning,color=SunnyColors.Secondary,fontSize=12.sp)}}
                }
            }
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
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val cell=if(LocalCompact.current) 145.dp else 235.dp
        val columns=((maxWidth-44.dp+18.dp)/(cell+18.dp)).toInt().coerceAtLeast(1)
        val state=rememberLazyGridState()
        val navigator=rememberGridFocusNavigator(state)
        LazyVerticalGrid(state=state,columns=GridCells.Adaptive(cell),horizontalArrangement=Arrangement.spacedBy(18.dp),
            verticalArrangement=Arrangement.spacedBy(20.dp),contentPadding=PaddingValues(horizontal=22.dp,vertical=22.dp)) {
            itemsIndexed(libs,key={_,lib->lib.key}) {index,lib->
                LibraryCard(lib,Modifier.gridFocusTarget(navigator,index,if(index>=columns) index-columns else null,
                    if(index+columns<libs.size) index+columns else if(index/columns<libs.lastIndex/columns) libs.lastIndex else null)) {model.navigate(Route.Library(lib))}
            }
        }
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
    val gridFocus=rememberGridFocusNavigator(gridState)
    val scope=rememberCoroutineScope()
    val mode=model.settings.libraryArtworkModes[library.key] ?: model.settings.artworkMode
    val subtitlePreference=model.settings.librarySubtitlePreferences[library.key] ?: model.settings.subtitlePreference
    val episodeContainer=library.type in setOf("Series","Season")
    val episodeLayout=model.settings.episodeLayouts[library.key] ?: "horizontal"
    val toolsEntry=remember(library.key) {FocusRequester()}
    val heroEntry=remember(library.key) {FocusRequester()}
    val moreFoldersEntry=remember(library.key) {FocusRequester()}
    val folderEntries=model.folderPages[library.key]?.items.orEmpty()
    val folderRequesters=remember(library.key,folderEntries.map {it.key}) {List(folderEntries.size) {FocusRequester()}}
    val moreFolders=folderEntries.size<(model.folderPages[library.key]?.total ?: 0)
    fun moveFolder(index:Int) {
        scope.launch {
            gridState.scrollToItem(if(index<0) 1 else 2+index)
            withFrameNanos {};withFrameNanos {}
            when {index<0->toolsEntry;index<folderRequesters.size->folderRequesters[index];else->moreFoldersEntry}.requestFocus()
        }
    }
    LaunchedEffect(folderMode,library.key) {if(folderMode) model.loadLibraryFolders(library)}
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Backdrop(hero ?: library)
        val cellSize=if(episodeContainer && episodeLayout=="numbers") 68.dp else if(mode=="Poster") 132.dp else if(LocalCompact.current) 160.dp else 230.dp
        val columns=if(episodeContainer && episodeLayout=="vertical") 1 else
            ((maxWidth-pageSidePadding*2+17.dp)/(cellSize+17.dp)).toInt().coerceAtLeast(1)
        val entries=page?.items.orEmpty()
        val more=page!=null && entries.size<page.total
        @Composable fun mediaNavigation(index:Int)=Modifier.gridFocusTarget(gridFocus,2+index,
            if(index<columns) 1 else 2+index-columns,
            if(index+columns<entries.size) 2+index+columns else
                if(index/columns<(entries.lastIndex/columns)) 2+entries.lastIndex else if(more) 2+entries.size else null)
        LazyVerticalGrid(modifier=Modifier.testTag("library:grid"),state=gridState,columns=GridCells.Adaptive(cellSize),
            horizontalArrangement=Arrangement.spacedBy(17.dp),verticalArrangement=Arrangement.spacedBy(22.dp),
            contentPadding=PaddingValues(start=pageSidePadding,end=pageSidePadding,top=pageTopPadding,bottom=40.dp)) {
            item(key="library-hero",span={GridItemSpan(maxLineSpan)}) {Box(Modifier.focusRequester(heroEntry).gridFocusTarget(gridFocus,0,null,1).onPreviewKeyEvent {
                if(it.type==KeyEventType.KeyDown && it.key==Key.DirectionDown) {
                    scope.launch {gridState.scrollToItem(1);withFrameNanos {};toolsEntry.requestFocus()};true
                } else if(it.type==KeyEventType.KeyDown && it.key==Key.DirectionUp) {
                    scope.launch {gridState.scrollToItem(0)};true
                } else false
            }) {Hero(hero,"媒体库 / ${library.title}",true,page?.items ?: emptyList(),{candidate=it},onPlay)}}
            item(key="library-tools",span={GridItemSpan(maxLineSpan)}) {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    SectionTitle("${library.title} · 全部内容", "${page?.total ?: 0} 个条目")
                    StableLazyRow(Modifier.focusRequester(toolsEntry).gridFocusTarget(gridFocus,1,null,
                        if(!folderMode && entries.isNotEmpty()) 2 else null).onPreviewKeyEvent {event->
                        if(event.type!=KeyEventType.KeyDown) false else when(event.key) {
                            Key.DirectionDown -> if(folderMode && folderEntries.isNotEmpty()) {moveFolder(0);true} else false
                            Key.DirectionUp -> if(hero!=null) {scope.launch {gridState.scrollToItem(0);withFrameNanos {};withFrameNanos {};heroEntry.requestFocus()};true} else false
                            else -> false
                        }
                    }.focusGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(3.dp)) {
                        item {Action(Presentation.sorts.firstOrNull {it.first==sort}?.second.orEmpty(),id="library-sort",active=true,icon=if(ascending) "ascending" else "descending") {chooser="sort"}}
                        item {Action("字幕偏好：${Presentation.subtitles.firstOrNull {it.first==subtitlePreference}?.second ?: "媒体默认"}") {chooser="subtitle"}}
                        if(episodeContainer) item {EpisodeLayoutButtons(library,episodeLayout)}
                        else item {Action("视图：$mode") {chooser="view"}}
                        if(!episodeContainer && Presentation.supportsFolders(library)) item {Action(if(folderMode) "按海报" else "按文件夹",id="library-folder-mode",active=folderMode) {folderMode=!folderMode}}
                    }
                }
            }
            if(folderMode) {
                val folders=model.folderPages[library.key]
                itemsIndexed(folderEntries,key={_,entry->"folder:${entry.key}"},span={_,_->GridItemSpan(maxLineSpan)}) {index,folder->
                    LibraryLatestRow(folder,true,folderRequesters[index],onUp={moveFolder(index-1)},
                        onDown=if(index<folderEntries.lastIndex || moreFolders) {{moveFolder(index+1)}} else null)
                }
                if(folders!=null && folders.items.size<folders.total) item(span={GridItemSpan(maxLineSpan)}) {
                    Action("更多文件夹",modifier=Modifier.focusRequester(moreFoldersEntry).onPreviewKeyEvent {event->
                        if(event.type==KeyEventType.KeyDown && event.key==Key.DirectionUp && folderEntries.isNotEmpty()) {moveFolder(folderEntries.lastIndex);true} else false
                    }) {model.loadLibraryFolders(library,true)}
                }
                if(folders?.items?.isEmpty()==true) item(span={GridItemSpan(maxLineSpan)}) {Text("此目录没有可浏览的子文件夹",color=SunnyColors.Secondary)}
                model.errors["folders:${library.key}"]?.let {error->item(span={GridItemSpan(maxLineSpan)}) {EmptyState("目录读取失败",error,"重试") {model.loadLibraryFolders(library)}}}
            } else {
                if(episodeContainer) when(episodeLayout) {
                    "vertical" -> itemsIndexed(entries,key={_,entry->entry.key},span={_,_->GridItemSpan(maxLineSpan)}) {index,entry->EpisodeListCard(entry,mediaNavigation(index))}
                    "numbers" -> itemsIndexed(entries,key={_,entry->entry.key}) {index,entry->EpisodeNumber(entry,index,mediaNavigation(index).fillMaxWidth())}
                    else -> item(span={GridItemSpan(maxLineSpan)}) {Box(Modifier.gridFocusTarget(gridFocus,2,1,if(more) 3 else null)) {EpisodeHorizontal(entries)}}
                } else itemsIndexed(entries,key={_,entry->entry.key}) {index,entry->
                    PosterWallCard(entry,mode,mediaNavigation(index)) {model.navigate(Route.Detail(entry))}
                }
                if(page!=null && page.items.size<page.total) item(span={GridItemSpan(maxLineSpan)}) {
                    val horizontal=episodeContainer && episodeLayout=="horizontal"
                    Action("加载更多 · ${page.items.size}/${page.total}",id="library-more",modifier=Modifier.gridFocusTarget(gridFocus,
                        if(horizontal) 3 else 2+entries.size,if(horizontal) 2 else 2+entries.lastIndex,null)) {model.loadLibrary(library,sort,true,ascending)}
                }
            }
            model.errors["library:${library.key}"]?.let {error->item(span={GridItemSpan(maxLineSpan)}) {EmptyState("读取失败",error,"重试") {model.loadLibrary(library,sort,ascending=ascending)}}}
        }
    }
    if(chooser.isNotEmpty()) ChoiceDialog(when(chooser) {"sort"->"排序";"subtitle"->"字幕优先级（未匹配时跟随媒体默认）";else->"展现方式"},
        when(chooser) {"sort"->Presentation.sorts.map {(key,label)->key to if(key==sort) "$label · ${if(ascending) "升序" else "降序"}（再点切换）" else label};"subtitle"->listOf("default" to "跟随媒体默认")+Presentation.subtitles
            else->listOf("Poster" to "海报 · Poster","Thumb" to "背景 · Thumb","Banner" to "横幅 · Banner")},
        when(chooser) {"sort"->sort;"subtitle"->subtitlePreference;else->mode},onDismiss={chooser=""}) {value->
        when(chooser) {"sort"->{ascending=if(sort==value) !ascending else value=="SortName";sort=value;model.loadLibrary(library,sort,ascending=ascending)}
            "subtitle"->model.saveSettings(model.settings.copy(librarySubtitlePreferences=model.settings.librarySubtitlePreferences+(library.key to value)))
            else->model.saveSettings(model.settings.copy(libraryArtworkModes=model.settings.libraryArtworkModes+(library.key to value)))}
        chooser=""
    }
}

@Composable fun DetailScreen(initial:MediaEntry,onPlay:(MediaEntry,Boolean)->Unit) {
    if(initial.type=="Person") PersonScreen(initial) else MediaDetailContent(initial,onPlay)
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
