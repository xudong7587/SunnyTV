package io.github.xudong7587.sunnytv.core.model

/** Stable IDs are also the server's sorting keys; never sort only the loaded page. */
object Presentation {
    val sorts = listOf("DateCreated" to "加入日期", "SortName" to "名称", "CommunityRating" to "IMDb / 社区评分",
        "CriticRating" to "影评人评分", "ProductionYear" to "出品年份", "PremiereDate" to "上映时间",
        "OfficialRating" to "官方评级", "DatePlayed" to "播放日期", "Runtime" to "播放时长",
        "Bitrate" to "比特率", "Size" to "大小", "Random" to "随机")
    val subtitles = listOf("none" to "无字幕", "zh-Hans" to "Chinese simple", "zh" to "Chinese", "en" to "English")
    val accents = listOf(
        Triple("象牙米",0xFFF1EBDD,0xFF493D2E), Triple("淡金",0xFFD8C89B,0xFF403B2D),
        Triple("青瓷绿",0xFFBFD8C9,0xFF29483D), Triple("雾青",0xFFADCBC5,0xFF284744),
        Triple("云水蓝",0xFFC6D5E3,0xFF30495F), Triple("淡藤紫",0xFFD7CAE0,0xFF4C3C5B),
        Triple("胭脂粉",0xFFE0BEC1,0xFF603B40), Triple("暖褐",0xFFC9B59F,0xFF4F4033),
        Triple("深黛蓝",0xFF435A70,0xFFF2EEE4), Triple("墨绿色",0xFF61776A,0xFFF3EFE2))
    fun next(index:Int, delta:Int, count:Int, loop:Boolean):Int = if(count<=0) 0
        else if(loop) ((index+delta)%count+count)%count else (index+delta).coerceIn(0,count-1)
    fun canRotate(focused:Boolean,paused:Boolean,resumed:Boolean,visible:Boolean,busy:Boolean,dialog:Boolean,reduceMotion:Boolean,count:Int):Boolean =
        !focused && !paused && resumed && visible && !busy && !dialog && !reduceMotion && count>1
    fun languageMatches(preference:String, language:String, title:String=""):Boolean {
        val l=language.lowercase(); val t=title.lowercase()
        return when(preference) {
            "zh-Hans" -> (l in setOf("zh-hans","zh-cn","chi","zho","zh") &&
                !t.contains("繁") && !t.contains("traditional") && !l.contains("hant")) &&
                (t.contains("简") || t.contains("simpl") || l in setOf("zh-hans","zh-cn"))
            "zh" -> l in setOf("zh","chi","zho","zh-hans","zh-hant","zh-cn","zh-tw")
            "en" -> l in setOf("en","eng","en-us","en-gb")
            else -> false
        }
    }
}
