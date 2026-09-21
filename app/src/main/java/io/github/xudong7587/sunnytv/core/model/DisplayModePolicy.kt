package io.github.xudong7587.sunnytv.core.model

data class DisplayModeCandidate(
    val id:Int,
    val width:Int,
    val height:Int,
    val refreshRate:Float
) {
    val pixels:Long get()=width.toLong()*height.toLong()
}

/** Window-level display preference only. Never changes the device's global display setting. */
object DisplayModePolicy {
    const val AUTO="auto"
    const val FHD="1080p"
    const val NATIVE="native"
    val preferences=setOf(AUTO,FHD,NATIVE)

    fun normalize(value:String)=value.takeIf {it in preferences} ?: AUTO

    /** null means clear the app preference and let Android choose the window mode. */
    fun select(value:String,modes:List<DisplayModeCandidate>):DisplayModeCandidate?=when(normalize(value)) {
        FHD -> modes.filter {it.width==1920 && it.height==1080}.maxByOrNull {it.refreshRate}
        NATIVE -> modes.maxWithOrNull(compareBy<DisplayModeCandidate> {it.pixels}.thenBy {it.refreshRate})
        else -> null
    }
}
