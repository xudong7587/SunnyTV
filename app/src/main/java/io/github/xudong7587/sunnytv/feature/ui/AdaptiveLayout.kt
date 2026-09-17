package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import io.github.xudong7587.sunnytv.core.model.*

val LocalCompact=staticCompositionLocalOf {false}
val pageTopPadding get()=82.dp
val pageSidePadding @Composable get()=if(LocalCompact.current) 18.dp else 30.dp

@Composable fun ScaledUi(settings:AppSettings,content:@Composable ()->Unit) {
    val base=LocalDensity.current
    val scale=Presentation.uiScales[settings.uiScaleLevel.coerceIn(0,4)]
    CompositionLocalProvider(LocalDensity provides Density(base.density*scale,base.fontScale),content=content)
}
