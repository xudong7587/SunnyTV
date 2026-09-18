package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.Route
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** One fixed-height viewport, no lazy-scroll correction and no focus scale.
 * All width weights share a transition, so growing + shrinking always equals one wide card.
 * At an edge the physical focus slot stays put and only its media changes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable fun TvAccordionCards(entries:List<MediaEntry>,selected:Int,onSelect:(Int)->Unit,
    modifier:Modifier=Modifier,id:String,onMore:(()->Unit)?=null) {
    if(entries.isEmpty()) return
    val model=LocalAppModel.current
    val motion=LocalMotion.current
    val page=LocalPageKey.current
    val pageActive=LocalPageActive.current
    val density=LocalDensity.current
    var start by rememberSaveable(id) {mutableIntStateOf(0)}
    var direction by remember {mutableIntStateOf(1)}
    var pending by remember {mutableStateOf<Int?>(null)}
    var rowFocused by remember {mutableStateOf(false)}
    val returnOffset=remember {Animatable(0f)}
    val requesters=remember(entries.size) {List(entries.size) {FocusRequester()}}
    val moreFocus=remember {FocusRequester()}
    val selectedIndex=selected.coerceIn(0,entries.lastIndex+if(onMore!=null) 1 else 0)
    val expanded=selectedIndex.coerceAtMost(entries.lastIndex)
    val safeStart=start.coerceIn(0,expanded)
    BoxWithConstraints(modifier.fillMaxWidth().height(213.dp).clipToBounds().testTag("$id:viewport").onFocusChanged {rowFocused=it.hasFocus}.focusProperties {enter={requesters[0]}}.focusGroup()) {
        val inset=24.dp
        val height=165.dp
        val gap=8.dp
        val viewport=(maxWidth-inset*2).coerceAtLeast(1.dp)
        val wide=(height*16/9).coerceAtMost(viewport)
        val narrow=(height*2/3).coerceAtMost(wide)
        val allWidth=108.dp.coerceAtMost(viewport)
        val lastSlot=ShelfWindow.lastWideSlot(viewport.value,wide.value,narrow.value,gap.value)
        val slots=entries.size-safeStart
        fun select(index:Int) {
            val next=index.coerceIn(0,entries.lastIndex+if(onMore!=null) 1 else 0)
            direction=if(next>=selectedIndex) 1 else -1
            if(next<=entries.lastIndex) start=ShelfWindow.startFor(next,safeStart,lastSlot)
            model.focusMemory[page]="$id:${entries.getOrNull(next)?.key ?: "all"}"
            onSelect(next)
            pending=next
        }
        LaunchedEffect(rowFocused) {
            if(rowFocused) {
                // Every vertical entrance starts at the left, even if the previous exit is still animating.
                returnOffset.snapTo(0f)
                start=0
                onSelect(0)
                pending=0
            } else {
                pending=null
                // First contract the currently wide card, then reveal the original narrow sequence.
                delay(motion.duration(320).toLong())
                val prior=start
                if(prior>0) {
                    returnOffset.snapTo(prior*(narrow+gap).value)
                    start=0
                    onSelect(0)
                    returnOffset.animateTo(0f,motion.fade(400))
                } else onSelect(0)
            }
        }
        LaunchedEffect(pending,start) {
            val next=pending ?: return@LaunchedEffect
            withFrameNanos {}
            if(next==entries.size) moreFocus.requestFocus()
            else requesters[(next-start).coerceIn(requesters.indices)].requestFocus()
            pending=null
        }
        // A single coordinated transition keeps all uninvolved X positions constant.
        val transition=updateTransition(if(rowFocused) expanded-safeStart else -1,label="shelf-expansion")
        val weights=(0 until slots).map {slot->
            transition.animateFloat(transitionSpec={if(targetState==-1 || initialState==-1) motion.fade(320) else motion.spring()},label="slot-$slot") {if(it==slot) 1f else 0f}
        }
        val offset=animateDpAsState(if(rowFocused && selectedIndex==entries.size)
            ShelfWindow.revealAllOffset(slots,narrow.value,wide.value,gap.value,allWidth.value,viewport.value).dp else 0.dp,
            motion.spring(),label="shelf-view-all-reveal")
        Layout(modifier=Modifier.fillMaxSize(),content={
            repeat(slots) {slot->
                val index=safeStart+slot
                val entry=entries[index]
                FocusTile("$id:${entry.key}",Modifier.fillMaxHeight().focusRequester(requesters[slot]).onPreviewKeyEvent {event->
                    if(event.type==KeyEventType.KeyDown && event.key in listOf(Key.DirectionLeft,Key.DirectionRight)) {
                        val delta=if(event.key==Key.DirectionRight) 1 else -1
                        select(index+delta);true
                    } else false
                },active=rowFocused && selectedIndex==index,shape=RoundedCornerShape(15.dp),focusLift=false,restoreFocus=false,onFocus={
                    if(selectedIndex!=index) select(index)
                },onClick={model.navigate(Route.Detail(entry))}) {
                    AnimatedContent(entry,contentKey={it.key},transitionSpec={
                        (fadeIn(motion.fade(320))+slideInHorizontally(motion.fade(320)) {it/6*direction}) togetherWith
                            (fadeOut(motion.fade(320))+slideOutHorizontally(motion.fade(320)) {-it/6*direction}) using SizeTransform(clip=false)
                    },label="shelf-media") {media->
                        ShelfArtwork(media,{weights[slot].value},wide,rowFocused && expanded==index)
                    }
                }
            }
            if(onMore!=null) FocusTile("$id:all",Modifier.focusRequester(moreFocus).onPreviewKeyEvent {event->
                if(event.type==KeyEventType.KeyDown && event.key in listOf(Key.DirectionLeft,Key.DirectionRight)) {
                    if(event.key==Key.DirectionLeft) select(entries.lastIndex)
                    true
                } else false
            },restoreFocus=false,onFocus={onSelect(entries.size)},onClick=onMore) {
                Column(Modifier.fillMaxSize().padding(10.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("→",color=SunnyColors.Text,fontSize=30.sp)
                    Text("查看全部",color=SunnyColors.Text,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                }
            }
        }) {measurables,constraints->
            val h=with(density) {height.roundToPx()}
            val gapPx=with(density) {gap.roundToPx()}
            val insetPx=with(density) {inset.roundToPx()}
            // Calculate boundaries from cumulative floats, avoiding a one-pixel rounding drift.
            val boundaries=mutableListOf(0f)
            repeat(slots) {slot->
                val width=with(density) {(narrow+(wide-narrow)*weights[slot].value.coerceIn(0f,1f)).toPx()}
                boundaries+=boundaries.last()+width+gapPx
            }
            val placeables=measurables.mapIndexed {index,measurable->
                val w=if(index<slots) (boundaries[index+1].roundToInt()-boundaries[index].roundToInt()-gapPx).coerceAtLeast(1)
                    else with(density) {allWidth.roundToPx()}
                measurable.measure(Constraints.fixed(w,h))
            }
            layout(constraints.maxWidth,constraints.maxHeight) {
                placeables.forEachIndexed {index,placeable->
                    placeable.placeRelative(insetPx+boundaries[index].roundToInt()-with(density) {(offset.value+returnOffset.value.dp).roundToPx()},insetPx)
                }
            }
        }
    }
}

@Composable private fun ShelfArtwork(item:MediaEntry,weight:()->Float,wide:Dp,expanded:Boolean) {
    val motion=LocalMotion.current
    Box(Modifier.fillMaxSize()) {
        Crossfade(if(expanded) MediaLogic.wideArtwork(item) else item.primary,animationSpec=motion.fade(300),label="shelf-artwork") {art->
            ArtworkView(item,art,Modifier.fillMaxSize(),640)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(.06f),Color.Black.copy(.88f)))))
        if(item.rating>0) Text("★ %.1f".format(item.rating),color=Color.White,fontSize=10.sp,
            modifier=Modifier.align(Alignment.TopStart).padding(7.dp))
        Column(Modifier.align(Alignment.BottomStart).requiredWidth((wide-26.dp).coerceAtLeast(1.dp))
            .padding(start=13.dp,bottom=13.dp).graphicsLayer {alpha=weight().coerceIn(0f,1f)}) {
            Text(item.title,color=Color.White,fontSize=19.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(item.subtitle,color=Color.White.copy(.82f),fontSize=11.sp,maxLines=1,modifier=Modifier.padding(top=5.dp))
        }
        Text(item.title,color=Color.White,fontSize=11.sp,lineHeight=15.sp,fontWeight=FontWeight.Medium,maxLines=3,overflow=TextOverflow.Ellipsis,
            modifier=Modifier.align(Alignment.BottomStart).padding(7.dp).graphicsLayer {alpha=(1f-weight()).coerceIn(0f,1f)})
    }
}
