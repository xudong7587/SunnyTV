package io.github.xudong7587.sunnytv.feature.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** API30–32: bounded shared bitmap diffusion on a worker, textured lens triangles on the GPU.
 * This is page artwork only; video surfaces and foreground glyphs never enter this texture.
 */
internal class LegacyGlassFrame(val clear:Bitmap,val frost:Bitmap)
internal fun legacyGlassFrame(image:ImageBitmap):LegacyGlassFrame {
    val original=image.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888,false)
    val w=original.width;val h=original.height
    val pixels=IntArray(w*h);original.getPixels(pixels,0,w,0,0,w,h)
    fun blur(radius:Int):Bitmap {
        val horizontal=IntArray(pixels.size);val output=IntArray(pixels.size)
        fun pass(input:IntArray,result:IntArray,vertical:Boolean) {
            for(y in 0 until h)for(x in 0 until w) {
                var r=0;var g=0;var b=0
                for(d in -radius..radius) {
                    val c=input[(if(vertical)(y+d).coerceIn(0,h-1) else y)*w+if(vertical)x else (x+d).coerceIn(0,w-1)]
                    r+=c shr 16 and 255;g+=c shr 8 and 255;b+=c and 255
                }
                val n=radius*2+1
                result[y*w+x]=0xff000000.toInt() or (r/n shl 16) or (g/n shl 8) or (b/n)
            }
        }
        pass(pixels,horizontal,false);pass(horizontal,output,true)
        return Bitmap.createBitmap(output,w,h,Bitmap.Config.ARGB_8888)
    }
    val result=LegacyGlassFrame(blur(1),blur(3))
    original.recycle()
    return result
}

@Composable internal fun Modifier.legacyLiquidGlass(source:GlassBackdrop,shape:Shape,radius:Dp,material:String,surfaceAlpha:()->Float):Modifier {
    val palette=LocalSunnyPalette.current
    var coordinates by remember {mutableStateOf<LayoutCoordinates?>(null,neverEqualPolicy())}
    val paint=remember {Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter=android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply {setSaturation(.85f)})
    }}
    return clip(shape).onGloballyPositioned {coordinates=it}.drawWithContent {
        val src=source.coordinates;val dst=coordinates;val frame=source.legacyFrame
        drawRect(palette.background)
        if(frame!=null && src?.isAttached==true && dst?.isAttached==true) {
            val bitmap=if(material=="frosted")frame.frost else frame.clear
            val offset=dst.positionOnScreen()-src.positionOnScreen()
            val sx=bitmap.width.toFloat()/src.size.width;val sy=bitmap.height.toFloat()/src.size.height
            val cols=(size.width/12.dp.toPx()).roundToInt().coerceIn(8,64)
            val rows=(size.height/12.dp.toPx()).roundToInt().coerceIn(4,32)
            val vertices=FloatArray((cols+1)*(rows+1)*2);val texture=FloatArray(vertices.size)
            val rounded=(shape.createOutline(size,layoutDirection,this) as? Outline.Rounded)?.roundRect
            val r=(rounded?.topLeftCornerRadius?.x ?: radius.toPx()).coerceIn(0f,size.minDimension/2)
            for(y in 0..rows)for(x in 0..cols) {
                val px=size.width*x/cols;val py=size.height*y/rows
                val qx=abs(px-size.width/2)-(size.width/2-r)
                val qy=abs(py-size.height/2)-(size.height/2-r)
                val d=hypot(max(qx,0f),max(qy,0f))+min(max(qx,qy),0f)-r
                val depth=(-d/18.dp.toPx()).coerceIn(0f,1f)
                val bend=sin(depth*PI).toFloat()*if(material=="frosted") .6.dp.toPx() else 3.dp.toPx()
                val nx=if(qx>qy)sign(px-size.width/2) else 0f
                val ny=if(qy>=qx)sign(py-size.height/2) else 0f
                val i=(y*(cols+1)+x)*2
                vertices[i]=px;vertices[i+1]=py
                texture[i]=(offset.x+px-nx*bend)*sx;texture[i+1]=(offset.y+py-ny*bend)*sy
            }
            val indices=ShortArray(cols*rows*6);var k=0
            for(y in 0 until rows)for(x in 0 until cols) {
                val i=y*(cols+1)+x
                for(v in intArrayOf(i,i+1,i+cols+1,i+1,i+cols+2,i+cols+1))indices[k++]=v.toShort()
            }
            paint.shader=BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
            drawIntoCanvas {it.nativeCanvas.drawVertices(Canvas.VertexMode.TRIANGLES,vertices.size,vertices,0,texture,0,null,0,indices,0,indices.size,paint)}
        }
        drawRect(palette.surface.copy(alpha=if(material=="frosted") .6f else surfaceAlpha()))
        if(material=="aurora")drawRect(Brush.linearGradient(listOf(palette.accent.copy(.09f),Color.Transparent,palette.accent2.copy(.08f))))
        val rim=shape.createOutline(size,layoutDirection,this)
        drawOutline(rim,Brush.linearGradient(listOf(Color.White.copy(.45f),Color.White.copy(.05f),Color.White.copy(.22f)),Offset.Zero,Offset(size.width,size.height)),style=Stroke(1.dp.toPx()))
        drawContent()
    }
}
