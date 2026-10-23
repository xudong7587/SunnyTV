package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    onDismiss:()->Unit,columns:Int=1,description:String="",onChoose:(String)->Unit) {
    val model=LocalAppModel.current
    val page=LocalPageKey.current
    val prior=remember {model.focusMemory[page]}
    val inputMotion=LocalTvFocusMotion.current
    LaunchedEffect(Unit) {inputMotion.horizontal=false}
    DisposableEffect(Unit) {onDispose {if(prior!=null) model.focusMemory[page]=prior}}
    val grid=columns>1
    val selectedIndex=choices.indexOfFirst {it.first==selected}.coerceAtLeast(0)
    val listState=rememberLazyListState(initialFirstVisibleItemIndex=selectedIndex)
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
      val window=(LocalView.current.parent as? DialogWindowProvider)?.window
      SideEffect {window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window?.setDimAmount(0f)}
      BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.Center) {
        val panelHeight=maxHeight.coerceAtMost(if(maxWidth<600.dp) 760.dp else 560.dp)
        val gridColumns=if(maxWidth<600.dp) minOf(columns,2) else columns
        Column(Modifier.testTag("choice:panel").onPreviewKeyEvent {inputMotion.record(it);false}.widthIn(max=if(grid) 640.dp else 460.dp)
            .fillMaxWidth().heightIn(max=panelHeight).choicePanel(28.dp).padding(20.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(title,color=SunnyColors.Text,fontSize=21.sp,fontWeight=FontWeight.SemiBold)
            if(description.isNotBlank()) Text(description,color=SunnyColors.Secondary,fontSize=12.sp,lineHeight=18.sp)
          CompositionLocalProvider(LocalGlassControls provides true,LocalChoiceControls provides true) {
            if(grid) {
                Column(Modifier.weight(1f,false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    choices.chunked(gridColumns).forEach {row->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            row.forEach {(value,label)->
                                FocusTile("dialog:$title:$value",Modifier.weight(1f),active=value==selected,shape=sunnyShape(16.dp),
                                    autoFocus=value==selected || (choices.none {it.first==selected} && value==choices.firstOrNull()?.first),
                                    onClick={onChoose(value)}) {
                                    ChoiceLabel(label,value==selected,compact=true)
                                }
                            }
                            repeat(gridColumns-row.size) {Spacer(Modifier.weight(1f))}
                        }
                    }
                }
            } else LazyColumn(Modifier.weight(1f,false),state=listState,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                items(choices,key={it.first}) {(value,label)->
                    FocusTile("dialog:$title:$value",Modifier.fillMaxWidth(),active=value==selected,shape=sunnyShape(16.dp),
                        autoFocus=value==selected || (choices.none {it.first==selected} && value==choices.firstOrNull()?.first),
                        onClick={onChoose(value)}) {
                        ChoiceLabel(label,value==selected)
                    }
                }
            }
            Action("取消",id="dialog:$title:cancel",alwaysShowLabel=true,icon="back",onClick=onDismiss)
          }
        }
      }
    }
}

@Composable private fun ChoiceLabel(label:String,selected:Boolean,compact:Boolean=false) {
    val rowHeight=maxOf(56f,36f*LocalDensity.current.fontScale+16f).dp
    Row(Modifier.fillMaxWidth().then(if(compact) Modifier.height(rowHeight) else Modifier.heightIn(min=48.dp))
        .padding(horizontal=12.dp,vertical=8.dp),
        horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(label,color=SunnyColors.Text,fontSize=if(compact) 13.sp else 15.sp,
            fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Medium,
            lineHeight=18.sp,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
        Box(Modifier.size(18.dp)) {if(selected) LineIcon("check",SunnyColors.Text,Modifier.fillMaxSize())}
    }
}
