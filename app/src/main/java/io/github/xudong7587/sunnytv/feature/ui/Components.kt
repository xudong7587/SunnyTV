package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.xudong7587.sunnytv.core.model.*
import kotlinx.coroutines.delay

@Composable fun FocusTile(
    id:String, modifier:Modifier=Modifier, active:Boolean=false, autoFocus:Boolean=false,
    shape:Shape=RoundedCornerShape(12.dp), onFocus:()->Unit={}, onClick:()->Unit, content:@Composable BoxScope.(Boolean)->Unit
) {
    val model=LocalAppModel.current; val page=LocalPageKey.current
    val requester=remember(page,id) { FocusRequester() }
    val bridge=LocalNavigationBridge.current
    DisposableEffect(bridge,id,requester) {
        val targets=if(id.startsWith("nav:")) bridge?.navigation else if(!id.startsWith("dialog:")) bridge?.content else null
        targets?.put(id,requester)
        onDispose {targets?.remove(id)}
    }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if(focused && !model.settings.reduceMotion) 1.025f else 1f,
        tween(if(model.settings.reduceMotion) 0 else 130),label="focus-scale")
    LaunchedEffect(page,id) {
        if(model.focusMemory[page]==id || (autoFocus && (model.focusMemory[page]==null || id.startsWith("dialog:")))) {
            delay(45); runCatching { requester.requestFocus() }
        }
    }
    Box(modifier.testTag(id).graphicsLayer { scaleX=scale; scaleY=scale }
        .focusRequester(requester).onFocusChanged {
            focused=it.isFocused
            if(it.isFocused) { model.focusMemory[page]=id; onFocus() }
        }.clip(shape)
        .background(Brush.verticalGradient(listOf(
            if(focused || active) SunnyColors.SurfaceRaised.copy(.94f) else SunnyColors.SurfaceRaised.copy(.72f),
            SunnyColors.Surface.copy(.70f))))
        .border(if(focused) 2.dp else 1.dp, if(focused) SunnyColors.Accent else if(active) SunnyColors.Border else Color.White.copy(alpha=.07f),shape)
        .onPreviewKeyEvent {event->event.type==KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount>0 &&
            event.key in listOf(Key.Enter,Key.NumPadEnter,Key.DirectionCenter)}
        .clickable(onClick=onClick), contentAlignment=Alignment.Center) { content(focused) }
}

