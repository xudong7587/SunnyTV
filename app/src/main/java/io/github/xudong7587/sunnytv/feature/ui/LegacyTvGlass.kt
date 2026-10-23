package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
 * AGSL diffusion remains API33+; here a narrow magnified rim supplies the lens approximation.
 */
@Composable internal fun Modifier.legacyTvLiquidGlass(source:GlassBackdrop,shape:Shape,radius:Dp,material:String,surfaceAlpha:()->Float):Modifier {
    val palette=LocalSunnyPalette.current
    var coordinates by remember {mutableStateOf<LayoutCoordinates?>(null,neverEqualPolicy())}
    return clip(shape).onGloballyPositioned {coordinates=it}.drawWithContent {
        val src=source.coordinates;val dst=coordinates
        drawRect(palette.background)
        if(source.ready && src?.isAttached==true && dst?.isAttached==true) {
            val offset=dst.positionOnScreen()-src.positionOnScreen()
            translate(-offset.x,-offset.y) {drawLayer(source.layer)}
            // Only the rim refracts: the centre stays aligned with the actual page underneath.
            val edge=3.dp.toPx().coerceAtMost(size.minDimension/4)
            val ring=Path().apply {
                fillType=PathFillType.EvenOdd
                addRect(Rect(Offset.Zero,size))
                addRect(Rect(edge,edge,size.width-edge,size.height-edge))
            }
            clipPath(ring) {
                scale(1.025f,1.025f,pivot=Offset(size.width/2,size.height/2)) {
                    translate(-offset.x,-offset.y) {drawLayer(source.layer)}
                }
            }
        }
        drawRect(palette.surface.copy(alpha=if(material=="frosted") .6f else surfaceAlpha()))
        if(material=="aurora")drawRect(Brush.linearGradient(listOf(palette.accent.copy(.09f),Color.Transparent,palette.accent2.copy(.08f))))
        val rim=shape.createOutline(size,layoutDirection,this)
        drawOutline(rim,Brush.linearGradient(listOf(Color.White.copy(.45f),Color.White.copy(.05f),Color.White.copy(.22f)),Offset.Zero,Offset(size.width,size.height)),style=Stroke(1.dp.toPx()))
        drawContent()
    }
}
