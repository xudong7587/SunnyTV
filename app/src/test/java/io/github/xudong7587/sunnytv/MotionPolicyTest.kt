package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.model.*
import org.junit.Assert.*
import org.junit.Test

class MotionPolicyTest {
    @Test fun speedControlsDurationAndSpringResponseInTheSameDirection() {
        assertEquals(800,MotionPolicy.duration(400,.5f))
        assertEquals(533,MotionPolicy.duration(400,.75f))
        assertEquals(400,MotionPolicy.duration(400,1f))
        assertEquals(267,MotionPolicy.duration(400,1.5f))
        assertEquals(200,MotionPolicy.duration(400,2f))
        assertEquals(0,MotionPolicy.duration(400,0f))
        assertEquals(MotionPolicy.stiffness(1f)*.25f,MotionPolicy.stiffness(.5f),.001f)
        assertEquals(0f,MotionPolicy.speed(AppSettings(reduceMotion=true,animationSpeed=2f)))
        assertEquals(1f,MotionPolicy.speed(AppSettings(animationSpeed=Float.NaN)))
        assertEquals(listOf("0.5x","0.75x","1x","1.5x","2x","关闭动画"),MotionPolicy.speeds.map(MotionPolicy::label))
    }
    @Test fun shelfPinsItsEdgeSlotAndReversesWithoutSkippingItems() {
        val last=ShelfWindow.lastWideSlot(800f,290f,110f,8f)
        assertEquals(4,last)
        var start=0
        val seen=mutableListOf<Int>()
        for(target in 0..9) {
            start=ShelfWindow.startFor(target,start,last)
            assertTrue(target-start in 0..last)
            if(target<=last) assertEquals(0,start) else assertEquals(last,target-start)
            seen+=target
        }
        for(target in 8 downTo 0) {
            start=ShelfWindow.startFor(target,start,last)
            assertTrue(target-start in 0..last)
            seen+=target
        }
        assertEquals((0..9).toList()+(8 downTo 0).toList(),seen)
        assertEquals(0,start)
        assertEquals(0,ShelfWindow.lastWideSlot(180f,180f,110f,8f))
    }
    @Test fun viewAllRevealsOnlyTheRequiredOverflowAndDoesNotShrinkTheLastCard() {
        val offset=ShelfWindow.revealAllOffset(5,110f,290f,8f,108f,800f)
        assertEquals(78f,offset,0f)
        assertEquals(800f,290f+4*110f+5*8f+108f-offset,0f)
        assertEquals(0f,ShelfWindow.revealAllOffset(1,110f,290f,8f,108f,800f),0f)
    }
    @Test fun textSizeHasFiveIndependentLevelsWithAnUnscaledDefault() {
        assertEquals(5,MotionPolicy.fontScales.size)
        assertEquals(1f,MotionPolicy.fontScales[AppSettings().fontScaleLevel])
        assertTrue(MotionPolicy.fontScales.zipWithNext().all {(a,b)->a<b})
    }
}
