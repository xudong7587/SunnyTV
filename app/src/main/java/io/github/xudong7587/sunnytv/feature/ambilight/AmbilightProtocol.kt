package io.github.xudong7587.sunnytv.feature.ambilight

/** Addresses are controller units, which can contain more than one physical LED. */
data class LightLayout(val count:Int=96,val topLeft:Int=49,val topRight:Int=18,
    val bottomRight:Int=3,val bottomLeft:Int=65) {
    val corners get()=listOf(topLeft,topRight,bottomRight,bottomLeft)
    fun direction():Int? {
        if(count !in 4..600 || corners.any {it !in 0 until count} || corners.distinct().size!=4) return null
        return listOf(1,-1).firstOrNull {d->corners.indices.sumOf {i->
            Math.floorMod((corners[(i+1)%4]-corners[i])*d,count)
        }==count}
    }
    fun lengths():List<Int> = direction()?.let {d->corners.indices.map {i->
        Math.floorMod((corners[(i+1)%4]-corners[i])*d,count)
    }} ?: emptyList()
    fun side(address:Int):Int {
        val d=requireNotNull(direction())
        val offset=Math.floorMod((address-topLeft)*d,count)
        var end=0
        lengths().forEachIndexed {i,n->end+=n;if(offset in (end-n) until end) return i}
        return 3
    }
}

data class LightConfig(val enabled:Boolean=false,val sync:Boolean=true,val fixedColor:Int=0xff4d05,
    val brightness:Int=255,val fps:Int=10,val layout:LightLayout=LightLayout(),val deviceAddress:String="",val vivid:Boolean=true)

object Sp658eProtocol {
    val query=byteArrayOf(0x53,2,0x5b,1,0,1,0xdd.toByte())
    fun frame(command:Int,payload:ByteArray,seed:Int=0x34):ByteArray {
        require(payload.size<=255)
        return byteArrayOf(0x53,command.toByte(),seed.toByte(),1,0,payload.size.toByte())+
            payload.map {(it.toInt() xor seed xor 0x3f).toByte()}.toByteArray()
    }
    fun fixed(rgb:Int,brightness:Int)=listOf(frame(0x53,byteArrayOf(1,1)),
        frame(0x52,byteArrayOf((rgb shr 16).toByte(),(rgb shr 8).toByte(),rgb.toByte(),brightness.coerceIn(1,255).toByte())))

    /** Select an effect only on a real mode change. Re-selecting a static effect can restart
     * the controller output even when its RGB target has not changed. */
    fun update(rgb:Int?,slots:ByteArray?,brightness:Int,previousMode:Int,previousBrightness:Int):List<ByteArray> {
        val level=brightness.coerceIn(1,255)
        return buildList {
            if(rgb!=null) {
                if(previousMode!=1)add(frame(0x53,byteArrayOf(1,1)))
                add(frame(0x52,byteArrayOf((rgb shr 16).toByte(),(rgb shr 8).toByte(),rgb.toByte(),level.toByte())))
            } else {
                if(previousMode!=7 || previousBrightness!=level)add(frame(0x52,byteArrayOf(0,0,0,level.toByte())))
                add(frame(0x63,requireNotNull(slots)))
                if(previousMode!=7)add(frame(0x53,byteArrayOf(7,1)))
            }
        }
    }

    /** Seven run-length slots are a hardware constraint; never silently truncate a layout. */
    fun runs(colors:IntArray):ByteArray {
        require(colors.isNotEmpty() && colors.size<=600)
        val slots=mutableListOf<Pair<Int,Int>>()
        var start=0
        while(start<colors.size) {
            var end=start+1
            while(end<colors.size && colors[end]==colors[start] && end-start<150) end++
            slots+=Pair(end-start,colors[start]);start=end
        }
        require(slots.size<=7) {"四边分段超过控制器的七段限制，请调整定位"}
        return ByteArray(29).also {p->p[0]=1;slots.forEachIndexed {i,(n,c)->
            val k=1+i*4;p[k]=n.toByte();p[k+1]=(c shr 16).toByte();p[k+2]=(c shr 8).toByte();p[k+3]=c.toByte()
        }}
    }
    fun edges(layout:LightLayout,colors:IntArray):ByteArray {
        require(colors.size==4 && layout.direction()!=null)
        val pixels=IntArray(layout.count);val direction=requireNotNull(layout.direction())
        val lengths=layout.lengths()
        layout.corners.forEachIndexed {side,start->repeat(lengths[side]) {offset->
            pixels[Math.floorMod(start+direction*offset,layout.count)]=colors[side]
        }}
        return runs(pixels)
    }
    fun marker(count:Int,address:Int)=runs(IntArray(count) {if(it==address) 0xffffff else 0})
}

