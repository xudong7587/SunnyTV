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
    val brightness:Int=255,val fps:Int=10,val layout:LightLayout=LightLayout(),val deviceAddress:String="")

object Sp658eProtocol {
    val query=byteArrayOf(0x53,2,0x5b,1,0,1,0xdd.toByte())
    fun frame(command:Int,payload:ByteArray,seed:Int=0x34):ByteArray {
        require(payload.size<=255)
        return byteArrayOf(0x53,command.toByte(),seed.toByte(),1,0,payload.size.toByte())+
            payload.map {(it.toInt() xor seed xor 0x3f).toByte()}.toByteArray()
    }
    fun fixed(rgb:Int,brightness:Int)=listOf(frame(0x53,byteArrayOf(1,1)),
        frame(0x52,byteArrayOf((rgb shr 16).toByte(),(rgb shr 8).toByte(),rgb.toByte(),brightness.coerceIn(1,255).toByte())))

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

object VideoCrop {
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
    fun sample(pixels:IntArray,width:Int,height:Int):IntArray {
        require(width>=16 && height>=16 && pixels.size>=width*height)
        fun blackRow(y:Int)= (0 until width step 2).all {x->val c=pixels[y*width+x];(c shr 16 and 255)<8&&(c shr 8 and 255)<8&&(c and 255)<8}
        fun blackCol(x:Int)= (0 until height step 2).all {y->val c=pixels[y*width+x];(c shr 16 and 255)<8&&(c shr 8 and 255)<8&&(c and 255)<8}
        var top=0;var bottom=height;var left=0;var right=width
        while(top<height/5 && blackRow(top)) top++
        while(bottom>height-height/5 && blackRow(bottom-1)) bottom--
        while(left<width/5 && blackCol(left)) left++
        while(right>width-width/5 && blackCol(right-1)) right--
        val dx=((right-left)/12).coerceAtLeast(1);val dy=((bottom-top)/12).coerceAtLeast(1)
        fun mean(x0:Int,y0:Int,x1:Int,y1:Int):Int {
            var r=0L;var g=0L;var b=0L;var n=0
            for(y in y0 until y1) for(x in x0 until x1) {val c=pixels[y*width+x];r+=c shr 16 and 255;g+=c shr 8 and 255;b+=c and 255;n++}
            return ((r/n).toInt() shl 16) or ((g/n).toInt() shl 8) or (b/n).toInt()
        }
        return intArrayOf(mean(left,top,right,top+dy),mean(right-dx,top,right,bottom),
            mean(left,bottom-dy,right,bottom),mean(left,top,left+dx,bottom))
    }
}