@Composable fun Action(text:String,id:String=text,primary:Boolean=false,autoFocus:Boolean=false,active:Boolean=false,icon:String=actionIcon(text),onClick:()->Unit) {
    val model=LocalAppModel.current
    FocusTile(id=id,modifier=Modifier.semantics {contentDescription=text},autoFocus=autoFocus,active=active,
        shape=RoundedCornerShape(28.dp),onClick=onClick) { focused ->
        val ink=if(primary || focused) SunnyColors.Accent else SunnyColors.Text
        Row(Modifier.height(48.dp).widthIn(min=48.dp)
            .padding(horizontal=14.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
            LineIcon(icon,ink)
            val ms=if(model.settings.reduceMotion) 0 else 160
            AnimatedVisibility(focused || active,enter=expandHorizontally(tween(ms))+fadeIn(tween(ms)),exit=shrinkHorizontally(tween(ms))+fadeOut(tween(ms))) {
                Text(text.trimStart('▶','✓','♡','♥','ⓘ','≋','▱',' '),color=ink,fontSize=13.sp,fontWeight=FontWeight.SemiBold,
                    modifier=Modifier.padding(start=8.dp),maxLines=1)
            }
        }
    }
}

@Composable fun ArtworkView(item:MediaEntry, preferred:Artwork?,modifier:Modifier=Modifier,
    widthPx:Int=480,fit:Boolean=false,fallbackText:String=item.title) {
    val model=LocalAppModel.current
    val source=model.sources.firstOrNull { it.id==item.sourceId }
    val candidates=remember(item,preferred) { listOfNotNull(preferred,item.primary,item.thumb,item.backdrop).distinct() }
    var attempt by remember(item.key,preferred) { mutableIntStateOf(0) }
    val art=candidates.getOrNull(attempt)
    Box(modifier.background(SunnyColors.Surface),contentAlignment=Alignment.Center) {
        if(art!=null && source?.kind==SourceKind.EMBY) {
            val service=remember(source) { model.app.emby(source) }
            val view=LocalView.current
            val density=LocalDensity.current.density
            val lowRam=(LocalContext.current.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice
            val densityScale=(density/2f).coerceAtLeast(1f)
            val maxPixels=if(lowRam) 1920 else 3840
            val actualWidth=(widthPx*densityScale*(if(model.settings.highQualityArtwork) 1.25f else 1f)).toInt()
                .coerceAtMost(minOf(maxPixels,maxOf(view.width,view.height,1280))).coerceAtLeast(64)
            val context=LocalContext.current
            val request=remember(art,actualWidth,source.id) {
                ImageRequest.Builder(context).data(service.imageUrl(art,actualWidth))
                    .size(actualWidth,if(art.type=="Primary" && !fit && item.type!="Episode") actualWidth*3/2 else actualWidth*9/16).precision(coil.size.Precision.INEXACT)
                    .memoryCacheKey("${source.id}:${art.itemId}:${art.type}:${art.tag}:$actualWidth")
                    .diskCacheKey("${source.id}:${art.itemId}:${art.type}:${art.tag}:$actualWidth")
                    .crossfade(false).build()
            }
            AsyncImage(model=request,imageLoader=model.app.images(source),contentDescription=item.title,
                contentScale=if(fit) ContentScale.Fit else ContentScale.Crop,modifier=Modifier.fillMaxSize(),
                onError={ attempt++ })
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(SunnyColors.SurfaceRaised,SunnyColors.Background))),contentAlignment=Alignment.Center) {
                Text(fallbackText,Modifier.padding(20.dp),color=SunnyColors.Secondary,fontSize=22.sp,fontWeight=FontWeight.SemiBold,maxLines=3,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable fun Backdrop(item:MediaEntry?) {
    val model=LocalAppModel.current
    Box(Modifier.fillMaxSize().background(SunnyColors.Background)) {
        if(model.settings.backdropEnabled && item!=null && (item.backdrop!=null || item.primary!=null)) {
            ArtworkView(item,if(LocalCompact.current) item.primary ?: item.backdrop else item.backdrop ?: item.primary,Modifier.fillMaxSize(),widthPx=1920)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(.7f),Color.Black.copy(.20f)))))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SunnyColors.Background.copy(.20f),SunnyColors.Background.copy(.72f),SunnyColors.Background))))
    }
}

@Composable fun SectionTitle(title:String,extra:String="",onMore:(()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().padding(top=20.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,color=SunnyColors.Text,fontWeight=FontWeight.SemiBold,fontSize=19.sp,modifier=Modifier.weight(1f))
        if(onMore!=null) Action(extra.ifBlank { "查看全部  ›" },id="more:$title",onClick=onMore)
        else if(extra.isNotBlank()) Text(extra,color=SunnyColors.Secondary,fontSize=12.sp)
    }
}

@Composable fun LibraryCard(item:MediaEntry,onClick:()->Unit) {
    Column(Modifier.width(240.dp)) {
        FocusTile("library:${item.key}",Modifier.fillMaxWidth().aspectRatio(16f/9),onClick=onClick) {
            // Keep the server's native library picture intact, including lettering in the art.
            ArtworkView(item,MediaLogic.libraryArtwork(item),Modifier.fillMaxSize(),640,fit=true)
        }
        Text(item.title,color=SunnyColors.Text,fontSize=15.sp,modifier=Modifier.padding(top=8.dp),maxLines=1)
    }
}

