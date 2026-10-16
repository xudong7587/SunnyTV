package io.github.xudong7587.sunnytv.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.feature.ui.SunnyColors
import java.util.Locale

/** No full-screen scrim or bottom controls: the picture and normal bottom subtitles stay visible. */
@Composable internal fun SubtitleTimingOverlay(progressMs:Long,enabled:Boolean,onAdjust:(Long)->Unit,
    onReset:()->Unit,onClose:()->Unit,onHeight:(Int)->Unit) {
    Popup(alignment=Alignment.TopCenter,onDismissRequest=onClose,
        properties=PopupProperties(focusable=true)) {
        BoxWithConstraints(Modifier.onSizeChanged {onHeight(it.height)}.padding(8.dp).widthIn(max=620.dp).fillMaxWidth()
            .background(Color.Black.copy(alpha=.78f),RoundedCornerShape(12.dp))
            .padding(10.dp).testTag("subtitle-timing:overlay")) {
            val wide=maxWidth>=460.dp
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("字幕微调  ${String.format(Locale.ROOT,"%+.1f",progressMs/1000.0)} 秒",
                        color=Color.White,fontSize=14.sp,modifier=Modifier.weight(1f).testTag("subtitle-timing:offset"))
                    TimingButton("归零","subtitle-timing:reset",enabled,onReset)
                    TimingButton("完成","subtitle-timing:close",true,onClose,initial=!enabled)
                }
                Text(if(enabled) "← 字幕进度回退    字幕进度前进 →" else "请先在字幕面板选择并开启字幕",
                    color=Color.White.copy(alpha=.8f),fontSize=12.sp)
                val steps=listOf(-10_000L,-3_000L,-500L,500L,3_000L,10_000L)
                steps.chunked(if(wide) 6 else 3).forEach {row ->
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        row.forEach {step ->
                            val seconds=when(kotlin.math.abs(step)) {500L->"0.5";3_000L->"3";else->"10"}
                            val label=if(step<0) "← $seconds 秒" else "$seconds 秒 →"
                            TimingButton(label,"subtitle-timing:$step",enabled,{onAdjust(step)},
                                modifier=Modifier.weight(1f),initial=enabled && step==-500L)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun TimingButton(label:String,tag:String,enabled:Boolean,onClick:()->Unit,
    modifier:Modifier=Modifier,initial:Boolean=false) {
    var focused by remember {mutableStateOf(false)}
    val focus=remember {FocusRequester()}
    LaunchedEffect(Unit) {if(initial) {withFrameNanos {};focus.requestFocus()}}
    Box(modifier.heightIn(min=44.dp).focusRequester(focus).onFocusChanged {focused=it.isFocused}
        .background(if(focused) SunnyColors.Accent.copy(alpha=.22f) else Color.White.copy(alpha=.08f),RoundedCornerShape(8.dp))
        .border(1.dp,if(focused) SunnyColors.Accent else Color.Transparent,RoundedCornerShape(8.dp))
        .clickable(enabled=enabled,onClick=onClick).testTag(tag).semantics {contentDescription=label}
        .padding(horizontal=8.dp,vertical=6.dp),contentAlignment=Alignment.Center) {
        Text(label,color=Color.White.copy(alpha=if(enabled) 1f else .4f),fontSize=12.sp,maxLines=1)
    }
}
