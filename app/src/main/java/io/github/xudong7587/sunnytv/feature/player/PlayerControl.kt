package io.github.xudong7587.sunnytv.feature.player

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.feature.ui.LineIcon
import io.github.xudong7587.sunnytv.feature.ui.SunnyColors

/** Original Compose rendering: transparent circular controls and a label above the focused icon. */
@Composable internal fun PlayerControl(label:String,icon:String,modifier:Modifier=Modifier,
    initial:Boolean=false,onClick:()->Unit) {
    val requester=remember {FocusRequester()}
    var focused by remember {mutableStateOf(false)}
    LaunchedEffect(Unit) {if(initial) {withFrameNanos {};requester.requestFocus()}}
    Box(modifier.size(50.dp).testTag("player:$icon").semantics {contentDescription=label}
        .focusRequester(requester).onFocusChanged {focused=it.isFocused}
        .background(if(focused) Color.White.copy(.18f) else Color.Transparent,CircleShape)
        .border(2.dp,if(focused) SunnyColors.Accent else Color.Transparent,CircleShape)
        .clickable(onClick=onClick),contentAlignment=Alignment.Center) {
        LineIcon(icon,Color.White,Modifier.size(25.dp))
        if(focused) Text(label,color=Color.White,fontSize=12.sp,maxLines=1,
            modifier=Modifier.align(Alignment.TopCenter).offset(y=(-34).dp).wrapContentSize(unbounded=true)
                .background(Color.Black.copy(.8f),RoundedCornerShape(8.dp)).padding(horizontal=10.dp,vertical=6.dp))
    }
}

@Composable internal fun PlayerOption(label:String,selected:Boolean=false,initial:Boolean=false,onClick:()->Unit) {
    var focused by remember {mutableStateOf(false)}
    val requester=remember {FocusRequester()}
    LaunchedEffect(Unit) {if(initial) {withFrameNanos {};requester.requestFocus()}}
    Row(Modifier.fillMaxWidth().focusRequester(requester).onFocusChanged {focused=it.isFocused}
        .background(if(focused) Color.White.copy(.16f) else Color.White.copy(.05f),RoundedCornerShape(12.dp))
        .border(2.dp,if(focused) SunnyColors.Accent else Color.Transparent,RoundedCornerShape(12.dp))
        .clickable(onClick=onClick).padding(16.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        LineIcon(if(selected) "check" else "audio",if(selected) SunnyColors.Accent else Color.White.copy(.6f))
        Text(label,color=Color.White,fontSize=14.sp,lineHeight=20.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
    }
}
