package io.github.xudong7587.sunnytv.feature.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.storage.PinyinIndex
import io.github.xudong7587.sunnytv.feature.*
import kotlinx.coroutines.delay
import coil.request.ImageRequest

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

/**
 * Library recommendation bar. It uses the same "one large block plus small blocks" carousel as
 * the home hero, while the left column keeps the existing play / detail buttons. Candidates are a
 * server-side random sample, so the strip changes on every refresh.
 */
@Composable fun LibraryHero(library:MediaEntry,hero:MediaEntry?,candidates:List<MediaEntry>,selected:Int,
    onSelect:(Int)->Unit,onPlay:(MediaEntry,Boolean)->Unit,onExitDown:()->Unit,height:Dp=330.dp) {
    val model=LocalAppModel.current
    val compact=LocalCompact.current
    // Touch layouts need less room under the banner: the space the pinned bar would occupy is a
    // dead gap when the page simply scrolls, so phone/tablet keeps a compact bottom margin.
    val touchLayout=LocalHandset.current||LocalTouchFirst.current
    val heroBottom=if(touchLayout) 10.dp else 18.dp
    val entry=hero
    // Leaving the carousel to the left hands focus back to the first action button, like home.
    val actionFocus=remember(library.key) {FocusRequester()}
    val playable=entry!=null && (entry.isPlayable || entry.type in setOf("Series","Season"))
    @Composable fun Copy() {
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("媒体库 / ${library.title}",color=SunnyColors.Accent,fontSize=11.sp,letterSpacing=1.2.sp,
                maxLines=1,overflow=TextOverflow.Ellipsis)
            if(entry==null) {
                Text(library.title,color=SunnyColors.Text,fontSize=if(compact) 28.sp else 38.sp,fontWeight=FontWeight.Bold,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
                Text("正在读取推荐内容…",color=SunnyColors.Secondary,fontSize=13.sp)
            } else {
                MediaTitle(entry,if(compact) 28.sp else 38.sp)
                Text(entry.subtitle + if(entry.rating>0) "    ★ %.1f".format(entry.rating) else "",color=SunnyColors.Secondary,fontSize=13.sp)
                // Acceptance round 3: the description stays plain body text so it reads on a bright
                // backdrop instead of picking up the theme's secondary tone.
                Text(entry.overview.ifBlank {"来自你的 Emby 媒体库"},color=SunnyColors.Text,fontSize=13.sp,lineHeight=21.sp,
                    maxLines=if(compact) 2 else 3,overflow=TextOverflow.Ellipsis)
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(playable) Action(
                        if(entry.positionMs>0) "▶  继续播放" else "▶  播放",id="library-hero-play",primary=true,
                        modifier=Modifier.focusRequester(actionFocus)) {onPlay(entry,false)}
                    Action("查看详情",id="library-hero-detail",
                        modifier=if(playable) Modifier else Modifier.focusRequester(actionFocus)) {model.navigate(Route.Detail(entry))}
                }
                // Same hint as the home hero, so the two banners behave and read alike.
                Text("向下查看更多媒体  ↓",color=SunnyColors.Secondary,fontSize=11.sp)
            }
        }
    }
    val exitLeft=(if(candidates.isNotEmpty()) {{runCatching {actionFocus.requestFocus()};Unit}} else null)
    // The banner fills the first screen exactly like the home hero, so the media blocks sit where
    // home puts them and the title/tool row starts on the next screen.
    // heightIn(min) instead of a fixed height: the banner always fills the first screen like home,
    // but if its own content is taller it grows instead of drawing over the pinned tool row.
    if(compact) Column(Modifier.fillMaxWidth().heightIn(min=height).padding(top=6.dp,bottom=heroBottom),
        verticalArrangement=Arrangement.Bottom) {
        Copy()
        Spacer(Modifier.height(18.dp))
        RotatingHeroCards(candidates,selected,onSelect,id="library-carousel",onExitLeft=exitLeft,onExitDown=onExitDown)
    } else Row(Modifier.fillMaxWidth().heightIn(min=height).padding(top=4.dp,bottom=heroBottom),verticalAlignment=Alignment.Bottom,
        horizontalArrangement=Arrangement.spacedBy(26.dp)) {
        Box(Modifier.weight(.42f)) {Copy()}
        RotatingHeroCards(candidates,selected,onSelect,Modifier.weight(.58f),id="library-carousel",
            onExitLeft=exitLeft,onExitDown=onExitDown)
    }
}

