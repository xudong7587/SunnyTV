package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.core.model.MediaEntry
import io.github.xudong7587.sunnytv.feature.Route

@Composable fun PersonScreen(initial:MediaEntry) {
    val model=LocalAppModel.current
    val person=model.details[initial.key] ?: initial
    val works=model.personWorks[initial.key]
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val cell=if(LocalCompact.current) 130.dp else 150.dp
    val columns=((maxWidth-pageSidePadding*2+18.dp)/(cell+18.dp)).toInt().coerceAtLeast(1)
    val state=rememberLazyGridState()
    val navigator=rememberGridFocusNavigator(state)
    val entries=works?.items.orEmpty()
    val offset=if(model.errors["person:${initial.key}"]!=null) 4 else 3
    val more=works!=null && entries.size<works.total
    LazyVerticalGrid(state=state,columns=GridCells.Adaptive(cell),
        modifier=Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(18.dp),verticalArrangement=Arrangement.spacedBy(18.dp),
        contentPadding=PaddingValues(start=pageSidePadding,end=pageSidePadding,top=18.dp,bottom=32.dp)) {
        item(span={GridItemSpan(maxLineSpan)}) {Action("返回",id="person-back",autoFocus=true,icon="back",
            modifier=Modifier.gridFocusTarget(navigator,0,null,if(entries.isNotEmpty()) offset else null)) {model.back()}}
        item(span={GridItemSpan(maxLineSpan)}) {
            Row(horizontalArrangement=Arrangement.spacedBy(22.dp)) {
                ArtworkView(person,person.primary,Modifier.width(if(LocalCompact.current) 100.dp else 170.dp).aspectRatio(2f/3).clip(RoundedCornerShape(16.dp)),400)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text(person.title,color=SunnyColors.Text,fontSize=28.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold)
                    Text(person.overview.ifBlank {if(model.loading["person:${initial.key}"]==true) "正在读取人物介绍…" else "Emby 暂无此人物的介绍"},
                        color=SunnyColors.Secondary,fontSize=14.sp,lineHeight=23.sp)
                }
            }
        }
        model.errors["person:${initial.key}"]?.let {error->item(span={GridItemSpan(maxLineSpan)}) {EmptyState("人物介绍读取失败",error,"重试") {model.loadPerson(initial)}}}
        item(span={GridItemSpan(maxLineSpan)}) {SectionTitle("参演作品",works?.let {"${it.total} 部"}.orEmpty())}
        itemsIndexed(entries,key={_,entry->entry.key}) {index,entry->PosterWallCard(entry,"Poster",
            Modifier.gridFocusTarget(navigator,offset+index,if(index<columns) 0 else offset+index-columns,
                if(index+columns<entries.size) offset+index+columns else if(index/columns<entries.lastIndex/columns) offset+entries.lastIndex else if(more) offset+entries.size else null)) {model.navigate(Route.Detail(entry))}}
        if(works!=null && works.items.size<works.total) item(span={GridItemSpan(maxLineSpan)}) {
            Action("加载更多作品 · ${works.items.size}/${works.total}",modifier=Modifier.gridFocusTarget(navigator,
                offset+entries.size,offset+entries.lastIndex,null)) {model.loadPersonWorks(initial,true)}
        }
        if(works?.items?.isEmpty()==true) item(span={GridItemSpan(maxLineSpan)}) {Text("当前 Emby 中没有此人物关联的影视作品",color=SunnyColors.Secondary)}
        model.errors["person-works:${initial.key}"]?.let {error->item(span={GridItemSpan(maxLineSpan)}) {EmptyState("作品读取失败",error,"重试") {model.loadPersonWorks(initial)}}}
    }
    }
}
