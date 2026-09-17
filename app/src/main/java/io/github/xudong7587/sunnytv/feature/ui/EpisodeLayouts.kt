package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.Route

@Composable fun EpisodeLayoutButtons(parent:MediaEntry,mode:String) {
    val model=LocalAppModel.current
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf(Triple("horizontal","横向海报","layers"),Triple("vertical","竖向列表","list"),Triple("numbers","数字选集","numbers")).forEach {(key,label,icon)->
            Action(label,id="episode-layout:$key",active=mode==key,icon=icon) {
                model.saveSettings(model.settings.copy(episodeLayouts=model.settings.episodeLayouts+(parent.key to key)))
            }
        }
    }
}

@Composable fun EpisodeHorizontal(entries:List<MediaEntry>) {
    val model=LocalAppModel.current
    StableLazyRow(Modifier.fillMaxWidth().focusGroup(),horizontalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(4.dp)) {
        items(entries,key={it.key}) {entry->
            Column(Modifier.width(240.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                MediaCard(entry,wide=true,onClick={model.navigate(Route.Detail(entry))},focusId="episode:${entry.key}")
                Text(entry.overview.ifBlank {"暂无简介"},color=SunnyColors.Secondary,fontSize=12.sp,lineHeight=18.sp,
                    minLines=2,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable fun EpisodeListCard(entry:MediaEntry) {
    val model=LocalAppModel.current
    FocusTile("episode:${entry.key}",Modifier.fillMaxWidth(),onClick={model.navigate(Route.Detail(entry))}) {
        Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
            ArtworkView(entry,MediaLogic.wideArtwork(entry),Modifier.width(if(LocalCompact.current) 110.dp else 185.dp).aspectRatio(16f/9),480)
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
        onClick={model.navigate(Route.Detail(entry))}) {
        Text(number.toString(),color=SunnyColors.Text,fontSize=18.sp,fontWeight=FontWeight.Medium)
    }
}
