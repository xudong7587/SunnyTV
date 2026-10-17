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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.feature.ui.SunnyColors

/** One translucent row; no scrim, picture resizing or pause on open. */
@Composable internal fun SubtitleTimingOverlay(progressMs:Long,enabled:Boolean,onAdjust:(Long)->Unit,onClose:()->Unit) {
    Popup(alignment=Alignment.TopCenter,onDismissRequest=onClose,properties=PopupProperties(focusable=true)) {
        Row(Modifier.padding(8.dp).widthIn(max=620.dp).fillMaxWidth()
            .background(Color.Black.copy(alpha=.5f),RoundedCornerShape(12.dp)).padding(6.dp)
            .testTag("subtitle-timing:overlay").semantics {contentDescription="字幕偏移 ${progressMs/1000.0} 秒"},
            horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            listOf(-10_000L,-3_000L,-500L,500L,3_000L,10_000L).forEach {step->
                val seconds=when(kotlin.math.abs(step)) {500L->"0.5";3_000L->"3";else->"10"}
                TimingButton(if(step<0) "←$seconds" else "$seconds→","subtitle-timing:$step",enabled,{onAdjust(step)},
                    modifier=Modifier.weight(1f),initial=step==-500L)
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
