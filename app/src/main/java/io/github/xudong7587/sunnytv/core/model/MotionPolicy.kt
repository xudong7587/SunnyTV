package io.github.xudong7587.sunnytv.core.model

import kotlin.math.floor
import kotlin.math.roundToInt

/** Speed is playback speed: 0.5x takes twice as long; zero means no transitions. */
object MotionPolicy {
    val speeds=listOf(.5f,.75f,1f,1.5f,2f,0f)
    val fontScales=listOf(.85f,.925f,1f,1.125f,1.25f)
    fun label(speed:Float):String = when(speed) {0f->"关闭动画";1f->"1x";2f->"2x";else->"${speed}x"}
    fun speed(settings:AppSettings):Float = if(settings.reduceMotion) 0f else
        settings.animationSpeed.takeIf {it in speeds} ?: 1f
    fun duration(baseMs:Int,speed:Float):Int = if(speed<=0f) 0 else (baseMs/speed).roundToInt()
    // Apple-style critically damped response 0.4s, mapped to Compose unit-mass stiffness.
    fun stiffness(speed:Float):Float = 246.7401f*speed*speed
}

/** A bounded shelf window. Geometry changes only along X; focus never asks a LazyRow to scroll. */
object ShelfWindow {
    fun lastWideSlot(viewport:Float,wide:Float,narrow:Float,gap:Float):Int =
        floor(((viewport-wide)/(narrow+gap)).coerceAtLeast(0f)).toInt()
    fun startFor(target:Int,start:Int,lastSlot:Int):Int = when {
        target<start -> target
        target>start+lastSlot -> target-lastSlot
        else -> start
    }.coerceAtLeast(0)
    fun revealAllOffset(count:Int,narrow:Float,wide:Float,gap:Float,allWidth:Float,viewport:Float):Float =
        (wide+(count-1).coerceAtLeast(0)*narrow+count*gap+allWidth-viewport).coerceAtLeast(0f)
}