@Composable fun HomeScreen(onPlay:(MediaEntry,Boolean)->Unit) {
    val model=LocalAppModel.current
    // One source at a time: the home page never merges several Emby servers.
    val embySources=model.activeEmbySources()
    val feeds=embySources.mapNotNull { model.feeds[it.id] }
    val libraries=feeds.flatMap { it.libraries }.distinctBy { it.key }
    val resume=feeds.flatMap { it.resume }
    val latest=feeds.flatMap { it.latest }
    val nextUp=if(model.settings.showNextUp) feeds.flatMap { it.nextUp }.distinctBy {it.key} else emptyList()
    val incoming=when(model.settings.heroMode) {"resume"->resume;"random"->model.heroCandidates.ifEmpty {latest};else->latest}.distinctBy {it.key}.take(7)
    var candidates by remember {mutableStateOf(incoming)}
    var selected by rememberSaveable {mutableIntStateOf(0)}
    val hero=candidates.getOrNull(selected.coerceIn(0,(candidates.size-1).coerceAtLeast(0)))
    val compact=LocalCompact.current
    val handset=LocalHandset.current
    val touchFirst=LocalTouchFirst.current
    // Any touch-first device (phone or tablet, either orientation) keeps one touch-scrollable list,
    // so a swipe always reaches "我的媒体库" instead of stopping on the TV-only layered hero.
    val phoneFlow=compact||handset||touchFirst
    val list=rememberLazyListState()
    // TV keeps hero and the first home-content viewport composed at the same time. Crossing the
    // boundary is then a GPU translation only; no LazyColumn layout/image decode is introduced
    // halfway through the animation.
    val homeStage=remember {androidx.compose.animation.core.Animatable(0f)}
    val bridge=LocalNavigationBridge.current
    val scope=rememberCoroutineScope()
    val librariesFocus=remember {FocusRequester()}
    val motion=LocalMotion.current
    val axis=LocalTvFocusMotion.current
    val navigation=remember(list,scope,axis,phoneFlow) {HomeFocusNavigator(list,scope,axis,if(phoneFlow) 0 else 1)}
    val sections=remember(libraries.map {it.key},nextUp.isNotEmpty()) {
        HomeFocusPlan.sections(libraries.map {it.key},nextUp.isNotEmpty())
    }
    DisposableEffect(bridge,list,compact) {
        bridge?.revealTop={
            list.scrollToItem(0)
            if(!compact) homeStage.snapTo(0f)
        }
        onDispose {bridge?.revealTop=null}
    }
    var heroFocused by remember {mutableStateOf(false)}
    var heroTransitioning by remember {mutableStateOf(false)}
    var appliedHeroRevision by remember {mutableIntStateOf(-1)}
    var carouselOffset by remember {mutableIntStateOf(0)}
    // A manual refresh rotates the shown sample, so "最新入库 / 继续观看" also visibly changes.
    val heroDisplay=remember(incoming,carouselOffset) {
        if(incoming.size>1) incoming.drop(carouselOffset%incoming.size)+incoming.take(carouselOffset%incoming.size) else incoming
    }
    LaunchedEffect(heroDisplay,heroFocused,model.busy,model.heroRevision) {
        // A manual refresh (top "Up" / pull-down) must show its new sample even while the carousel
        // still holds focus; the automatic feed update keeps the old behaviour.
        val manual=model.heroRevision!=appliedHeroRevision
        if(!model.busy && (!heroFocused || manual)) {
            if(manual) carouselOffset++
            candidates=heroDisplay
            selected=selected.coerceIn(0,(heroDisplay.size-1).coerceAtLeast(0))
            appliedHeroRevision=model.heroRevision
        }
    }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var resumed by remember {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->resumed=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
        lifecycle.addObserver(observer);onDispose {lifecycle.removeObserver(observer)}
    }
    val heroVisible by remember(compact,list,homeStage) {derivedStateOf {
        if(phoneFlow) list.firstVisibleItemIndex==0 && list.firstVisibleItemScrollOffset==0
        else homeStage.value<=.001f
    }}
    LaunchedEffect(heroFocused,resumed,heroVisible,model.busy,model.message,motion.enabled,candidates.map {it.key},selected,model.settings.heroIntervalSeconds) {
        if(Presentation.canRotate(heroFocused,false,resumed,heroVisible,model.busy,model.message.isNotEmpty(),!motion.enabled,candidates.size)) {
            delay(model.settings.heroIntervalSeconds*1000L);selected=Presentation.next(selected,1,candidates.size,true)
        }
    }

    // Explicitly warm library artwork. The TV layout below is also kept composed off-screen, so
    // its first row is measured and its normal ArtworkView requests start before Down is pressed.
    val rootView=LocalView.current
    val density=LocalDensity.current.density
    val preloadKeys=libraries.take(6).map {it.key}
    LaunchedEffect(preloadKeys,model.settings.highQualityArtwork,model.settings.performanceMode,rootView.width,rootView.height) {
        if(libraries.isEmpty()) return@LaunchedEffect
        delay(80)
        val lean=model.app.lean(model.settings)
        val densityScale=(density/2f).coerceAtLeast(1f)
        val maxPixels=if(lean) 1280 else 3840
        val actualWidth=(420*densityScale*(if(model.settings.highQualityArtwork) 1.25f else 1f)).toInt()
            .coerceAtMost(minOf(maxPixels,maxOf(rootView.width,rootView.height,1280))).coerceAtLeast(64)
        libraries.take(6).forEach {library->
            val source=embySources.firstOrNull {it.id==library.sourceId} ?: return@forEach
            val art=MediaLogic.libraryArtwork(library) ?: return@forEach
            val request=ImageRequest.Builder(rootView.context).data(model.app.emby(source).imageUrl(art,actualWidth))
                .size(actualWidth,actualWidth*9/16)
                .memoryCacheKey("${source.id}:${art.itemId}:${art.type}:${art.tag}:$actualWidth")
                .diskCacheKey("${source.id}:${art.itemId}:${art.type}:${art.tag}:$actualWidth")
                .crossfade(false).build()
            model.app.images(source).enqueue(request)
        }
    }
    // Warm the visible home shelves up front. loadLatest is cache-aware and bounded by the
    // existing artwork/feed semaphore, so this still fetches only ten items per library and never
    // enumerates the complete collection while the user is moving between rows.
    val preloadLatestKeys=libraries.take(6).map {it.key}
    LaunchedEffect(preloadLatestKeys) {libraries.take(6).forEach {model.loadLatest(it)}}

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val viewport=maxHeight
        val heroHeight=if(compact) viewport.coerceAtLeast(630.dp) else viewport
        val heroExtentPx=with(LocalDensity.current) {(heroHeight+6.dp).toPx()}
        val viewportPx=with(LocalDensity.current) {viewport.toPx()}
        val navigationInset=with(LocalDensity.current) {if(LocalNavVisible.current) 84.dp.roundToPx() else 8.dp.roundToPx()}

        suspend fun revealTvLibraries() {
            // While hero fully covers the viewport we can normalize the hidden content position
            // without any visible snap, then only translate two already-rendered layers.
            list.scrollToItem(0)
            if(motion.enabled) homeStage.animateTo(1f,motion.fade(420)) else homeStage.snapTo(1f)
        }
        suspend fun revealTvHero() {
            if(motion.enabled) homeStage.animateTo(0f,motion.fade(420)) else homeStage.snapTo(0f)
            // Content is hidden now; prepare its top for the next downward transition.
            list.scrollToItem(0)
            withFrameNanos {};bridge?.enterContent()
        }
        SideEffect {
            navigation.sections=sections
            navigation.motion=motion
            navigation.topInsetPx=navigationInset
            if(phoneFlow) {
                navigation.revealLibraries={list.moveHomePage(1,heroExtentPx,motion)}
                navigation.revealHero={list.moveHomePage(0,heroExtentPx,motion);withFrameNanos {};bridge?.enterContent()}
            } else {
                navigation.revealLibraries={revealTvLibraries()}
                navigation.revealHero={revealTvHero()}
            }
        }

        @Composable fun HeroPane(onExitDown:()->Unit) {
            val colors=remember(model.settings.accentIndex,model.settings.darkTheme) {palette(model.settings)}
            CompositionLocalProvider(LocalSunnyPalette provides colors) {
                Box(Modifier.fillMaxWidth().height(heroHeight).testTag("home:hero")
                    // Pull down on the banner itself to re-roll the carousel.
                    .pullDownOnBanner(enabled=handset||touchFirst,
                        atTop={list.firstVisibleItemIndex==0 && list.firstVisibleItemScrollOffset==0}) {
                        if(!model.carouselRefreshing) model.refreshCarousel()
                    }
                    .onFocusChanged {heroFocused=it.hasFocus}.focusGroup()) {
                    CinemaBackdrop(hero)
                    HomeHeroContent(hero,candidates,selected,{selected=it},
                        if(model.settings.showResume) resume else emptyList(),onExitDown=onExitDown,onPlay=onPlay)
                }
            }
        }

        @Composable fun HomeFeed(includeHero:Boolean,onHeroExitDown:()->Unit) {
            StableVerticalViewport(hold={!compact && (navigation.moving || heroTransitioning)}) {
                LazyColumn(modifier=Modifier.onGloballyPositioned {
                    val p=it.positionInRoot();navigation.viewport=Rect(p.x,p.y,p.x+it.size.width,p.y+it.size.height)
                }.testTag("home:vertical")
                    .pullDownToRefresh(enabled=handset||touchFirst,
                        atTop={list.firstVisibleItemIndex==0 && list.firstVisibleItemScrollOffset==0}) {
                        if(!model.carouselRefreshing) model.refreshCarousel()
                    },state=list,contentPadding=PaddingValues(bottom=36.dp),
                    verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    if(embySources.isEmpty()) item {
                        Box(Modifier.padding(top=85.dp,start=40.dp,end=40.dp)) {
                            EmptyState("每一个夜晚，都值得好好看。","添加 Emby，保留已有海报、媒体库封面和观看进度。\n连接后即可查看推荐和观看进度。","添加媒体来源") {
                                model.navigate(Route.Settings,root=true)
                            }
                        }
                    } else {
                        if(includeHero) item(key="home-hero") {HeroPane(onHeroExitDown)}
                        item(key="home-libraries") {
                            HomeFocusRegion(HomeFocusPlan.LIBRARIES) {
                                // The row clips its own bounds, so it keeps the shadow gutter inside
                                // and the outer padding gives it back: heading and cards stay on 30dp.
                                Column(Modifier.padding(horizontal=(30.dp-FocusShadowGutter)).padding(top=if(compact) 0.dp else 42.dp).focusGroup()) {
                                    Box(Modifier.padding(start=FocusShadowGutter)) {
                                        SectionTitle("我的媒体库","查看全部  ›") {model.navigate(Route.Libraries,root=true)}
                                    }
                                    StableLazyRow(modifier=Modifier.focusGroup(),horizontalArrangement=Arrangement.spacedBy(16.dp),
                                        contentPadding=PaddingValues(start=FocusShadowGutter,end=FocusShadowGutter,
                                            top=FocusShadowTopGutter,bottom=FocusShadowBottomGutter),reserveFocusSpace=false) {
                                        itemsIndexed(libraries,key={_,it->it.key}) {index,lib->
                                            LibraryCard(lib,if(index==0) Modifier.focusRequester(librariesFocus) else Modifier,
                                                onLongPress={model.showItemActions(lib)}) {model.navigate(Route.Library(lib))}
                                        }
                                    }
                                }
                            }
                        }
                        if(nextUp.isNotEmpty()) item(key="home-next-up") {
                            HomeFocusRegion(HomeFocusPlan.NEXT_UP) {
                                Box(Modifier.padding(horizontal=(30.dp-FocusShadowGutter))) {
                                    MediaShelf("接着看下一集",nextUp,true,focusGutter=FocusShadowGutter,onClick={model.navigate(Route.Detail(it))})
                                }
                            }
                        }
                        items(libraries,key={"library-latest:${it.key}"}) {library ->
                            HomeFocusRegion(HomeFocusPlan.latest(library.key)) {
                                Box(Modifier.padding(horizontal=(30.dp-FocusShadowGutter))) {
                                    LibraryLatestRow(library,headingInset=FocusShadowGutter)
                                }
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

        CompositionLocalProvider(LocalHomeFocusNavigator provides navigation) {
            if(phoneFlow || embySources.isEmpty()) {
                val down:()->Unit={
                    if(!heroTransitioning) scope.launch {
                        heroTransitioning=true
                        try {
                            axis.horizontal=false
                            list.moveHomePage(1,heroExtentPx,motion)
                            withFrameNanos {};librariesFocus.requestFocus()
                            withFrameNanos {};withFrameNanos {}
                        } finally {heroTransitioning=false}
                    }
                }
                HomeFeed(includeHero=true,onHeroExitDown=down)
            } else {
                val down:()->Unit={
                    if(!heroTransitioning) scope.launch {
                        heroTransitioning=true
                        try {
                            axis.horizontal=false
                            revealTvLibraries()
                            withFrameNanos {};librariesFocus.requestFocus()
                            // Keep bring-into-view suppressed until focus has settled.
                            withFrameNanos {}
                        } finally {heroTransitioning=false}
                    }
                }
                // Hero occupies the complete viewport. The next page lives in a separate, already
                // composed viewport translated exactly one screen below it, so no title leaks at rest.
                Box(Modifier.fillMaxSize().graphicsLayer {translationY=-homeStage.value*viewportPx}) {
                    HeroPane(down)
                }
                Box(Modifier.fillMaxSize().graphicsLayer {translationY=(1f-homeStage.value)*viewportPx}) {
                    HomeFeed(includeHero=false,onHeroExitDown=down)
                }
            }
        }
        // Same brand mark as the player, so "reading" looks identical everywhere.
        if(model.carouselRefreshing) SunnyBrandLoading(true,Modifier.matchParentSize(),scopeTag="home:loading",scrim=true)
    }
}

@Composable fun LibrariesScreen() {
    val model=LocalAppModel.current
    val libs=model.activeEmbySources().flatMap {model.feeds[it.id]?.libraries ?: emptyList()}
    if(libs.isEmpty()) Column(Modifier.fillMaxSize().padding(horizontal=pageSidePadding).padding(top=pageTopPadding)) {
        SectionTitle("所有媒体库")
        EmptyState("这里还没有媒体库","添加 Emby 后，这里会显示你的原生媒体库封面。","管理来源") {model.navigate(Route.Settings)}
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cell=if(LocalCompact.current) 145.dp else 235.dp
        val columns=((maxWidth-pageSidePadding*2+18.dp)/(cell+18.dp)).toInt().coerceAtLeast(1)
        val state=rememberLazyGridState()
        val navigator=rememberGridFocusNavigator(state)
        // The title is a grid item, so it scrolls up together with the library artwork instead of
        // masking the cards at the top of the page.
        LazyVerticalGrid(state=state,columns=GridCells.Adaptive(cell),horizontalArrangement=Arrangement.spacedBy(18.dp),
            verticalArrangement=Arrangement.spacedBy(20.dp),
            contentPadding=PaddingValues(start=pageSidePadding,end=pageSidePadding,top=pageTopPadding,bottom=40.dp)) {
            item(key="libraries-title",span={GridItemSpan(maxLineSpan)}) {SectionTitle("所有媒体库")}
            itemsIndexed(libs,key={_,lib->lib.key}) {index,lib->
                val slot=index+1
                LibraryCard(lib,Modifier.gridFocusTarget(navigator,slot,
                    if(index>=columns) slot-columns else null,
                    if(index+columns<libs.size) slot+columns else if(index/columns<libs.lastIndex/columns) libs.size else null),
                    onLongPress={model.showItemActions(lib)}) {model.navigate(Route.Library(lib))}
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
    var heroSelected by rememberSaveable(library.key) {mutableIntStateOf(0)}
    val recommendations=model.recommendations[library.key].orEmpty()
    // The random recommendation strip is the default; fall back to the loaded page while it loads.
    val carousel=recommendations.ifEmpty {page?.items.orEmpty().take(7)}
    LaunchedEffect(library.key) {model.loadRecommendations(library)}
    LaunchedEffect(carousel) {heroSelected=heroSelected.coerceIn(0,(carousel.size-1).coerceAtLeast(0))}
    // A manual refresh must show a different poster even when the server returns the same sample.
    LaunchedEffect(model.carouselRevision[library.key]) {
        if((model.carouselRevision[library.key] ?: 0)>0 && carousel.size>1) {
            heroSelected=(heroSelected+1)%carousel.size
            carousel.getOrNull(heroSelected)?.let {candidate=it}
        }
    }
    LaunchedEffect(candidate) {delay(240); displayed=candidate}
    val hero=displayed ?: carousel.getOrNull(heroSelected) ?: page?.items?.firstOrNull()
    val gridState=rememberLazyGridState()
    val gridFocus=rememberGridFocusNavigator(gridState)
    val scope=rememberCoroutineScope()
    val mode=model.settings.libraryArtworkModes[library.key] ?: model.settings.artworkMode
    val subtitlePreference=model.settings.librarySubtitlePreferences[library.key] ?: model.settings.subtitlePreference
    val episodeContainer=library.type in setOf("Series","Season")
    val episodeLayout=model.settings.episodeLayouts[library.key] ?: "horizontal"
    val toolsEntry=remember(library.key) {FocusRequester()}
    // Focusing a concrete button is reliable; focusing the row container is not.
    val sortEntry=remember(library.key) {FocusRequester()}
    val heroEntry=remember(library.key) {FocusRequester()}
    val moreFoldersEntry=remember(library.key) {FocusRequester()}
    val folderEntries=model.folderPages[library.key]?.items.orEmpty()
    val folderRequesters=remember(library.key,folderEntries.map {it.key}) {List(folderEntries.size) {FocusRequester()}}
    val moreFolders=folderEntries.size<(model.folderPages[library.key]?.total ?: 0)
    val density=LocalDensity.current
    val motion=LocalMotion.current
    val pinnedTop=with(density) {pageTopPadding.toPx()}
    // Touch layouts (phone/tablet) scroll the page freely and never align the tool row under the
    // pinned bar, so the bar's full inset only reads as a dead gap between the banner and the
    // media area. Televisions keep the exact inset the banner -> media-area switch aligns against.
    val touchLayout=LocalHandset.current||LocalTouchFirst.current
    val toolsTopInset=if(touchLayout) 8.dp else pageTopPadding
    val toolsTopInsetPx=with(density) {toolsTopInset.roundToPx()}
    var toolsHeightPx by remember(library.key) {mutableIntStateOf(with(density) {62.dp.roundToPx()})}
    SideEffect {
        gridFocus.topInset={0f}
        gridFocus.beforeMove={index->
            when(index) {
                // One animation to the banner's own offset. Revealing it row by row braked to a
                // full stop and re-accelerated halfway (a visible hitch).
                0 -> {if(motion.enabled) gridState.animateScrollToItem(0) else gridState.scrollToItem(0);true}
                1 -> true // Already visible above the poster wall: focus it without extra scrolling.
                else -> false
            }
        }
    }
    var folderMoving by remember {mutableStateOf(false)}
    var toolsEntering by remember {mutableStateOf(false)}
    var toolsSettling by remember(library.key) {mutableStateOf(false)}
    /**
     * Banner -> tool row switch, built like the home hero -> libraries switch: one controlled scroll
     * that lands the row at the top inset, then focus. Nothing bounces and nothing gets stuck.
     */
    fun enterTools() {
        if(toolsEntering) return
        toolsEntering=true
        scope.launch {
            try {
                // Hold the viewport for the whole transition: otherwise the focus request's own
                // bring-into-view cancels the scroll animation halfway (the visible "stall").
                toolsSettling=true
                coroutineScope {
                    // One scroll lands the tool row flush at the very top (LazyGrid draws the first
                    // visible item below its contentPadding, so that inset is added here), while the
                    // focus request runs in parallel: the button's own animation and the area switch
                    // therefore start in the same frame instead of one after the other.
                    // Landing offset = the row's own top inset: on television that keeps the tool
                    // row flush at the top (its content below the pinned bar), and on touch it puts
                    // the content in the same place without the dead gap.
                    val paddingPx=toolsTopInsetPx
                    val scrolling=launch {
                        if(motion.enabled) gridState.animateScrollToItem(1,paddingPx) else gridState.scrollToItem(1,paddingPx)
                    }
                    // The row is composed part way through the scroll, so focus is retried per frame.
                    for(attempt in 0 until 24) {
                        withFrameNanos {}
                        if(runCatching {sortEntry.requestFocus()}.getOrDefault(false)) break
                    }
                    scrolling.join()
                }
                // Keep the hold two more frames so the focused button cannot nudge the page again.
                withFrameNanos {}
                withFrameNanos {}
            } finally {toolsSettling=false;toolsEntering=false}
        }
    }
    var toolsHasFocus by remember(library.key) {mutableStateOf(false)}
    LaunchedEffect(toolsHasFocus) {
        if(toolsHasFocus && !toolsEntering) {
            // Suppress Compose's delayed automatic bring-into-view for the first couple of frames.
            // Entering the toolbar from the hero should change focus only, not nudge the page.
            toolsSettling=true
            withFrameNanos { }
            withFrameNanos { }
            toolsSettling=false
        } else if(!toolsEntering) toolsSettling=false
    }
    var toolsLeaving by remember(library.key) {mutableStateOf(false)}
    /**
     * Media area -> banner, built like [enterTools] in the other direction: one controlled scroll to
     * the banner's own offset with the focus request running in parallel, and the viewport held for
     * the whole transition so nothing re-scrolls afterwards.
     */
    fun leaveToolsForBanner() {
        if(toolsEntering || toolsLeaving) return
        toolsLeaving=true
        scope.launch {
            try {
                toolsSettling=true
                coroutineScope {
                    val scrolling=launch {
                        if(motion.enabled) gridState.animateScrollToItem(0) else gridState.scrollToItem(0)
                    }
                    for(attempt in 0 until 24) {
                        withFrameNanos {}
                        if(runCatching {heroEntry.requestFocus()}.getOrDefault(false)) break
                    }
                    scrolling.join()
                }
                withFrameNanos {}
                withFrameNanos {}
            } finally {toolsSettling=false;toolsLeaving=false}
        }
    }
    fun moveFolder(index:Int) {
        if(folderMoving) return
        folderMoving=true
        scope.launch {
            try {
                // Folders are media-area content: land them just below the top bar, never below the
                // (scrolling) tool row, otherwise a strip of the banner stays above them.
                // Going back up to the tool row uses one scroll to that same landing position
                // instead of letting the focus request snap the page several frames later.
                if(index<0) {
                    val paddingPx=toolsTopInsetPx
                    if(motion.enabled) gridState.animateScrollToItem(1,paddingPx) else gridState.scrollToItem(1,paddingPx)
                } else gridState.revealItem(2+index,motion,0f)
                repeat(20) {
                    withFrameNanos { }
                    val target=when {index<0->sortEntry;index<folderRequesters.size->folderRequesters[index];else->moreFoldersEntry}
                    if(runCatching {target.requestFocus()}.getOrDefault(false)) return@launch
                }
            } finally {folderMoving=false}
        }
    }
    LaunchedEffect(folderMode,library.key) {if(folderMode) model.loadLibraryFolders(library)}
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        // Same mask/gradient treatment as the home hero.
        CinemaBackdrop(hero ?: library)
        val bannerHeight=(maxHeight-pageTopPadding).coerceAtLeast(240.dp)
        val cellSize=if(episodeContainer && episodeLayout=="numbers") 68.dp else if(mode=="Poster") 132.dp else if(LocalCompact.current) 160.dp else 230.dp
        val columns=if(episodeContainer && episodeLayout=="vertical") 1 else
            ((maxWidth-pageSidePadding*2+17.dp)/(cellSize+17.dp)).toInt().coerceAtLeast(1)
        val entries=page?.items.orEmpty()
        val more=page!=null && entries.size<page.total
        // Before the first Emby page returns, reserve a few real grid rows instead of flashing an
        // empty wall. Once total is known, the lazy grid represents every server slot while only
        // composing what reaches the viewport.
        val totalSlots=if(!episodeContainer && page==null) (columns*3).coerceAtLeast(12)
            else (page?.total ?: entries.size).coerceAtLeast(entries.size)
        val navigationCount=if(episodeContainer) entries.size else totalSlots
        @Composable fun mediaNavigation(index:Int)=Modifier.gridFocusTarget(gridFocus,2+index,
            if(index<columns) 1 else 2+index-columns,
            if(index+columns<navigationCount) 2+index+columns else
                if(index/columns<((navigationCount-1).coerceAtLeast(0)/columns)) 2+(navigationCount-1) else null)
        // 0.dp inset: the media area switches in as a whole screen, so a focused row may sit flush
        // at the very top (the banner is fully scrolled away above it).
        StableVerticalViewport(hold={gridFocus.moving || folderMoving || toolsSettling},topInsetOverride=0.dp) {
        LazyVerticalGrid(modifier=Modifier.testTag("library:grid")
            .pullDownToRefresh(enabled=LocalHandset.current||LocalTouchFirst.current,
                atTop={gridState.firstVisibleItemIndex==0 && gridState.firstVisibleItemScrollOffset==0}) {
                if(!model.carouselRefreshing) model.refreshLibraryCarousel(library)
            },state=gridState,columns=GridCells.Adaptive(cellSize),
            horizontalArrangement=Arrangement.spacedBy(17.dp),verticalArrangement=Arrangement.spacedBy(22.dp),
            contentPadding=PaddingValues(start=pageSidePadding,end=pageSidePadding,top=pageTopPadding,bottom=40.dp)) {
            item(key="library-hero",span={GridItemSpan(maxLineSpan)}) {Box(Modifier.clipToBounds().focusRequester(heroEntry)
                // Preview handlers run outermost-first: this one must sit before gridFocusTarget,
                // otherwise that grid handler consumes Down and the banner->tool switch never runs.
                .onPreviewKeyEvent {
                if(it.type==KeyEventType.KeyDown && it.key==Key.DirectionDown) {
                    enterTools();true
                } else false
            }.gridFocusTarget(gridFocus,0,null,1)
                .pullDownOnBanner(enabled=LocalHandset.current||LocalTouchFirst.current,
                atTop={gridState.firstVisibleItemIndex==0 && gridState.firstVisibleItemScrollOffset==0}) {
                if(!model.carouselRefreshing) model.refreshLibraryCarousel(library)
            }) {LibraryHero(library,hero,carousel,heroSelected,{index->heroSelected=index;carousel.getOrNull(index)?.let {candidate=it}},onPlay,
                onExitDown={enterTools()},height=bannerHeight)}}
            // The title/tool row is an ordinary item right below the banner: it scrolls with the
            // media wall, is never pinned and can never be drawn over the banner.
            item(key="library-tools",span={GridItemSpan(maxLineSpan)}) {
                // The row carries its own top inset: when the grid aligns this item to the viewport
                // top, the title/tools end up right below the pinned bar with nothing above them.
                // The row itself is the top of the media area: no inset block and no opaque slab,
                // it simply starts at the very top of the screen once the grid is aligned to it.
                // The inset keeps the title/buttons clear of the pinned bar; nothing of the banner
                // can show above because the grid is aligned past it.
                Row(Modifier.fillMaxWidth().padding(horizontal=pageSidePadding).padding(top=toolsTopInset)
                    .onSizeChanged {if(toolsHeightPx!=it.height) toolsHeightPx=it.height}
                    .testTag("library:pinned-tools"),verticalAlignment=Alignment.CenterVertically) {
                    Row(Modifier.widthIn(max=if(LocalCompact.current) 190.dp else 360.dp).padding(vertical=4.dp),
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("${library.title} · 全部内容",color=SunnyColors.Text,fontWeight=FontWeight.SemiBold,fontSize=18.sp,
                            maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,fill=false))
                        if(!LocalCompact.current) Text("${page?.total ?: 0} 个条目",color=SunnyColors.Secondary,fontSize=11.sp,maxLines=1)
                    }
                    Spacer(Modifier.width(18.dp))
                    StableLazyRow(Modifier.weight(1f).focusRequester(toolsEntry).gridFocusTarget(gridFocus,1,null,
                        if(!folderMode && totalSlots>0) 2 else null).onFocusChanged {toolsHasFocus=it.hasFocus}.onPreviewKeyEvent {event->
                        if(event.type!=KeyEventType.KeyDown) false else when(event.key) {
                            Key.DirectionDown -> if(folderMode && folderEntries.isNotEmpty()) {moveFolder(0);true} else false
                            // Up from the tool row returns to the banner carousel (single scroll).
                            Key.DirectionUp -> if(hero!=null) {leaveToolsForBanner();true} else false
                            else -> false
                        }
                    }.focusGroup(),horizontalArrangement=Arrangement.spacedBy(6.dp),
                        contentPadding=PaddingValues(start=FocusShadowGutter,end=FocusShadowGutter,
                            top=FocusShadowTopGutter,bottom=FocusShadowBottomGutter),
                        reserveFocusSpace=false) {
                        item {Action(Presentation.sorts.firstOrNull {it.first==sort}?.second.orEmpty(),id="library-sort",
                            modifier=Modifier.focusRequester(sortEntry),
                            collapseWhenIdle=true,
                            // The direction lives on the left as one equal-height up/down pair: the
                            // active direction is bright, the other one faint. 随机 has no direction.
                            leading={ink->if(sort=="Random") LineIcon("shuffle",ink) else SortDirectionArrows(ascending,ink)}) {chooser="sort"}}
                        item {Action("字幕偏好：${Presentation.subtitles.firstOrNull {it.first==subtitlePreference}?.second ?: "媒体默认"}",
                            collapseWhenIdle=true) {chooser="subtitle"}}
                        if(episodeContainer) item {EpisodeLayoutButtons(library,episodeLayout,collapseWhenIdle=true)}
                        // One click walks 海报 → 背景 → 横幅; no submenu, the button already shows the
                        // mode it is in.
                        else item {Action("视图：${when(mode) {"Thumb"->"背景";"Banner"->"横幅";else->"海报"}}",id="library-view",
                            collapseWhenIdle=true) {
                            val next=when(mode) {"Poster"->"Thumb";"Thumb"->"Banner";else->"Poster"}
                            model.saveSettings(model.settings.copy(libraryArtworkModes=model.settings.libraryArtworkModes+(library.key to next)))
                        }}
                        // Every library can switch to folder browsing, movie libraries included.
                        if(!episodeContainer) item {Action(if(folderMode) "按海报" else "按文件夹",
                            id="library-folder-mode",active=folderMode,collapseWhenIdle=true) {folderMode=!folderMode}}
                    }
                }
            }
            if(folderMode) {
                val folders=model.folderPages[library.key]
                itemsIndexed(folderEntries,key={_,entry->"folder:${entry.key}"},span={_,_->GridItemSpan(maxLineSpan)}) {index,folder->
                    LibraryLatestRow(folder,true,folderRequesters[index],onUp={moveFolder(index-1)},
                        onDown=if(index<folderEntries.lastIndex || moreFolders) {{moveFolder(index+1)}} else null,
                        headingInset=FocusShadowGutter)
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
                    "vertical" -> itemsIndexed(entries,key={_,entry->entry.key},span={_,_->GridItemSpan(maxLineSpan)}) {index,entry->
                        if(more && index>=(entries.size-(columns*2).coerceAtLeast(4)).coerceAtLeast(0)) LaunchedEffect(entries.size,index) {
                            model.loadLibrary(library,sort,true,ascending)
                        }
                        EpisodeListCard(entry,mediaNavigation(index))
                    }
                    "numbers" -> itemsIndexed(entries,key={_,entry->entry.key}) {index,entry->
                        if(more && index>=(entries.size-(columns*2).coerceAtLeast(6)).coerceAtLeast(0)) LaunchedEffect(entries.size,index) {
                            model.loadLibrary(library,sort,true,ascending)
                        }
                        EpisodeNumber(entry,index,mediaNavigation(index).fillMaxWidth())
                    }
                    else -> item(span={GridItemSpan(maxLineSpan)}) {
                        Box(Modifier.gridFocusTarget(gridFocus,2,1,null)) {
                            EpisodeHorizontal(entries,onNeedMore={if(more) model.loadLibrary(library,sort,true,ascending)})
                        }
                    }
                } else items(totalSlots,key={"library-slot:$it"}) {index->
                    val entry=entries.getOrNull(index)
                    PosterWallSlot(entry,mode,mediaNavigation(index),focusId="library-slot:$index",loadGeneration=entries.size,
                        onNeedContent={
                            if(page==null) model.loadLibrary(library,sort,false,ascending)
                            else if(index>=entries.size && more) model.loadLibrary(library,sort,true,ascending)
                        }) {media->
                        model.navigate(Route.Detail(media))
                    }
                }
            }
            model.errors["library:${library.key}"]?.let {error->item(span={GridItemSpan(maxLineSpan)}) {EmptyState("读取失败",error,"重试") {model.loadLibrary(library,sort,ascending=ascending)}}}
        }
        }
        if(model.carouselRefreshing) SunnyBrandLoading(true,Modifier.matchParentSize(),scopeTag="library:loading",scrim=true)
    }
    if(chooser.isNotEmpty()) ChoiceDialog(when(chooser) {"sort"->"排序";"subtitle"->"字幕优先级（未匹配时跟随媒体默认）";else->"展现方式"},
        when(chooser) {"sort"->Presentation.sorts.map {(key,label)->key to if(key==sort) "$label · ${if(ascending) "升序" else "降序"}（再点切换）" else label};"subtitle"->listOf("default" to "跟随媒体默认")+Presentation.subtitles
            else->listOf("Poster" to "海报 · Poster","Thumb" to "背景 · Thumb","Banner" to "横幅 · Banner")},
        when(chooser) {"sort"->sort;"subtitle"->subtitlePreference;else->mode},onDismiss={chooser=""},
        // Twelve sort keys fit three to a row, so every option is reachable without scrolling.
        columns=if(chooser=="sort") 3 else 1) {value->
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
    // The row list clips its own bounds, so the folder buttons keep the shadow gutter inside it
    // while the outer padding is reduced by the same amount: the heading stays on the page edge.
    Column(Modifier.fillMaxSize().padding(horizontal=(pageSidePadding-FocusShadowGutter).coerceAtLeast(0.dp)).padding(top=pageTopPadding)) {
        Column(Modifier.padding(start=FocusShadowGutter)) {
            SectionTitle(route.title,"重新读取") {model.loadFolder(route.sourceId,route.path)}
            Text("CloudDrive2 · 只读模式；不移动、不重命名、不删除文件",color=SunnyColors.Secondary,fontSize=12.sp)
            model.errors["folder:${route.sourceId}:${route.path}"]?.let {e->EmptyState("读取目录失败",e)}
            if(entries==null) Text("正在读取目录…",color=SunnyColors.Secondary,modifier=Modifier.padding(24.dp))
            if(entries?.isEmpty()==true) EmptyState("这里没有可播放内容","仅显示文件夹、视频及 STRM 文件。")
        }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),
            contentPadding=PaddingValues(start=FocusShadowGutter,end=FocusShadowGutter,
                top=FocusShadowTopGutter,bottom=18.dp)) {
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
    val pool=remember(model.feeds.keys.toList(),model.libraryLatest.keys.toList(),model.pages.keys.toList()) {
        model.knownEntries()
    }
    // Typed latin letters match pinyin initials locally, so "sdyq" finds 速度与激情 on a remote.
    val latin=text.any {it.code<128 && it.isLetterOrDigit()}
    val pinyinMatches=remember(pool,text,latin) {
        if(!latin || text.isBlank()) emptyList() else pool.filter {PinyinIndex.matches(it.title,text)}.take(24)
    }
    fun run() {if(text.isNotBlank()) model.search(text.trim())}
    Column(Modifier.fillMaxSize().padding(horizontal=pageSidePadding).padding(top=pageTopPadding)) {
        SectionTitle("搜索你的媒体库")
        // Remote keyboard first, the field under it: the D-pad starts on the letters and the text
        // being typed stays on screen right below the keys. Letters and digits share one centred
        // block with 退格 / 清除 sitting between them, so the hand does not travel to the far right
        // for a correction.
        Text("拼音首字母 · 数字",color=SunnyColors.Secondary,fontSize=12.sp,modifier=Modifier.padding(top=12.dp))
        val scrollableKeys=LocalCompact.current||LocalHandset.current||LocalTouchFirst.current
        Row(Modifier.fillMaxWidth().padding(vertical=4.dp)
            .then(if(scrollableKeys) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .focusGroup(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
            Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                listOf("ABCDEFGHI","JKLMNOPQR","STUVWXYZ").forEach {row->
                    Row(horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically) {
                        row.forEach {letter->SearchKey("key:$letter",letter.toString()) {text=text+letter}}
                    }
                }
            }
            Column(Modifier.padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                SearchIconKey("key:backspace","backspace","退格") {if(text.isNotEmpty()) text=text.dropLast(1)}
                SearchIconKey("key:clear","trash","清除") {text=""}
            }
            Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically) {
                    listOf("1","2","3","4","5").forEach {digit->SearchKey("key:$digit",digit) {text=text+digit}}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically) {
                    listOf("6","7","8","9","0").forEach {digit->SearchKey("key:$digit",digit) {text=text+digit}}
                }
            }
        }
        Row(Modifier.padding(vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Field("片名或关键词",text,{text=it},Modifier.weight(1f))
            Action("搜索",primary=true) {run()}
        }
        LazyColumn(contentPadding=PaddingValues(bottom=30.dp)) {
            if(pinyinMatches.isNotEmpty()) item {
                MediaShelf("拼音匹配",pinyinMatches,onClick={model.navigate(Route.Detail(it))})
            }
            model.activeEmbySources().forEach {source -> item {
                MediaShelf(source.name,model.pages["search:${source.id}"]?.items ?: emptyList(),onClick={model.navigate(Route.Detail(it))})
                model.errors["search:${source.id}"]?.let {Text(it,color=SunnyColors.Secondary)}
            } }
        }
    }
}

/** Keyboard key: text only, no fill and no border; focus only changes the colour. */
@Composable private fun SearchKey(id:String,label:String,onClick:()->Unit) {
    var focused by remember {mutableStateOf(false)}
    Box(Modifier.testTag(id).clickable(onClick=onClick).onFocusChanged {focused=it.isFocused}.size(38.dp),
        contentAlignment=Alignment.Center) {
        Text(label,color=if(focused) SunnyColors.Accent else SunnyColors.Text,fontSize=17.sp,
            fontWeight=if(focused) FontWeight.Bold else FontWeight.Normal,maxLines=1)
    }
}

/** Backspace / clear: icon only, no button frame. */
@Composable private fun SearchIconKey(id:String,icon:String,label:String,onClick:()->Unit) {
    var focused by remember {mutableStateOf(false)}
    Box(Modifier.testTag(id).clickable(onClick=onClick).onFocusChanged {focused=it.isFocused}
        .semantics {contentDescription=label}.padding(horizontal=6.dp,vertical=8.dp)) {
        LineIcon(icon,if(focused) SunnyColors.Accent else SunnyColors.Text,Modifier.size(22.dp))
    }
}
