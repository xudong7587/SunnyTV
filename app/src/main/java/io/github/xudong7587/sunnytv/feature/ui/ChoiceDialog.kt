package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text

@Composable fun ChoiceDialog(title:String,choices:List<Pair<String,String>>,selected:String,
    onDismiss:()->Unit,onChoose:(String)->Unit) {
    val model=LocalAppModel.current
    val page=LocalPageKey.current
    val prior=remember {model.focusMemory[page]}
    DisposableEffect(Unit) {onDispose {if(prior!=null) model.focusMemory[page]=prior}}
    Dialog(onDismissRequest=onDismiss) {
        Column(Modifier.width(420.dp).heightIn(max=440.dp)
            .background(SunnyColors.Surface,RoundedCornerShape(22.dp))
            .border(1.dp,SunnyColors.Border,RoundedCornerShape(22.dp)).padding(22.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(title,color=SunnyColors.Text,fontSize=23.sp)
            LazyColumn(Modifier.weight(1f,false),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                items(choices,key={it.first}) {(value,label)->
                    FocusTile("dialog:$title:$value",Modifier.fillMaxWidth(),active=value==selected,
                        autoFocus=value==selected || (choices.none {it.first==selected} && value==choices.firstOrNull()?.first),
                        onClick={onChoose(value)}) {
                        Text((if(value==selected) "✓  " else "    ")+label,color=SunnyColors.Text,fontSize=15.sp,
                            modifier=Modifier.fillMaxWidth().padding(12.dp))
                    }
                }
            }
            Action("取消",id="dialog:$title:cancel",onClick=onDismiss)
        }
    }
}
