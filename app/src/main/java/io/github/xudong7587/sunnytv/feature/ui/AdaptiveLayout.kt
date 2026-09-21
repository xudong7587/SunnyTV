package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import io.github.xudong7587.sunnytv.core.model.*

val LocalCompact=staticCompositionLocalOf {false}
val pageTopPadding @Composable get()=(if(LocalNavVisible.current) 82.dp else 18.dp)+LocalTopInset.current
val pageSidePadding @Composable get()=if(LocalCompact.current) 18.dp else 30.dp

@Composable fun ScaledUi(settings:AppSettings,content:@Composable ()->Unit) {
    val base=LocalDensity.current
    val scale=Presentation.uiScales[settings.uiScaleLevel.coerceIn(0,4)]
    val fontScale=MotionPolicy.fontScales[settings.fontScaleLevel.coerceIn(0,4)]
    CompositionLocalProvider(LocalDensity provides Density(base.density*scale,base.fontScale*fontScale),content=content)
}
