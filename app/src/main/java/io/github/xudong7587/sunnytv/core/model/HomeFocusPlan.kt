package io.github.xudong7587.sunnytv.core.model

/** IDs include the source so equal library names/IDs never share a navigation target. */
data class HomeFocusSection(val key: String, val lazyIndex: Int, val headingId: String?, val mediaPrefix: String)

object HomeFocusPlan {
    const val HERO = "hero"
    const val LIBRARIES = "libraries"
    const val NEXT_UP = "next-up"
    fun latest(libraryKey: String) = "latest:$libraryKey"
    fun sections(libraryKeys: List<String>, hasNextUp: Boolean): List<HomeFocusSection> = buildList {
        add(HomeFocusSection(LIBRARIES, 0, "more:我的媒体库", "library:"))
        if (hasNextUp) add(HomeFocusSection(NEXT_UP, 1, null, "shelf:接着看下一集:"))
        libraryKeys.distinct().forEachIndexed { index, key ->
            add(HomeFocusSection(latest(key), 1 + (if (hasNextUp) 1 else 0) + index, "more:$key", "latest:$key:"))
        }
    }
    fun down(sections: List<HomeFocusSection>, current: String, focusedId: String?): String? {
        // NextUp is a real region, not a zero-height phantom row and never skipped by D-pad.
        if (current == LIBRARIES && sections.any { it.key == NEXT_UP }) return NEXT_UP
        if (current in setOf(LIBRARIES, NEXT_UP) && focusedId?.startsWith("library:") == true) {
            val selected = latest(focusedId.removePrefix("library:"))
            if (sections.any { it.key == selected }) return selected
        }
        val index = sections.indexOfFirst { it.key == current }
        return if (index < 0) null else sections.getOrNull(index + 1)?.key
    }
    fun up(sections: List<HomeFocusSection>, current: String): String? {
        if (current == LIBRARIES) return HERO
        val index = sections.indexOfFirst { it.key == current }
        return if (index <= 0) null else sections[index - 1].key
    }
}
