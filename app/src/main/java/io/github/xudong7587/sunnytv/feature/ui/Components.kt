package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.Stroke
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

fun Modifier.flatShadow(shape:Shape,enabled:Boolean):Modifier = if(!enabled) this else drawWithCache {
    val outline=shape.createOutline(size,layoutDirection,this)
    val offset=2.dp.toPx()
    onDrawBehind {translate(offset,offset) {drawOutline(outline,Color(0x26333333))}}
}

@Composable fun FocusTile(
    id:String, modifier:Modifier=Modifier, active:Boolean=false, autoFocus:Boolean=false,
    shape:Shape=RoundedCornerShape(12.dp), focusOutline:Boolean=true, button:Boolean=false, restoreFocus:Boolean=true,onFocus:()->Unit={}, onClick:()->Unit, content:@Composable BoxScope.(Boolean)->Unit
) {
    val model=LocalAppModel.current; val page=LocalPageKey.current
    val pageActive=LocalPageActive.current
    val requester=remember(page,id) { FocusRequester() }
    val bridge=LocalNavigationBridge.current
    DisposableEffect(bridge,id,requester) {
        val targets=if(id.startsWith("nav:")) bridge?.navigation else if(!id.startsWith("dialog:")) bridge?.content else null
        targets?.put(id,requester)
        onDispose {targets?.remove(id)}
    }
    var focused by remember { mutableStateOf(false) }
    val motion=LocalMotion.current
    val selected=focused || active
    val base=LocalSunnyPalette.current
    // Read transition values only during draw; media and text stay out of per-frame recomposition.
    val fill=animateColorAsState(if(selected) base.focusBackground else base.raised.copy(.72f),motion.fade(240),label="focus-fill")
    val bottom=animateColorAsState(if(selected) lerp(base.focusBackground,Color.Black,.16f) else base.surface.copy(.70f),motion.fade(240),label="focus-gradient")
    val contentPalette=if(selected) base.copy(text=base.focusContent,secondary=base.focusContent.copy(.88f),accent=base.focusContent) else base
    val outline=if(model.settings.darkTheme) base.focusContent else base.focusBackground
    val showShadow=model.settings.shadowsEnabled && (!model.settings.darkTheme || button) && focused
    LaunchedEffect(page,id,pageActive) {
        if(pageActive && ((restoreFocus && model.focusMemory[page]==id) || (autoFocus && (model.focusMemory[page]==null || id.startsWith("dialog:"))))) {
            delay(45); runCatching { requester.requestFocus() }
        }
    }
    Box(modifier.testTag(id).flatShadow(shape,showShadow)
        .focusRequester(requester).focusProperties {canFocus=pageActive}.onFocusChanged {
            focused=it.isFocused
            if(it.isFocused && pageActive) { model.focusMemory[page]=id; onFocus() }
        }.clip(shape)
        .drawWithCache {
            val edge=shape.createOutline(size,layoutDirection,this)
            onDrawWithContent {
                drawOutline(edge,Brush.verticalGradient(listOf(fill.value,bottom.value)))
                drawContent()
                if(focused && focusOutline) drawOutline(edge,outline,style=Stroke(2.dp.toPx()))
            }
        }
        .onPreviewKeyEvent {event->event.type==KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount>0 &&
            event.key in listOf(Key.Enter,Key.NumPadEnter,Key.DirectionCenter)}
        .clickable(enabled=pageActive,onClick=onClick), contentAlignment=Alignment.Center) {
        CompositionLocalProvider(LocalSunnyPalette provides contentPalette) {content(focused)}
    }
}

@Composable fun Action(text:String,id:String=text,primary:Boolean=false,autoFocus:Boolean=false,active:Boolean=false,icon:String=actionIcon(text),modifier:Modifier=Modifier,onClick:()->Unit) {
    val model=LocalAppModel.current
    FocusTile(id=id,modifier=modifier.semantics {contentDescription=text},autoFocus=autoFocus,active=active,
        shape=RoundedCornerShape(28.dp),button=true,onClick=onClick) { focused ->
        val ink=if(primary || focused) SunnyColors.Accent else SunnyColors.Text
        Row(Modifier.height(48.dp).widthIn(min=48.dp)
            .padding(horizontal=14.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
            LineIcon(icon,ink)
            val motion=LocalMotion.current
            AnimatedVisibility(focused || active,enter=expandHorizontally(motion.spring())+fadeIn(motion.fade(240)),
                exit=shrinkHorizontally(motion.spring())+fadeOut(motion.fade(240))) {
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
            val fadeMs=LocalMotion.current.duration(300)
            val request=remember(art,actualWidth,source.id,fadeMs) {
                ImageRequest.Builder(context).data(service.imageUrl(art,actualWidth))
                    .size(actualWidth,if(art.type=="Primary" && !fit && item.type!="Episode") actualWidth*3/2 else actualWidth*9/16).precision(coil.size.Precision.INEXACT)
                    .memoryCacheKey("${source.id}:${art.itemId}:${art.type}:${art.tag}:$actualWidth")
                    .diskCacheKey("${source.id}:${art.itemId}:${art.type}:${art.tag}:$actualWidth")
                    .crossfade(fadeMs).build()
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
    val motion=LocalMotion.current
    val compact=LocalCompact.current
    Box(Modifier.fillMaxSize().background(SunnyColors.Background)) {
        if(model.settings.backdropEnabled && item!=null && (item.backdrop!=null || item.primary!=null)) {
            Crossfade(item,animationSpec=motion.fade(400),label="library-backdrop") {media->
                ArtworkView(media,if(compact) media.primary ?: media.backdrop else media.backdrop ?: media.primary,Modifier.fillMaxSize(),widthPx=1920)
            }
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
        StableLazyRow(horizontalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(3.dp),
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
    val motion=LocalMotion.current
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
    val logoAlpha by animateFloatAsState(if(loaded) 1f else 0f,motion.fade(300),label="title-logo")
    Box(Modifier.fillMaxWidth().height(titleHeight), contentAlignment = Alignment.CenterStart) {
        if (logoAlpha<1f) Text(item.title, modifier=Modifier.graphicsLayer {alpha=1f-logoAlpha},color = SunnyColors.Text, fontSize = fontSize, lineHeight = fontSize * 1.2f,
            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        AsyncImage(model = request, imageLoader = model.app.images(source), contentDescription = item.title,
            contentScale = ContentScale.Fit, alignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize().graphicsLayer {alpha=logoAlpha},
            onSuccess = { loaded = true }, onError = { failed = true })
    }
}