/** Empirical RGB load envelope: this installation is stable at 24% full-strip white.
 * Scale all channels together, including static previews, without changing the hardware level
 * on every video frame. This is a conservative channel-load proxy, not a current measurement.
 */
internal object LightOutputLimit {
    private const val CHANNEL_BUDGET=183 // 3 * 61 (24% of 255, rounded down)
    private fun sum(rgb:Int)=(rgb shr 16 and 255)+(rgb shr 8 and 255)+(rgb and 255)
    private fun scale(rgb:Int,factor:Double):Int =
        (((rgb shr 16 and 255)*factor).toInt() shl 16) or
        (((rgb shr 8 and 255)*factor).toInt() shl 8) or ((rgb and 255)*factor).toInt()
    private fun factor(average:Double,brightness:Int)=if(average<=0)1.0 else
        (CHANNEL_BUDGET*255.0/(average*brightness.coerceIn(1,255))).coerceAtMost(1.0)
    fun fixed(rgb:Int,brightness:Int)=scale(rgb,factor(sum(rgb).toDouble(),brightness))
    fun slots(slots:ByteArray,brightness:Int):ByteArray {
        require(slots.size==29)
        var units=0;var load=0L
        for(k in 1 until slots.size step 4) {
            val n=slots[k].toInt() and 255
            units+=n
            load+=n*((slots[k+1].toInt() and 255)+(slots[k+2].toInt() and 255)+(slots[k+3].toInt() and 255))
        }
        val factor=factor(if(units==0)0.0 else load.toDouble()/units,brightness)
        return slots.clone().also {result->
            for(k in 1 until result.size step 4)for(channel in 1..3)
                result[k+channel]=((result[k+channel].toInt() and 255)*factor).toInt().toByte()
        }
    }
}

object VideoCrop {
    fun visiblePixels(input:IntArray,output:IntArray,width:Int,height:Int,bounds:IntArray) {
        require(input.size>=width*height && output.size>=width*height && bounds.size==4)
        val left=bounds[0].coerceIn(0,width-1);val top=bounds[1].coerceIn(0,height-1)
        val right=bounds[2].coerceIn(left+1,width);val bottom=bounds[3].coerceIn(top+1,height)
        for(y in 0 until height)for(x in 0 until width)
            output[y*width+x]=input[(top+y*(bottom-top)/height)*width+left+x*(right-left)/width]
    }
    /** PixelCopy uses buffer pixels, which need not equal SurfaceView layout pixels. */
    fun bufferRect(viewWidth:Int,viewHeight:Int,left:Int,top:Int,right:Int,bottom:Int,bufferWidth:Int,bufferHeight:Int):IntArray {
        require(viewWidth>0 && viewHeight>0 && bufferWidth>0 && bufferHeight>0)
        fun floor(v:Int,b:Int,view:Int)=(v.toLong()*b/view).toInt().coerceIn(0,b)
        fun ceil(v:Int,b:Int,view:Int)=((v.toLong()*b+view-1)/view).toInt().coerceIn(0,b)
        return intArrayOf(floor(left,bufferWidth,viewWidth),floor(top,bufferHeight,viewHeight),
            ceil(right,bufferWidth,viewWidth),ceil(bottom,bufferHeight,viewHeight))
    }
}

/** Notification fragments can arrive separately, duplicated, or with an expired predecessor. */
class Sp658eStateDecoder {
    private var parts=arrayOfNulls<ByteArray>(0)
    private var started=0L
    fun accept(packet:ByteArray,nowMs:Long):ByteArray? {
        if(packet.size<6 || packet[0]!=0x53.toByte() || packet[1]!=2.toByte()) return null
        val total=packet[3].toInt() and 255;val index=packet[4].toInt() and 255
        val length=packet[5].toInt() and 255
        if(total !in 1..4 || index>=total || packet.size!=6+length) return null
        if(nowMs-started>3000 || parts.size!=total || index==0) {parts=arrayOfNulls(total);started=nowMs}
        val seed=packet[2].toInt() and 255
        parts[index]=packet.copyOfRange(6,packet.size).map {if(seed==0) it else (it.toInt() xor seed xor 0x3f).toByte()}.toByteArray()
        if(parts.any {it==null}) return null
        val result=parts.fold(byteArrayOf(0x53,2,0,1,0,0)) {acc,p->acc+p!!}
        parts=emptyArray();return result
    }
}

