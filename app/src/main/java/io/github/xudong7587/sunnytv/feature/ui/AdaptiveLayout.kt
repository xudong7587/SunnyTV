package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp

val LocalCompact=staticCompositionLocalOf {false}
val pageTopPadding get()=82.dp
val pageSidePadding @Composable get()=if(LocalCompact.current) 18.dp else 30.dp
