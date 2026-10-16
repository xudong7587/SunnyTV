package io.github.xudong7587.sunnytv.feature.player

/** Bounded AAC-LC Program Config Element metadata reader; never guesses a layout. */
internal object AacPceConfig {
    data class Config(val bytes:ByteArray,val channels:Int)
    fun incomplete(config:ByteArray):Boolean = config.size==2 &&
        (config[0].toInt() and 255) shr 3==2 && (config[1].toInt() and 127)==0

    fun fromFrame(config:ByteArray,frame:ByteArray):Config? = runCatching {
        require(incomplete(config) && frame.size in 1..8192)
        var position=0
        fun read(count:Int):Int {
            require(count in 0..16 && position+count<=frame.size*8)
            var value=0
            repeat(count) {value=(value shl 1) or ((frame[position/8].toInt() shr (7-position%8)) and 1);position++}
            return value
        }
        require(read(3)==5) // ID_PCE, before any channel's spectral data.
        val output=mutableListOf<Int>()
        fun write(value:Int,count:Int) {for(i in count-1 downTo 0) output+=(value shr i) and 1}
        config.forEach {write(it.toInt() and 255,8)}
        fun copy(count:Int):Int = read(count).also {write(it,count)}
        copy(4) // instance tag
        require(copy(2)==1) // AAC-LC
        val frequency=copy(4)
        require(frequency==((config[0].toInt() and 7) shl 1 or ((config[1].toInt() and 128) shr 7)))
        val front=copy(4);val side=copy(4);val back=copy(4)
        val lfe=copy(2);val associated=copy(3);val coupled=copy(4)
        if(copy(1)!=0) copy(4)
        if(copy(1)!=0) copy(4)
        if(copy(1)!=0) copy(3)
        var channels=lfe
        repeat(front+side+back) {channels+=if(copy(1)!=0) 2 else 1;copy(4)}
        repeat(lfe+associated) {copy(4)}
        repeat(coupled) {copy(5)}
        require(channels in 1..8)
        position=(position+7)/8*8
        val commentLength=read(8)
        require(position+commentLength*8<=frame.size*8)
        // PCE byte alignment is relative to its container. Re-align for ASC,
        // rather than copying ADTS padding; encoder comments are not needed.
        while(output.size%8!=0) output+=0
        write(0,8)
        Config(ByteArray(output.size/8) {i->(0..7).fold(0) {v,j->(v shl 1) or output[i*8+j]}.toByte()},channels)
    }.getOrNull()
}
