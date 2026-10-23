package io.github.xudong7587.sunnytv.feature.player

import kotlin.math.*

/** Decoded frame fractions, excluding only consistent encoded black bars. */
internal data class PictureArea(val left:Float=0f,val top:Float=0f,val right:Float=1f,val bottom:Float=1f)
internal data class VideoBounds(val width:Int,val height:Int,val x:Float,val y:Float)
internal object VideoGeometry {
    fun visibleFraction(width:Int,height:Int,bounds:VideoBounds):FloatArray {
        if(bounds.width<=0 || bounds.height<=0)return floatArrayOf(0f,0f,1f,1f)
        val left=(width-bounds.width)/2f+bounds.x;val top=(height-bounds.height)/2f+bounds.y
        return floatArrayOf((-left/bounds.width).coerceIn(0f,1f),(-top/bounds.height).coerceIn(0f,1f),
            ((width-left)/bounds.width).coerceIn(0f,1f),((height-top)/bounds.height).coerceIn(0f,1f))
    }
    fun bounds(width:Int,height:Int,aspect:Float,zoom:Boolean,stretch:Boolean,area:PictureArea=PictureArea()):VideoBounds {
        if(width<=0 || height<=0 || !aspect.isFinite() || aspect<=0)return VideoBounds(width,height,0f,0f)
        if(stretch) {
            val w=(width/(area.right-area.left)).roundToInt();val h=(height/(area.bottom-area.top)).roundToInt()
            return VideoBounds(w,h,(.5f-(area.left+area.right)/2)*w,(.5f-(area.top+area.bottom)/2)*h)
        }
        val scale=if(zoom)max(width/(aspect*(area.right-area.left)),height/(area.bottom-area.top))
            else min(width/aspect,height.toFloat())
        val w=(aspect*scale).roundToInt();val h=scale.roundToInt()
        return VideoBounds(w,h,if(zoom)(.5f-(area.left+area.right)/2)*w else 0f,
            if(zoom)(.5f-(area.top+area.bottom)/2)*h else 0f)
    }
    fun picture(pixels:IntArray,w:Int,h:Int):PictureArea {
        fun dark(c:Int)=maxOf(c shr 16 and 255,c shr 8 and 255,c and 255)<10
        // Do not classify a black scene / isolated lightning as letterboxing.
        if(pixels.count { !dark(it) }<w*h/5)return PictureArea()
        fun row(y:Int)=(0 until w).count {dark(pixels[y*w+it])}>=w*.99
        fun col(x:Int)=(0 until h).count {dark(pixels[it*w+x])}>=h*.99
        var t=0;var b=h;var l=0;var r=w
        while(t<h/4 && row(t))t++
        while(b>h-h/4 && row(b-1))b--
        while(l<w/4 && col(l))l++
        while(r>w-w/4 && col(r-1))r--
        // Subtitles can occupy one of the bars; preserve centred cropping with the smaller margin.
        // PixelCopy downsamples across the black/content boundary: one mixed pixel belongs to
        // that transition. Include it, otherwise a narrow black rim survives the enlargement.
        val vertical=min(t,h-b).takeIf {it>=2}?.let {(it+1).coerceAtMost(h/4)} ?: 0
        val horizontal=min(l,w-r).takeIf {it>=2}?.let {(it+1).coerceAtMost(w/4)} ?: 0
        return PictureArea(horizontal.toFloat()/w,vertical.toFloat()/h,1-horizontal.toFloat()/w,1-vertical.toFloat()/h)
    }
}
