package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.core.model.MediaEntry
import io.github.xudong7587.sunnytv.feature.MediaAction
import kotlinx.coroutines.delay

/**
 * Long-press sheet for a library or media item. Every action is a server-side request; the app
 * never touches NAS files itself and no destructive action runs without an explicit confirmation.
 */
@Composable fun ItemActionSheet(item:MediaEntry,actions:List<MediaAction>,onRun:(MediaAction)->Unit,onDelete:()->Unit,onDismiss:()->Unit) {
    val model=LocalAppModel.current
    val page=LocalPageKey.current
    val prior=remember {model.focusMemory[page]}
    val inputMotion=LocalTvFocusMotion.current
    // The long press that opened this sheet is still holding the centre key; ignore the release.
    var armed by remember {mutableStateOf(false)}
    LaunchedEffect(Unit) {delay(260);armed=true}
    LaunchedEffect(Unit) {inputMotion.horizontal=false}
    DisposableEffect(Unit) {onDispose {if(prior!=null) model.focusMemory[page]=prior}}
    Dialog(onDismissRequest=onDismiss) {
        Column(Modifier.onPreviewKeyEvent {inputMotion.record(it);false}.width(520.dp)
            .background(SunnyColors.Surface,RoundedCornerShape(22.dp))
            .border(1.dp,SunnyColors.Border,RoundedCornerShape(22.dp)).padding(22.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("按住确定键的操作",color=SunnyColors.Accent,fontSize=12.sp)
            Text(item.title,color=SunnyColors.Text,fontSize=22.sp,maxLines=2)
            if(item.subtitle.isNotBlank()) Text(item.subtitle,color=SunnyColors.Secondary,fontSize=12.sp)
            actions.forEachIndexed {index,action->
                FocusTile("action:${action.name}:${item.key}",Modifier.fillMaxWidth(),autoFocus=index==0,
                    onClick={if(!armed) Unit else if(action==MediaAction.DELETE) onDelete() else onRun(action)}) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        LineIcon(actionIcon(action.label),SunnyColors.Accent,Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(action.label,color=SunnyColors.Text,fontSize=16.sp)
                            Text(action.description,color=SunnyColors.Secondary,fontSize=11.sp)
                        }
                    }
                }
            }
            Action("取消",id="dialog:item-actions:cancel",onClick=onDismiss)
        }
    }
}

@Composable fun ItemDeleteConfirm(item:MediaEntry,onConfirm:()->Unit,onDismiss:()->Unit) {
    val model=LocalAppModel.current
    Dialog(onDismissRequest=onDismiss) {
        Column(Modifier.width(520.dp).background(SunnyColors.Surface,RoundedCornerShape(22.dp))
            .border(1.dp,SunnyColors.Border,RoundedCornerShape(22.dp)).padding(24.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("从媒体库移除？",color=SunnyColors.Text,fontSize=22.sp)
            Text("将请求 Emby 移除「${item.title}」。是否同时删除服务器上的媒体文件由服务端与账号权限决定，此操作无法撤销。",
                color=SunnyColors.Secondary,fontSize=14.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Action("取消",id="dialog:delete-cancel",autoFocus=true,onClick=onDismiss)
                Action("确认删除",id="dialog:delete-confirm",primary=true,onClick=onConfirm)
            }
        }
    }
}
