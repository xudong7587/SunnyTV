package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp


fun librarySortIcon(sort:String):String=when(sort) {
    "DateCreated" -> "calendar-plus"
    "SortName" -> "sort-alpha"
    "CommunityRating","CriticRating","OfficialRating" -> "rating"
    "ProductionYear","PremiereDate" -> "calendar"
    "DatePlayed" -> "history"
    "Runtime" -> "clock"
    "Bitrate" -> "signal"
    "Size" -> "size"
    "Random" -> "shuffle"
    else -> "sort"
}

fun actionIcon(text:String):String=when {
    text.contains("确认") || text.contains("保存") || text.contains("知道了") || text.contains("完成") -> "check"
    text.contains("收藏") -> "heart"
    text.contains("已看") -> "check"
    text.contains("升序") -> "ascending"
    text.contains("降序") -> "descending"
    text.contains("首页") -> "home"
    text.contains("媒体库") || text.contains("文件夹") || text.contains("来源") -> "library"
    text.contains("云盘") || text.contains("CloudDrive") -> "cloud"
    text.contains("搜索") -> "search"
    text.contains("设置") || text.contains("配置") -> "settings"
    text.contains("字幕") -> "subtitle"
    text.contains("音频") || text.contains("音轨") -> "audio"
    text.contains("排序") || text.contains("升序") || text.contains("降序") -> "sort"
    text.contains("版本") || text.contains("视图") || text.contains("展现") -> "layers"
    text.contains("返回") || text.contains("取消") || text.contains("关闭") -> "back"
    text.contains("暂停") -> "pause"
    text.contains("播放") -> "play"
    text.contains("轮播") || text.contains("切换") || text.contains("刷新") || text.contains("重试") -> "repeat"
    text.contains("详情") || text.contains("信息") || text.contains("知道") -> "info"
    text.contains("更多") || text.contains("全部") || text.contains("进入") -> "arrow"
    text.contains("清理") || text.contains("移除") || text.contains("重置") || text.contains("删除") -> "trash"
    text.startsWith("＋") || text.contains("添加") -> "plus"
    else -> "settings"
}

/** Shared original vectors. Fill/stroke choice belongs to each symbol, independent of density. */
@Composable fun LineIcon(name:String,color:Color,modifier:Modifier=Modifier) {
    val definition=SunnyIconCatalog.icons[name] ?: SunnyIconCatalog.icons.getValue("plus")
    val paths=remember(definition) {definition.map {it to PathParser().parsePathString(it.pathData).toPath()}}
    Canvas(modifier.size(20.dp)) {
        val unit=size.minDimension/SunnyIconCatalog.viewport
        translate((size.width-size.minDimension)/2,(size.height-size.minDimension)/2) {
            scale(unit,unit,pivot=androidx.compose.ui.geometry.Offset.Zero) {
                paths.forEach {(layer,path)->
                    if(layer.filled) drawPath(path,color)
                    else drawPath(path,color,style=Stroke(layer.strokeWidth,cap=StrokeCap.Round,join=StrokeJoin.Round))
                }
            }
        }
    }
}

/** The active half keeps its original weight; only the inactive half changes opacity. */
@Composable fun SortDirectionArrows(ascending:Boolean,color:Color,modifier:Modifier=Modifier,enabled:Boolean=true) {
    val up=SunnyIconCatalog.icons.getValue("sort-up-half").single()
    val down=SunnyIconCatalog.icons.getValue("sort-down-half").single()
    val upPath=remember(up) {PathParser().parsePathString(up.pathData).toPath()}
    val downPath=remember(down) {PathParser().parsePathString(down.pathData).toPath()}
    val active=if(enabled) color else color.copy(alpha=.45f)
    Canvas(modifier.size(20.dp)) {
        val unit=size.minDimension/24f
        translate((size.width-size.minDimension)/2,(size.height-size.minDimension)/2) {
            scale(unit,unit,pivot=androidx.compose.ui.geometry.Offset.Zero) {
                drawPath(upPath,if(enabled && ascending) active else active.copy(alpha=.42f),style=Stroke(up.strokeWidth,cap=StrokeCap.Round,join=StrokeJoin.Round))
                drawPath(downPath,if(enabled && !ascending) active else active.copy(alpha=.42f),style=Stroke(down.strokeWidth,cap=StrokeCap.Round,join=StrokeJoin.Round))
            }
        }
    }
}
