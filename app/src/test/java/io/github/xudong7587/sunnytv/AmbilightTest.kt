package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.feature.ambilight.*
import org.junit.Assert.*
import org.junit.Test

class AmbilightTest {
    @Test fun samplesOnlyFivePercentNotInnerPicture() {
        val w=200;val h=100
        fun picture(center:Int)=IntArray(w*h) {i->val x=i%w;val y=i/w
            when {y<5->0xff0000;y>=95->0x0000ff;x<10->0xffff00;x>=190->0x00ff00;else->center}
        }
        val edges=EdgeColors.sample(picture(0xffffff),w,h,true)
        assertArrayEquals(edges,EdgeColors.sample(picture(0x00ffff),w,h,true))
        assertEquals(0xff0000,edges[0]);assertEquals(0x0000ff,edges[2])
        assertTrue((edges[1] shr 8 and 255)>240 && (edges[1] shr 16 and 255)<20)
    }
    @Test fun vividColorsResistNeutralHighlightsAndKeepHue() {
        val p=IntArray(200*100) {if(it%200<100)0x804020 else 0x808080}
        val natural=EdgeColors.sample(p,200,100)[0]
        val vivid=EdgeColors.sample(p,200,100,true)[0]
        fun saturation(c:Int):Double {val r=c shr 16 and 255;val g=c shr 8 and 255;val b=c and 255
            return (maxOf(r,g,b)-minOf(r,g,b)).toDouble()/maxOf(r,g,b)
        }
        assertTrue(saturation(vivid)>saturation(natural)+0.25)
        val r=vivid shr 16 and 255;val g=vivid shr 8 and 255;val b=vivid and 255
        assertTrue(r>g && g>b);assertTrue(r>(natural shr 16 and 255))
        assertEquals(2.0,(r-g).toDouble()/(g-b),0.15)
    }
    @Test fun vividKeepsBlackGrayAndWhiteNeutral() {
        for(rgb in listOf(0,0x050505,0x808080,0xffffff)) {
            assertArrayEquals(IntArray(4){rgb},EdgeColors.sample(IntArray(160*90){rgb},160,90,true))
        }
    }
    @Test fun paleAndDarkSectorColorsStayRecognizableWithVividEnabled() {
        fun saturation(c:Int):Double {
            val high=maxOf(c shr 16 and 255,c shr 8 and 255,c and 255)
            val low=minOf(c shr 16 and 255,c shr 8 and 255,c and 255)
            return (high-low).toDouble()/high
        }
        val pale=EdgeColors.sample(IntArray(160*90){0x9ab7ce},160,90,true)
        pale.forEach {assertTrue("Pale blue should produce colored light",saturation(it)>=.69)}
        val darkEdges=IntArray(160*90) {i->if(i/160 in 10..79 && i%160 in 10..149)0x9ab7ce else 0}
        EdgeColors.sample(darkEdges,160,90,true).forEach {assertTrue("Sector fallback also needs vivid color",saturation(it)>=.69)}
    }
    @Test fun playbackReturnHomeExitAndReentryHaveDifferentIdleColors() {
        val s=LightPresence();val home=Any();val player=Any();val owner=Any()
        val c=LightConfig(enabled=true,fixedColor=0xff4d05)
        s.started(home);assertEquals(c.fixedColor,s.idleColor(c))
        s.started(player);s.play(owner);s.stopped(home)
        assertTrue(s.acceptsFrame(owner,c,false))
        s.started(home);s.stop(owner);s.stopped(player)
        assertFalse(s.acceptsFrame(owner,c,false));assertEquals(c.fixedColor,s.idleColor(c))
        s.stopped(home);assertEquals(0,s.idleColor(c))
        s.started(home);assertEquals(c.fixedColor,s.idleColor(c))
    }
    @Test fun backgroundAndStaleCallbacksCannotRelightStrip() {
        val s=LightPresence();val activity=Any();val old=Any();val current=Any();val c=LightConfig(enabled=true)
        s.started(activity);s.play(old);s.play(current)
        assertFalse(s.stop(old));assertTrue(s.acceptsFrame(current,c,false))
        assertFalse(s.acceptsFrame(old,c,false));assertFalse(s.acceptsFrame(current,c,true))
        assertFalse(s.acceptsFrame(current,c.copy(sync=false),false))
        assertFalse(s.acceptsFrame(current,c.copy(enabled=false),false))
        s.stopped(activity)
        assertFalse(s.acceptsFrame(current,c,false));assertEquals(0,s.idleColor(c))
    }
    @Test fun capturedBlackCommandTurnsEveryUnitBlackInStaticMode() {
        val commands=Sp658eProtocol.fixed(0,255)
        assertArrayEquals(hex("5353340100020a0a"),commands.first())
        assertArrayEquals(hex("5352340100040b0b0bf4"),commands.last())
    }
    @Test fun visibleCropUsesVideoBufferCoordinates() {
        assertArrayEquals(intArrayOf(0,0,3840,2160),VideoCrop.bufferRect(1920,1080,0,0,1920,1080,3840,2160))
        assertArrayEquals(intArrayOf(384,0,3456,2160),VideoCrop.bufferRect(2400,1350,240,0,2160,1350,3840,2160))
    }
    private fun hex(s:String)=s.chunked(2).map {it.toInt(16).toByte()}.toByteArray()
    @Test fun capturedStaticColorCommand() {
        assertArrayEquals(hex("535234010004f4460ef4"),Sp658eProtocol.fixed(0xff4d05,255).last())
    }
    @Test fun requestedSavedCornersAndBlueEndpoints() {
        val l=LightLayout();assertEquals(-1,l.direction());assertEquals(listOf(31,15,34,16),l.lengths())
        val colors=intArrayOf(0xff0000,0x00ff00,0x0000ff,0xffff00)
        val p=Sp658eProtocol.edges(l,colors)
        assertArrayEquals(hex("01040000ff0f00ff001fff000010ffff001e0000ff0000000000000000"),p)
    }
    @Test fun reversedAndCrossedLayouts() {
        assertEquals(1,LightLayout(100,0,25,50,75).direction())
        assertNull(LightLayout(100,0,50,25,75).direction())
        assertNull(LightLayout(96,49,18,3,3).direction())
    }
    @Test fun markerHasExactlyOneWhiteAddressAtEveryPosition() {
        for(address in 0 until 600) {
            val p=Sp658eProtocol.marker(600,address)
            val result=mutableListOf<Int>()
            for(slot in 0..6) {val k=1+slot*4;repeat(p[k].toInt() and 255) {
                result+=((p[k+1].toInt() and 255) shl 16) or ((p[k+2].toInt() and 255) shl 8) or (p[k+3].toInt() and 255)
            }}
            assertEquals(600,result.size);assertEquals(listOf(address),result.indices.filter {result[it]!=0})
        }
    }
    @Test(expected=IllegalArgumentException::class) fun eighthRunIsRejected() {
        Sp658eProtocol.runs(IntArray(8) {it})
    }
    @Test fun stateFragmentsAreReassembledAndExpired() {
        val d=Sp658eStateDecoder();val first=byteArrayOf(0x53,2,0,2,0,2,7,8);val last=byteArrayOf(0x53,2,0,2,1,1,9)
        assertNull(d.accept(first,100));assertNull(d.accept(first,101))
        assertArrayEquals(byteArrayOf(0x53,2,0,1,0,0,7,8,9),d.accept(last,102))
        assertNull(d.accept(first,200));assertNull(d.accept(last,4000))
        assertNull(d.accept(last.copyOfRange(0,6),4001))
    }
    @Test fun samplesActualFourEdges() {
        val w=160;val h=90;val p=IntArray(w*h){0x808080}
        for(y in 0 until h)for(x in 0 until w)p[y*w+x]=when {
            y<8->0xff0000;y>=82->0x0000ff;x<13->0xffff00;x>=147->0x00ff00;else->0x808080
        }
        val c=EdgeColors.sample(p,w,h)
        assertEquals(0xff0000,c[0]);assertEquals(0x0000ff,c[2])
        assertTrue((c[1] shr 8 and 255)>200);assertTrue((c[3] shr 16 and 255)>200)
    }
    @Test fun letterboxDoesNotTurnTopAndBottomLightsBlack() {
        val p=IntArray(160*90){i->if(i/160 in 12..77)0x2277cc else 0}
        assertArrayEquals(IntArray(4){0x2277cc},EdgeColors.sample(p,160,90))
    }
}
