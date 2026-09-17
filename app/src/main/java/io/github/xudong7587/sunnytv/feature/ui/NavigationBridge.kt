package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester

/** Explicit bridge between overlay navigation and scrollable content. */
class NavigationBridge {
    val content=linkedMapOf<String,FocusRequester>()
    val navigation=linkedMapOf<String,FocusRequester>()
    var revealTop:(suspend ()->Unit)?=null
    fun enterContent() {(content["home-play"] ?: content["home-carousel:featured"] ?: content.values.firstOrNull())?.let {runCatching {it.requestFocus()}}}
    fun enterNavigation() {navigation.values.firstOrNull()?.let {runCatching {it.requestFocus()}}}
}
val LocalNavigationBridge=staticCompositionLocalOf<NavigationBridge?> {null}
val LocalNavVisible=staticCompositionLocalOf {true}
