package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester

/** Explicit bridge between overlay navigation and scrollable content. */
class NavigationBridge {
    val content=linkedMapOf<String,FocusRequester>()
    val navigation=linkedMapOf<String,FocusRequester>()
    /** True while any pinned navigation tile holds focus: used to detect "already at the top". */
    var navActive by mutableStateOf(false)
    /**
     * The tile a page wants "Down" from the pinned navigation to land on. A page sets this when its
     * first control is not simply the first registered one (the detail page wants its play button,
     * not whichever tile happened to compose first).
     */
    var contentEntryId:String?=null
    /** The navigation tile that stands for the page on screen, so "Up" lands on the current tab. */
    var navEntryId:String?=null
    var revealTop:(suspend ()->Unit)?=null
    fun enterContent() {
        val target=contentEntryId?.let {content[it]} ?: content["home-play"] ?: content["home-carousel:featured"]
            ?: content.values.firstOrNull()
        target?.let {runCatching {it.requestFocus()}}
    }
    fun enterNavigation() {
        (navEntryId?.let {navigation[it]} ?: navigation.values.firstOrNull())?.let {runCatching {it.requestFocus()}}
    }
}
val LocalNavigationBridge=staticCompositionLocalOf<NavigationBridge?> {null}
val LocalNavVisible=staticCompositionLocalOf {true}