@Composable fun MediaCard(item:MediaEntry,wide:Boolean=false,autoFocus:Boolean=false,onFocus:()->Unit={},onClick:()->Unit,focusId:String="media:${item.key}") {
    val width=if(wide) 240.dp else 132.dp
    Column(Modifier.width(width)) {
        FocusTile(id=focusId,modifier=Modifier.fillMaxWidth().aspectRatio(if(wide) 16f/9 else 2f/3),autoFocus=autoFocus,onFocus=onFocus,onClick=onClick) {
            ArtworkView(item,if(wide) MediaLogic.wideArtwork(item) else item.primary,Modifier.fillMaxSize(),if(wide) 640 else 400)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(.1f),Color.Black.copy(.5f)))))
            if(item.rating>0) Text("★ %.1f".format(item.rating),color=SunnyColors.Text,fontSize=10.sp,
                modifier=Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(.65f)).padding(horizontal=6.dp,vertical=3.dp))
            if(item.played) Text("已看",color=SunnyColors.Accent,fontSize=10.sp,modifier=Modifier.align(Alignment.TopEnd).padding(8.dp))
            if(item.positionMs>0 && item.durationMs>0) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(.15f)))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(MediaLogic.progress(item.positionMs,item.durationMs)).height(3.dp).background(SunnyColors.Accent))
            }
        }
        Text(item.title,color=SunnyColors.Text,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=9.dp))
        Text(if(wide && item.positionMs>0) "${item.subtitle} · 剩余 ${MediaLogic.remainingMinutes(item.positionMs,item.durationMs)} 分钟" else item.subtitle,
            color=SunnyColors.Secondary,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=3.dp))
    }
}

@Composable fun MediaShelf(title:String,list:List<MediaEntry>,wide:Boolean=false,onFocus:(MediaEntry)->Unit={},onClick:(MediaEntry)->Unit) {
    if(list.isEmpty()) return
    Column {
        SectionTitle(title)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(3.dp),
            modifier=Modifier.fillMaxWidth().focusGroup()) {
            items(list,key={ it.key }) { entry -> MediaCard(entry,wide,onFocus={onFocus(entry)},onClick={onClick(entry)},focusId="shelf:$title:${entry.key}") }
        }
    }
}

@Composable fun EmptyState(title:String,description:String,action:String?=null,onAction:()->Unit={}) {
    Column(Modifier.fillMaxWidth().padding(vertical=36.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
        Text(title,color=SunnyColors.Text,fontSize=32.sp,fontWeight=FontWeight.SemiBold)
        Text(description,color=SunnyColors.Secondary,fontSize=15.sp,lineHeight=24.sp)
        if(action!=null) Action(action,primary=true,autoFocus=true,onClick=onAction)
    }
}


/** Emby transparent title logos are artwork, not a replacement font. Text remains the fallback. */
@Composable fun MediaTitle(item: MediaEntry, fontSize: TextUnit = 40.sp) {
    val model = LocalAppModel.current
    val source = model.sources.firstOrNull { it.id == item.sourceId }
    val logo = item.logo
    var loaded by remember(item.key, logo) { mutableStateOf(false) }
    var failed by remember(item.key, logo) { mutableStateOf(false) }
    val context = LocalContext.current
    if (logo == null || source == null || failed) {
        Text(item.title, color = SunnyColors.Text, fontSize = fontSize, lineHeight = fontSize * 1.2f, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        return
    }
    val logoWidth=(720*(LocalDensity.current.density/2f).coerceAtLeast(1f)).toInt().coerceAtMost(1440)
    val request = remember(source.id, logo,logoWidth) {
        ImageRequest.Builder(context).data(model.app.emby(source).imageUrl(logo, logoWidth))
            .size(logoWidth, logoWidth/3)
            .memoryCacheKey("${source.id}:${logo.itemId}:Logo:${logo.tag}:$logoWidth")
            .diskCacheKey("${source.id}:${logo.itemId}:Logo:${logo.tag}:$logoWidth")
            .crossfade(false).build()
    }
    val titleHeight=with(LocalDensity.current) {(fontSize * 2.4f).toDp()}.coerceAtLeast(75.dp)
    Box(Modifier.fillMaxWidth().height(titleHeight), contentAlignment = Alignment.CenterStart) {
        if (!loaded) Text(item.title, color = SunnyColors.Text, fontSize = fontSize, lineHeight = fontSize * 1.2f,
            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        AsyncImage(model = request, imageLoader = model.app.images(source), contentDescription = item.title,
            contentScale = ContentScale.Fit, alignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize(),
            onSuccess = { loaded = true }, onError = { failed = true })
    }
}
