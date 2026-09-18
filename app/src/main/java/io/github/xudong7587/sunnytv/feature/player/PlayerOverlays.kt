package io.github.xudong7587.sunnytv.feature.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.R
import io.github.xudong7587.sunnytv.core.model.SkipSegment
import io.github.xudong7587.sunnytv.feature.ui.*

@Composable internal fun SunnyLoadingOverlay(visible:Boolean) {
    if(!visible) return
    val transition=rememberInfiniteTransition(label="sunny-loading")
    val y by transition.animateFloat(-9f,5f,infiniteRepeatable(
        animation=tween(620,easing=FastOutSlowInEasing),repeatMode=RepeatMode.Reverse),label="loading-y")
    val alpha by transition.animateFloat(.45f,1f,infiniteRepeatable(
        animation=tween(900,easing=LinearEasing),repeatMode=RepeatMode.Reverse),label="loading-alpha")
    Column(Modifier.fillMaxSize().testTag("player:loading"),verticalArrangement=Arrangement.Center,
        horizontalAlignment=Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.ic_sun_brand),"SunnyTV",
            Modifier.size(72.dp).graphicsLayer {translationY=y.dp.toPx()})
        Text("L O A D I N G",color=Color.White.copy(alpha),fontSize=13.sp,fontWeight=FontWeight.SemiBold,
            letterSpacing=3.sp,modifier=Modifier.padding(top=18.dp))
    }
}

@Composable internal fun PlayerProgress(position:Long,duration:Long,stepMs:Long,onSeek:(Long)->Unit) {
    var focused by remember {mutableStateOf(false)}
    val progress=if(duration>0) (position.toFloat()/duration).coerceIn(0f,1f) else 0f
    BoxWithConstraints(Modifier.fillMaxWidth().height(24.dp).testTag("player:progress").semantics {contentDescription="播放进度"}
        .onFocusChanged {focused=it.isFocused}.focusable()
        .onPreviewKeyEvent {event->
            if(event.type!=KeyEventType.KeyDown) false else when(event.key) {
                Key.DirectionLeft->{onSeek(-stepMs);true}
                Key.DirectionRight->{onSeek(stepMs);true}
                else->false
            }
        },contentAlignment=Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(if(focused) 5.dp else 3.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(.24f))) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(SunnyColors.Accent))
        }
        if(focused) Box(Modifier.offset(x=(maxWidth-10.dp)*progress).size(10.dp).background(Color.White,androidx.compose.foundation.shape.CircleShape))
    }
}

@Composable internal fun PlayerSheet(title:String,onClose:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(.76f)),contentAlignment=Alignment.Center) {
        Column(Modifier.widthIn(min=480.dp,max=680.dp).heightIn(max=620.dp)
            .background(Color(0xF21A1B1E),RoundedCornerShape(22.dp))
            .border(1.dp,Color.White.copy(.14f),RoundedCornerShape(22.dp)).padding(26.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(title,color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                PlayerControl("关闭","close",initial=true,onClick=onClose)
            }
            content()
        }
    }
}

@Composable internal fun SkipSegmentPrompt(segment:SkipSegment,onSkip:()->Unit,onDismiss:()->Unit,modifier:Modifier=Modifier) {
    val label=if(segment.type=="outro") "跳过片尾" else "跳过片头"
    Row(modifier.testTag("player:skip-prompt").background(Color.Black.copy(.82f),RoundedCornerShape(18.dp))
        .border(1.dp,Color.White.copy(.18f),RoundedCornerShape(18.dp)).padding(12.dp),
        horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
        PlayerControl(label,"skip",initial=true,onClick=onSkip)
        Text(label,color=Color.White,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
        PlayerControl("暂不","close",onClick=onDismiss)
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
