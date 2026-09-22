package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text

/**
 * A short list of choices.
 *
 * [columns] above 1 lays the options out as a fixed grid instead of a scrolling column. With every
 * option on screen at once the selected tile can always be focused immediately — a long scrolling
 * list silently dropped that focus request for anything below the fold (choosing a sort key under
 * 官方评级 left focus on 取消 instead of the current option).
 */
@Composable fun ChoiceDialog(title:String,choices:List<Pair<String,String>>,selected:String,
    onDismiss:()->Unit,columns:Int=1,onChoose:(String)->Unit) {
    val model=LocalAppModel.current
    val page=LocalPageKey.current
    val prior=remember {model.focusMemory[page]}
    val inputMotion=LocalTvFocusMotion.current
    LaunchedEffect(Unit) {inputMotion.horizontal=false}
    DisposableEffect(Unit) {onDispose {if(prior!=null) model.focusMemory[page]=prior}}
    val grid=columns>1
    Dialog(onDismissRequest=onDismiss) {
        Column(Modifier.onPreviewKeyEvent {inputMotion.record(it);false}.width(if(grid) 640.dp else 420.dp).heightIn(max=if(grid) 560.dp else 440.dp)
            .background(SunnyColors.Surface,RoundedCornerShape(22.dp))
            .border(1.dp,SunnyColors.Border,RoundedCornerShape(22.dp)).padding(22.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(title,color=SunnyColors.Text,fontSize=23.sp)
            if(grid) {
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    choices.chunked(columns).forEach {row->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            row.forEach {(value,label)->
                                FocusTile("dialog:$title:$value",Modifier.weight(1f),active=value==selected,
                                    autoFocus=value==selected || (choices.none {it.first==selected} && value==choices.firstOrNull()?.first),
                                    onClick={onChoose(value)}) {
                                    Text((if(value==selected) "✓ " else "")+label,color=SunnyColors.Text,fontSize=14.sp,
                                        maxLines=2,modifier=Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=11.dp))
                                }
                            }
                            repeat(columns-row.size) {Spacer(Modifier.weight(1f))}
                        }
                    }
                }
            } else LazyColumn(Modifier.weight(1f,false),verticalArrangement=Arrangement.spacedBy(6.dp)) {
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
