package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
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

/** Original 24-unit line icons, a single stroke weight at every display density. */
@Composable fun LineIcon(name:String,color:Color,modifier:Modifier=Modifier) {
    val data=when(name) {
        "home"->"M3 11 L12 3 L21 11 M5 10 L5 21 L10 21 L10 14 L14 14 L14 21 L19 21 L19 10"
        "library"->"M3 5 L3 21 L8 21 L8 5 Z M11 5 L11 21 L15 21 L15 5 Z M17 5 L21 20"
        "cloud"->"M6 18 C0 18 1 10 6 10 C5 2 17 1 18 10 C24 10 24 18 18 18 Z"
        "search"->"M16 16 L21 21 M18 10 C18 0 2 0 2 10 C2 20 18 20 18 10 Z"
        "settings"->"M9 2 L15 2 L16 5 L19 5 L22 10 L20 12 L22 14 L19 19 L16 19 L15 22 L9 22 L8 19 L5 19 L2 14 L4 12 L2 10 L5 5 L8 5 Z M16 12 A4 4 0 1 1 8 12 A4 4 0 1 1 16 12"
        "subtitle"->"M3 5 L21 5 L21 19 L3 19 Z M9 9 C4 7 4 17 9 15 M18 9 C13 7 13 17 18 15"
        "audio"->"M3 10 L3 14 M7 6 L7 18 M12 3 L12 21 M17 6 L17 18 M21 10 L21 14"
        "heart"->"M12 21 C9 18 2 13 2 7 C2 1 10 1 12 6 C14 1 22 1 22 7 C22 13 15 18 12 21 Z"
        "check"->"M4 12 L9 18 L21 5"
        "sort"->"M4 6 L16 6 M4 11 L12 11 M4 16 L8 16 M19 10 L19 21 M15 17 L19 21 L23 17"
        "sort-directions"->"M7 21 L7 3 M2 8 L7 3 L12 8 M17 3 L17 21 M12 16 L17 21 L22 16"
        "ascending"->"M12 21 L12 3 M5 10 L12 3 L19 10"
        "descending"->"M12 3 L12 21 M5 14 L12 21 L19 14"
        "list"->"M4 5 L5 5 M9 5 L21 5 M4 12 L5 12 M9 12 L21 12 M4 19 L5 19 M9 19 L21 19"
        "numbers"->"M9 3 L7 21 M17 3 L15 21 M3 9 L21 9 M2 15 L20 15"
        "layers"->"M2 8 L12 2 L22 8 L12 14 Z M2 13 L12 19 L22 13 M2 18 L12 24 L22 18"
        "calendar"->"M5 4 L5 7 M19 4 L19 7 M4 7 L20 7 L20 21 L4 21 Z M7 11 L10 11 M14 11 L17 11 M7 15 L10 15 M14 15 L17 15"
        "calendar-plus"->"M5 4 L5 7 M19 4 L19 7 M4 7 L20 7 L20 21 L4 21 Z M8 14 L16 14 M12 10 L12 18"
        "sort-alpha"->"M4 19 L8 5 L12 19 M6 13 L10 13 M15 6 L21 6 L15 18 L21 18"
        "rating"->"M12 3 L14.8 8.8 L21 9.7 L16.5 14.1 L17.6 20.5 L12 17.5 L6.4 20.5 L7.5 14.1 L3 9.7 L9.2 8.8 Z"
        "history"->"M4 7 L4 3 M4 7 L8 7 M4 7 C7 2 16 2 20 8 C24 15 19 22 12 22 C7 22 3 19 2 15 M12 8 L12 13 L16 15"
        "clock"->"M12 3 C24 3 24 21 12 21 C0 21 0 3 12 3 Z M12 7 L12 13 L16 15"
        "signal"->"M5 18 L5 14 M10 18 L10 11 M15 18 L15 8 M20 18 L20 5"
        "size"->"M5 6 L19 6 L19 18 L5 18 Z M8 9 L16 9 M8 12 L13 12 M8 15 L11 15"
        "shuffle"->"M3 7 L6 7 C11 7 12 17 18 17 L21 17 M17 13 L21 17 L17 21 M3 17 L6 17 C9 17 11 12 13 9 C14 7 16 7 18 7 L21 7 M17 3 L21 7 L17 11"
        "back"->"M14 4 L6 12 L14 20 M6 12 L22 12"
        "backspace"->"M9.5 5 L21 5 L21 19 L9.5 19 L3 12 Z M12 9.4 L17 14.6 M17 9.4 L12 14.6"
        "play"->"M6 3 L21 12 L6 21 Z"
        "pause"->"M7 4 L7 20 M17 4 L17 20"
        // Two solid triangles per side, the shape every video player uses for rewind / fast forward.
        "rewind"->"M12 5 L12 19 L3 12 Z M21 5 L21 19 L12 12 Z"
        "forward"->"M12 5 L12 19 L21 12 Z M3 5 L3 19 L12 12 Z"
        "frame"->"M9 3 L3 3 L3 9 M15 3 L21 3 L21 9 M21 15 L21 21 L15 21 M9 21 L3 21 L3 15"
        "close"->"M5 5 L19 19 M19 5 L5 19"
        "exit"->"M10 3 L3 3 L3 21 L10 21 M10 12 L22 12 M17 7 L22 12 L17 17"
        "speed"->"M3 19 C-1 3 25 3 21 19 M12 15 L17 8 M5 17 L6 17 M18 17 L19 17"
        "repeat"->"M3 10 C3 2 17 0 21 7 M21 2 L21 7 L16 7 M21 14 C21 22 7 24 3 17 M3 22 L3 17 L8 17"
        "restart"->"M20 8 C17 2 7 2 4 9 M4 4 L4 9 L9 9 M5 14 C7 21 18 22 21 14 M10 8 L17 12 L10 16 Z"
        "previous"->"M5 4 L5 20 M19 5 L8 12 L19 19 Z"
        "next"->"M19 4 L19 20 M5 5 L16 12 L5 19 Z"
        "chapters"->"M4 6 L5 6 M9 6 L21 6 M4 12 L5 12 M9 12 L21 12 M4 18 L5 18 M9 18 L21 18"
        "cast"->"M12 11 C16 11 16 4 12 4 C8 4 8 11 12 11 Z M4 21 C4 14 20 14 20 21 M18 5 C21 6 22 9 21 12"
        // A crescent whose bite sits in the upper right: two arcs of the same sweeping direction.
        "sleep"->"M6.45 4.92 A9 9 0 1 0 19.08 17.55 A11 11 0 0 1 6.45 4.92"
        "skip"->"M4 5 L13 12 L4 19 Z M13 5 L22 12 L13 19 Z"
        "info"->"M12 2 C25 2 25 22 12 22 C-1 22 -1 2 12 2 Z M12 11 L12 17 M12 7 L12 7.2"
        "arrow"->"M3 12 L21 12 M14 5 L21 12 L14 19"
        "trash"->"M4 6 L20 6 M9 6 L9 3 L15 3 L15 6 M6 6 L7 21 L17 21 L18 6 M10 10 L10 17 M14 10 L14 17"
        else->"M12 4 L12 20 M4 12 L20 12"
    }
    val path=remember(data) {PathParser().parsePathString(data).toPath()}
    Canvas(modifier.size(19.dp)) {
        scale(size.width/24,size.height/24,pivot=androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(path,color,style=Stroke(1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round))
        }
    }
}

/**
 * The sort indicator: an up arrow and a down arrow of exactly the same height, stacked. The active
 * direction is drawn at full strength and the other one faintly, so the current order reads at a
 * glance without any extra text.
 */
@Composable fun SortDirectionArrows(ascending:Boolean,color:Color,modifier:Modifier=Modifier,enabled:Boolean=true) {
    val up=remember {"M12 10 L12 3 M7 8 L12 3 L17 8"}
    val down=remember {"M12 14 L12 21 M7 16 L12 21 L17 16"}
    val upPath=remember(up) {PathParser().parsePathString(up).toPath()}
    val downPath=remember(down) {PathParser().parsePathString(down).toPath()}
    val active=if(enabled) color else color.copy(alpha=.45f)
    Canvas(modifier.size(19.dp)) {
        scale(size.width/24,size.height/24,pivot=androidx.compose.ui.geometry.Offset.Zero) {
            val stroke=Stroke(1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round)
            drawPath(upPath,if(enabled && ascending) active else active.copy(alpha=.42f),style=stroke)
            drawPath(downPath,if(enabled && !ascending) active else active.copy(alpha=.42f),style=stroke)
        }
    }
}
