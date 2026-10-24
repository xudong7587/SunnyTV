package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** API30–32 uses the live display list. Child RenderNode scrolling must remain live even when
 * the capture parent's draw lambda is not rerun. No GPU readback, bitmap upload or CPU blur.
 * AGSL diffusion remains API33+; one gently magnified layer supplies the lens approximation.
 */
@Composable internal fun Modifier.legacyTvLiquidGlass(source:GlassBackdrop,shape:Shape,radius:Dp,material:String,surfaceAlpha:()->Float):Modifier {
    val palette=LocalSunnyPalette.current
    var coordinates by remember {mutableStateOf<LayoutCoordinates?>(null,neverEqualPolicy())}
    return clip(shape).onGloballyPositioned {coordinates=it}.drawWithContent {
        val src=source.coordinates;val dst=coordinates
        drawRect(palette.background)
        if(source.ready && src?.isAttached==true && dst?.isAttached==true) {
            val offset=dst.positionOnScreen()-src.positionOnScreen()
            // A rectangular inner rim cut left straight seams inside circular buttons. Draw the
            // live scene once, without a second crop edge or a discontinuous refraction band.
            scale(1.015f,1.015f,pivot=Offset(size.width/2,size.height/2)) {
                translate(-offset.x,-offset.y) {drawLayer(source.layer)}
            }
        }
        drawRect(palette.surface.copy(alpha=if(material=="frosted") .6f else surfaceAlpha()))
        if(material=="aurora")drawRect(Brush.linearGradient(listOf(palette.accent.copy(.09f),Color.Transparent,palette.accent2.copy(.08f))))
        val stroke=.75.dp.toPx()
        val rim=concentricInsetOutline(shape,size,stroke/2,layoutDirection,this)
        drawOutline(rim,Brush.linearGradient(listOf(Color.White.copy(.36f),Color.White.copy(.04f),Color.White.copy(.18f)),Offset.Zero,Offset(size.width,size.height)),style=Stroke(stroke))
        drawContent()
    }
}
