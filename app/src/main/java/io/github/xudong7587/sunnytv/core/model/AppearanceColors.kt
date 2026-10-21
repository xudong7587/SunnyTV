package io.github.xudong7587.sunnytv.core.model

import kotlin.math.*

/** One seed per palette. Decorative tones retain its hue; semantic status colors are separate. */
data class AppearanceColor(val id:String,val name:String,val seed:Long) {
    val multi=false
    val light=MonoColor.tone(seed,false)
    val dark=MonoColor.tone(seed,true)
    val light2=MonoColor.mix(light,0xFFFFFFFF,.18)
    val light3=MonoColor.mix(light,0xFF000000,.14)
    val dark2=MonoColor.mix(dark,0xFFFFFFFF,.18)
    val dark3=MonoColor.mix(dark,0xFF000000,.14)
}
object AppearanceColors {
    const val DEFAULT_CUSTOM=0xFF376BD8L
    val all=listOf(
        AppearanceColor("blue","晴蓝",0xFF376BD8),
        AppearanceColor("celadon","青瓷",0xFF2F7F76),
        AppearanceColor("violet","鸢紫",0xFF8855CC),
        AppearanceColor("rose","莓红",0xFFC34278),
        AppearanceColor("brick","砖窑",0xFFA8433A),
        AppearanceColor("amber","琥珀",0xFFB27A16),
        AppearanceColor("indigo","靛蓝",0xFF3B4FA8))
    fun find(id:String)=all.firstOrNull {it.id==id}
    fun custom(argb:Long)=AppearanceColor("custom","自定义",opaque(argb))
    fun opaque(argb:Long)=(argb and 0xFFFFFFL) or 0xFF000000L
}
/** Platform-independent HSV picker and sRGB tonal ladder, including black/white and grey seeds. */
object MonoColor {
    fun mix(a:Long,b:Long,t:Double):Long {
        var result=0xFF000000L
        for(shift in listOf(16,8,0)) {
            val x=(a shr shift) and 255;val y=(b shr shift) and 255
            result=result or (round(x+(y-x)*t.coerceIn(0.0,1.0)).toLong() shl shift)
        }
        return result
    }
    fun luminance(rgb:Long):Double {
        fun channel(shift:Int):Double {val c=((rgb shr shift) and 255)/255.0;return if(c<=.04045) c/12.92 else ((c+.055)/1.055).pow(2.4)}
        return .2126*channel(16)+.7152*channel(8)+.0722*channel(0)
    }
    fun tone(seed:Long,dark:Boolean):Long {
        val c=AppearanceColors.opaque(seed)
        val target=if(dark) .50 else .18
        val lum=luminance(c)
        if(if(dark) lum>=target && lum<=.72 else lum in .10..target) return c
        val end=if(lum<target) 0xFFFFFFFFL else 0xFF000000L
        var lo=0.0;var hi=1.0
        repeat(16) {
            val mid=(lo+hi)/2;val m=luminance(mix(c,end,mid))
            if((m<target)==(lum<target)) lo=mid else hi=mid
        }
        return mix(c,end,hi)
    }
    fun hsv(hue:Float,saturation:Float,value:Float):Long {
        val h=((hue%360+360)%360)/60;val s=saturation.coerceIn(0f,1f);val v=value.coerceIn(0f,1f)
        val c=v*s;val x=c*(1-abs(h%2-1));val m=v-c
        val (r,g,b)=when(h.toInt()) {0->Triple(c,x,0f);1->Triple(x,c,0f);2->Triple(0f,c,x);3->Triple(0f,x,c);4->Triple(x,0f,c);else->Triple(c,0f,x)}
        return 0xFF000000L or (((r+m)*255).roundToLong() shl 16) or (((g+m)*255).roundToLong() shl 8) or ((b+m)*255).roundToLong()
    }
    fun toHsv(rgb:Long):FloatArray {
        val r=((rgb shr 16) and 255)/255f;val g=((rgb shr 8) and 255)/255f;val b=(rgb and 255)/255f
        val max=max(r,max(g,b));val min=min(r,min(g,b));val d=max-min
        val h=if(d==0f) 0f else when(max) {r->60*((g-b)/d%6);g->60*((b-r)/d+2);else->60*((r-g)/d+4)}
        return floatArrayOf((h+360)%360,if(max==0f) 0f else d/max,max)
    }
    fun parseHex(value:String):Long?=value.removePrefix("#").takeIf {it.matches(Regex("[0-9a-fA-F]{6}"))}
        ?.toLongOrNull(16)?.let(AppearanceColors::opaque)
    fun hex(rgb:Long)="#%06X".format(rgb and 0xFFFFFF)
}