object EdgeColors {
    /** Small RGB bitmap only. Encoded letterboxing is excluded when the entire border is black. */
    fun sample(pixels:IntArray,width:Int,height:Int,vivid:Boolean=false):IntArray {
        require(width>=16 && height>=16 && pixels.size>=width*height)
        fun blackRow(y:Int)= (0 until width step 2).all {x->val c=pixels[y*width+x];(c shr 16 and 255)<8&&(c shr 8 and 255)<8&&(c and 255)<8}
        fun blackCol(x:Int)= (0 until height step 2).all {y->val c=pixels[y*width+x];(c shr 16 and 255)<8&&(c shr 8 and 255)<8&&(c and 255)<8}
        var top=0;var bottom=height;var left=0;var right=width
        while(top<height/5 && blackRow(top)) top++
        while(bottom>height-height/5 && blackRow(bottom-1)) bottom--
        while(left<width/5 && blackCol(left)) left++
        while(right>width-width/5 && blackCol(right-1)) right--
        // Exactly the outer 5% of the visible picture; the center cannot dilute the edges.
        val dx=((right-left)/20).coerceAtLeast(1);val dy=((bottom-top)/20).coerceAtLeast(1)
        fun mean(x0:Int,y0:Int,x1:Int,y1:Int):Int {
            var r=0.0;var g=0.0;var b=0.0;var n=0.0
            for(y in y0 until y1) for(x in x0 until x1) {
                val c=pixels[y*width+x];val cr=c shr 16 and 255;val cg=c shr 8 and 255;val cb=c and 255
                val high=maxOf(cr,cg,cb);val low=minOf(cr,cg,cb)
                val saturation=if(high==0)0.0 else (high-low).toDouble()/high
                // Prefer the colored objects over neutral highlights without selecting a single noisy pixel.
                val weight=if(vivid)1.0+3.0*saturation*saturation else 1.0
                r+=cr*weight;g+=cg*weight;b+=cb*weight;n+=weight
            }
            val rgb=intArrayOf((r/n).toInt(),(g/n).toInt(),(b/n).toInt())
            if(vivid) {
                val high=rgb.max();val low=rgb.min();val delta=high-low
                // Neutral/black content stays neutral/black. Preserve hue while increasing chroma.
                if(delta>high*0.02) {
                    val value=(high*1.20).coerceAtMost(255.0)
                    val saturation=(delta.toDouble()/high*1.60).coerceAtMost(1.0)
                    for(i in rgb.indices)rgb[i]=(value*(1.0-saturation*(high-rgb[i])/delta)).toInt().coerceIn(0,255)
                }
            }
            return (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
        }
        val edges=intArrayOf(mean(left,top,right,top+dy),mean(right-dx,top,right,bottom),
            mean(left,bottom-dy,right,bottom),mean(left,top,left+dx,bottom))
        // A dark edge expands into its own triangular sector, never into an unrelated opposite edge.
        // Ignore near-black pixels here: their varying area must not modulate the strip's brightness.
        fun value(c:Int)=maxOf(c shr 16 and 255,c shr 8 and 255,c and 255)
        if((0 until width*height).none {value(pixels[it])>=12})return edges
        for(side in 0..3) if(value(edges[side])<12) {
            for(depth in listOf(.15f,.30f,.50f,1f)) {
                var r=0L;var g=0L;var b=0L;var count=0
                for(y in top until bottom) for(x in left until right) {
                    val nx=(x-left+.5f)/(right-left);val ny=(y-top+.5f)/(bottom-top)
                    val distance=when(side) {0->ny;1->1-nx;2->1-ny;else->nx}
                    if(distance>depth || distance>minOf(ny,1-nx,1-ny,nx)+.001f)continue
                    val c=pixels[y*width+x]
                    if(value(c)<12)continue
                    r+=c shr 16 and 255;g+=c shr 8 and 255;b+=c and 255;count++
                }
                if(count>=maxOf(4,(right-left)*(bottom-top)/500)) {
                    edges[side]=((r/count).toInt() shl 16) or ((g/count).toInt() shl 8) or (b/count).toInt()
                    break
                }
            }
        }
        return edges
    }
}

/** Brightness is the user's hardware level, not the movie's exposure. Black holds the last hue.
 * Three-frame median rejects isolated flashes; time-based interpolation cannot oscillate by itself.
 */
internal class StableLightColors(fallback:Int) {
    private var current=IntArray(4) {normalize(fallback).takeIf {it!=0} ?: 0xffffff}
    private val history=java.util.ArrayDeque<IntArray>()
    private var lastAt=0L
    fun next(colors:IntArray,nowMs:Long):IntArray {
        require(colors.size==4)
        history.addLast(colors.map(::normalize).toIntArray())
        if(history.size>3)history.removeFirst()
        val alpha=(1-kotlin.math.exp(-(if(lastAt==0L)100L else (nowMs-lastAt).coerceIn(1,250)).toDouble()/450)).toFloat()
        lastAt=nowMs
        if(history.size<3)return current.clone()
        val frames=history.toList()
        current=IntArray(4) {side->
            var target=0
            for(shift in listOf(16,8,0)) {
                val channels=frames.map {it[side] shr shift and 255}.sorted()
                target=target or (channels[1] shl shift)
            }
            target=normalize(target)
            if(target==0)current[side] else {
                var mixed=0
                for(shift in listOf(16,8,0)) {
                    val before=current[side] shr shift and 255;val after=target shr shift and 255
                    val step=if(kotlin.math.abs(after-before)<=3)after else
                        (before+(after-before)*alpha).toInt().coerceIn(0,255)
                    mixed=mixed or (step shl shift)
                }
                normalize(mixed)
            }
        }
        return current.clone()
    }
    private fun normalize(c:Int):Int {
        val high=maxOf(c shr 16 and 255,c shr 8 and 255,c and 255)
        if(high<12)return 0
        return ((c shr 16 and 255)*255/high shl 16) or ((c shr 8 and 255)*255/high shl 8) or ((c and 255)*255/high)
    }
}

/** A finite reconnect budget per outage; explicit disconnect cancels scheduled attempts. */
internal class LightReconnect {
    private var attempts=0
    var cancelled=false
        private set
    fun reset() {attempts=0;cancelled=false}
    fun cancel() {cancelled=true}
    fun nextDelay():Long? = if(cancelled || attempts>=10)null else
        (1500L shl (attempts++).coerceAtMost(4)).coerceAtMost(30_000L)
}

/** One GPU read supplies all strip sections; the smaller grid is a performance fallback. */
internal object VideoSampleGrid {
    const val WIDTH=32
    const val HEIGHT=18
    const val FINE_WIDTH=160
    const val FINE_HEIGHT=90
}

/** Probe cheaply, retain detail when affordable, and recover without alternating every frame. */
internal class VideoReadbackQuality {
    var fine=false; private set
    var suspended=false; private set
    private var probing=true
    private var fineBlocked=false
    private var retryAt=0L
    private var fastSamples=0
    fun reset() {
        fine=false;suspended=false;probing=true;fineBlocked=false;retryAt=0L;fastSamples=0
    }
    fun complete(costMs:Long,nowMs:Long) {
        if(suspended)return
        if(VideoReadbackPacing.blocksPlayback(costMs)) {
            // A costly detailed copy may still have an affordable coarse alternative.
            // Never repeat that detailed stall during this playback.
            if(fine) {fine=false;fineBlocked=true} else suspended=true
            probing=false;fastSamples=0
        } else if(fine) {
            if(costMs>60) {fine=false;retryAt=nowMs+30_000;fastSamples=0}
        } else if(probing) {
            probing=false;fine=costMs<=25;retryAt=nowMs+30_000
        } else {
            fastSamples=if(costMs<=16)(fastSamples+1).coerceAtMost(6) else 0
            if(!fineBlocked && fastSamples>=6 && nowMs>=retryAt) {
                fine=true;fastSamples=0
            }
        }
    }
}

internal object VideoReadbackPacing {
    // Surface readback can occupy Android's RenderThread even when requested on our worker.
    // Spend at most 10% of wall time copying; the configured FPS is an upper limit.
    fun cooldown(costMs:Long,fps:Int):Long {
        val cost=costMs.coerceIn(0,10_000)
        return maxOf((1000L/fps.coerceAtLeast(1)-cost).coerceAtLeast(0),cost*9)
    }
    // A quarter-second copy already stalls several video/UI frames. Repeating it cannot
    // produce useful ambient animation; hold the last colour until a new playback or retry.
    fun blocksPlayback(costMs:Long)=costMs>=250
}
