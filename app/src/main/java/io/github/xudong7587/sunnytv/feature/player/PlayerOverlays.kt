package io.github.xudong7587.sunnytv.feature.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.tv.material3.Text
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import io.github.xudong7587.sunnytv.R
import io.github.xudong7587.sunnytv.core.model.SkipSegment
import io.github.xudong7587.sunnytv.feature.ui.*

internal fun playerClock(ms:Long):String {
    val seconds=ms.coerceAtLeast(0)/1000
    return if(seconds>=3600) "%d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60)
    else "%02d:%02d".format(seconds/60,seconds%60)
}

@Composable internal fun SunnyLoadingOverlay(visible:Boolean) {
    SunnyBrandLoading(visible,scopeTag="player:loading")
}

/**
 * Playback progress with three ways to seek, all anchored on the same played-position dot:
 * a single D-pad step, a held D-pad key for continuous accelerated seek (the marker time is shown
 * while seeking), and a touch drag anywhere on the bar or on the dot itself.
 */
@Composable internal fun PlayerProgress(position:Long,duration:Long,stepMs:Long,modifier:Modifier=Modifier,
    onSeekBy:(Long)->Unit,onSeekTo:(Long)->Unit) {
    var focused by remember {mutableStateOf(false)}
    var preview by remember {mutableLongStateOf(-1L)}
    val scope=rememberCoroutineScope()
    var holdJob by remember {mutableStateOf<Job?>(null)}
    var holdDirection by remember {mutableIntStateOf(0)}
    val unit=stepMs.coerceAtLeast(1000L)
    fun stopHold(commit:Boolean) {
        holdJob?.cancel();holdJob=null
        if(commit && preview>=0L) onSeekTo(preview)
        preview=-1L;holdDirection=0
    }
    fun startHold(direction:Int) {
        if(holdJob!=null && holdDirection==direction) return
        holdJob?.cancel()
        holdDirection=direction
        holdJob=scope.launch {
            var step=unit
            while(isActive) {
                val base=preview.takeIf {it>=0L} ?: position
                preview=(base+direction*step).coerceIn(0L,duration.coerceAtLeast(0L))
                withFrameNanos {}
                delay(110)
                // Keep accelerating while the key stays down, but stay within a bounded speed.
                step=(step*1.4f).toLong().coerceAtMost(unit*30)
            }
        }
    }
    DisposableEffect(Unit) {onDispose {holdJob?.cancel()}}
    val scrubbing=preview>=0L
    val shown=if(scrubbing) preview else position
    val progress=if(duration>0) (shown.toFloat()/duration).coerceIn(0f,1f) else 0f
    BoxWithConstraints(modifier.fillMaxWidth().height(42.dp).testTag("player:progress").semantics {contentDescription="播放进度"}
        .onFocusChanged {focused=it.isFocused}.focusable()
        .onPreviewKeyEvent {event->
            val horizontal=event.key==Key.DirectionLeft || event.key==Key.DirectionRight
            when {
                event.type==KeyEventType.KeyUp -> {stopHold(true);horizontal}
                event.type!=KeyEventType.KeyDown -> false
                !horizontal -> false
                event.nativeKeyEvent.repeatCount>0 -> {
                    if(!scrubbing) preview=position
                    startHold(if(event.key==Key.DirectionLeft) -1 else 1)
                    true
                }
                else -> {onSeekBy(if(event.key==Key.DirectionLeft) -unit else unit);true}
            }
        }
        .pointerInput(duration) {
            var dragging=false
            fun target(x:Float):Long = if(duration<=0) 0L else
                (x/size.width.coerceAtLeast(1).toFloat()*duration).toLong().coerceIn(0L,duration)
            detectHorizontalDragGestures(
                onDragStart={offset->if(duration>0) {dragging=true;preview=target(offset.x)}},
                onHorizontalDrag={change,_->if(duration>0) {change.consume();preview=target(change.position.x)}},
                onDragEnd={if(dragging) {dragging=false;if(preview>=0L) onSeekTo(preview);preview=-1L}},
                onDragCancel={dragging=false;preview=-1L})
        },contentAlignment=Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(if(focused||scrubbing) 6.dp else 4.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(.24f))) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(SunnyColors.Accent))
        }
        // The played-position dot is always visible; it grows, brightens and gains a ring while seeking.
        val dotSize=if(focused||scrubbing) 17.dp else 11.dp
        Box(Modifier.offset(x=(maxWidth-dotSize)*progress).size(dotSize)
            .background(if(focused||scrubbing) SunnyColors.Accent else Color.White,CircleShape)
            .border(if(focused||scrubbing) 2.dp else 0.dp,Color.Black.copy(.35f),CircleShape))
        if(scrubbing) Text("${playerClock(preview)} / ${playerClock(duration)}",color=Color.White,fontSize=15.sp,
            fontWeight=FontWeight.SemiBold,modifier=Modifier.align(Alignment.TopCenter).offset(y=(-26).dp)
                .background(Color.Black.copy(.78f),RoundedCornerShape(9.dp)).padding(horizontal=11.dp,vertical=4.dp))
    }
}

