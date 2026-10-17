package io.github.xudong7587.sunnytv
import io.github.xudong7587.sunnytv.core.model.SubtitleLanguageOrder
import org.junit.Assert.*
import org.junit.Test

class SubtitleLanguageOrderTest {
    @Test fun chineseTracksSortFirstWithoutDroppingOtherLanguages() {
        val tracks=listOf("en" to "English","zh-TW" to "繁體","chi" to "中文","zh-Hans" to "简体","ja" to "Japanese")
        assertEquals(listOf("zh-Hans","chi","zh-TW","en","ja"),tracks.sortedBy {SubtitleLanguageOrder.priority(it.first,it.second)}.map {it.first})
        assertEquals(0,SubtitleLanguageOrder.priority("und","简体双语"))
        assertEquals(2,SubtitleLanguageOrder.priority("und","繁體中文"))
    }
}
