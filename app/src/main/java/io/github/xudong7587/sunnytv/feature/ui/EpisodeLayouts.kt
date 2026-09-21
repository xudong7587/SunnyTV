package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.Route

@Composable fun EpisodeLayoutButtons(parent:MediaEntry,mode:String,firstFocusRequester:FocusRequester?=null,collapseWhenIdle:Boolean=false) {
    val model=LocalAppModel.current
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf(Triple("horizontal","横向海报","layers"),Triple("vertical","竖向列表","list"),Triple("numbers","数字选集","numbers")).forEachIndexed { index,(key,label,icon)->
            Action(label,id="episode-layout:$key",active=mode==key,icon=icon,collapseWhenIdle=collapseWhenIdle,
                modifier=if(index==0 && firstFocusRequester!=null) Modifier.focusRequester(firstFocusRequester) else Modifier) {
                model.saveSettings(model.settings.copy(episodeLayouts=model.settings.episodeLayouts+(parent.key to key)))
            }
        }
    }
}

@Composable fun EpisodeHorizontal(entries:List<MediaEntry>,firstFocusRequester:FocusRequester?=null,onNeedMore:()->Unit={},gutter:Dp=4.dp) {
    val model=LocalAppModel.current
    val state=androidx.compose.foundation.lazy.rememberLazyListState()
    val scope=rememberCoroutineScope()
    val reveal=remember(entries.map {it.key}) {entries.associate {it.key to BringIntoViewRequester()}}
    val approachingEnd by remember(entries.size,state) {derivedStateOf {
        val last=state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        entries.isNotEmpty() && last>=entries.lastIndex-3
    }}
    LaunchedEffect(approachingEnd,entries.size) {if(approachingEnd) onNeedMore()}
    // Align the row with its section heading; the gutter inside the row keeps the focus shadow.
    StableLazyRow(Modifier.fillMaxWidth().focusGroup(),state=state,horizontalArrangement=Arrangement.spacedBy(12.dp),
        contentPadding=PaddingValues(horizontal=gutter,vertical=4.dp),reserveFocusSpace=false) {
        itemsIndexed(entries,key={ _,entry->entry.key}) {index,entry->
            // Focusing a card reveals the whole column: poster, title and the synopsis below it.
            val column=reveal[entry.key]
            Column(Modifier.width(240.dp).then(if(column!=null) Modifier.bringIntoViewRequester(column) else Modifier),
                verticalArrangement=Arrangement.spacedBy(6.dp)) {
                MediaCard(entry,wide=true,onClick={model.navigate(Route.Detail(entry))},focusId="episode:${entry.key}",
                    onFocus={column?.let {requester->scope.launch {requester.bringIntoView()}}},
                    modifier=if(index==0 && firstFocusRequester!=null) Modifier.focusRequester(firstFocusRequester) else Modifier)
                Text(entry.overview.ifBlank {"暂无简介"},color=SunnyColors.Secondary,fontSize=12.sp,lineHeight=18.sp,
                    minLines=2,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable fun EpisodeListCard(entry:MediaEntry,modifier:Modifier=Modifier) {
    val model=LocalAppModel.current
    val imageWidth=if(LocalCompact.current) 110.dp else 185.dp
    // 20dp outer radius minus the 12dp inset gives an 8dp image radius, sharing corner centres.
    FocusTile("episode:${entry.key}",Modifier.fillMaxWidth().then(modifier),shape=RoundedCornerShape(20.dp),
        onLongPress={model.showItemActions(entry)},onClick={model.navigate(Route.Detail(entry))}) {
        Row(Modifier.fillMaxWidth().padding(12.dp).height(IntrinsicSize.Min).heightIn(min=imageWidth*9f/16f),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
            ArtworkView(entry,MediaLogic.wideArtwork(entry),Modifier.width(imageWidth).fillMaxHeight().clip(RoundedCornerShape(8.dp)),480)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(entry.title,color=SunnyColors.Text,fontSize=16.sp,lineHeight=21.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(entry.overview.ifBlank {"暂无简介"},color=SunnyColors.Secondary,fontSize=13.sp,lineHeight=20.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable fun EpisodeNumber(entry:MediaEntry,index:Int,modifier:Modifier=Modifier) {
    val model=LocalAppModel.current
    val number=entry.episode.takeIf {it>0} ?: index+1
    FocusTile("episode:${entry.key}",modifier.height(52.dp).semantics {contentDescription="第 $number 集 · ${entry.title}"},
        onLongPress={model.showItemActions(entry)},onClick={model.navigate(Route.Detail(entry))}) {
        Text(number.toString(),color=SunnyColors.Text,fontSize=18.sp,fontWeight=FontWeight.Medium)
    }
}