@Composable internal fun PlayerSheet(title:String,onClose:()->Unit,
    headerAction:(@Composable ()->Unit)?=null,content:@Composable ColumnScope.()->Unit) {
    val focus=remember {FocusRequester()}
    Popup(alignment=Alignment.Center,onDismissRequest=onClose,properties=PopupProperties(focusable=true)) {
        LaunchedEffect(Unit) {withFrameNanos {};focus.requestFocus()}
        Column(Modifier.padding(16.dp).widthIn(max=680.dp).fillMaxWidth().heightIn(max=620.dp)
            .focusRequester(focus).focusGroup().verticalScroll(rememberScrollState())
            .background(Color.Black.copy(alpha=.5f),RoundedCornerShape(22.dp))
            .border(1.dp,Color.White.copy(.14f),RoundedCornerShape(22.dp)).padding(20.dp)
            .testTag("player:sheet"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(title,color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                headerAction?.invoke()
            }
            content()
        }
    }
}

@Composable internal fun SkipSegmentPrompt(segment:SkipSegment,onSkip:()->Unit,onDismiss:()->Unit,modifier:Modifier=Modifier,
    actionFocus:FocusRequester?=null,upFocus:FocusRequester=FocusRequester.Default,downFocus:FocusRequester=FocusRequester.Default) {
    val label=if(segment.type=="outro") "跳过片尾" else "跳过片头"
    val navigation=Modifier.focusProperties {up=upFocus;down=downFocus}
    fun activate(action:()->Unit) {
        // Give focus to a surviving control before skipping/dismissing removes this prompt.
        if(downFocus!==FocusRequester.Default)downFocus.requestFocus()
        action()
    }
    Row(modifier.testTag("player:skip-prompt").background(Color.Black.copy(.82f),RoundedCornerShape(18.dp))
        .border(1.dp,Color.White.copy(.18f),RoundedCornerShape(18.dp)).padding(12.dp),
        horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
        PlayerControl(label,"skip",initial=true,
            modifier=(if(actionFocus!=null)Modifier.focusRequester(actionFocus) else Modifier).then(navigation),
            onClick={activate(onSkip)})
        Text(label,color=Color.White,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
        PlayerControl("暂不","close",modifier=navigation,onClick={activate(onDismiss)})
    }
}


@Composable internal fun NextEpisodePrompt(title:String,remainingSeconds:Long,onPlay:()->Unit,onDismiss:()->Unit,
    modifier:Modifier=Modifier) {
    Column(modifier.testTag("player:next-prompt")
        .background(Color.Black.copy(.84f),RoundedCornerShape(20.dp))
        .border(1.dp,Color.White.copy(.18f),RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("即将播放下一集",color=Color.White.copy(.68f),fontSize=11.sp,letterSpacing=1.sp)
        Text(title,color=Color.White,fontSize=17.sp,fontWeight=FontWeight.SemiBold,maxLines=2)
        Text("$remainingSeconds 秒后进入片尾结束区",color=Color.White.copy(.58f),fontSize=11.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            PlayerControl("播放下一集","next",initial=true,onClick=onPlay)
            PlayerControl("稍后","close",onClick=onDismiss)
        }
    }
}
