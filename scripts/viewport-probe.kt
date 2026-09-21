import io.github.xudong7587.sunnytv.core.model.*
fun main() {
    var checks=0
    fun verify(ok:Boolean) {check(ok);checks++}
    for(top in 0..200 step 10) for(height in 40..240 step 20) for(offset in top..top+100 step 5)
        verify(ViewportPolicy.reveal(offset.toFloat(),height.toFloat(),top.toFloat(),600f,260f)==0f)
    for(overflow in 1..200) {
        verify(ViewportPolicy.reveal(400f+overflow,200f,120f,600f,220f)==220f)
        verify(ViewportPolicy.reveal(120f-overflow,200f,120f,600f,220f)==-220f)
    }
    verify(ViewportPolicy.reveal(Float.NaN,10f,0f,100f)==0f)
    verify(PerformancePolicy.cacheSize(-1)==512)
    println("Viewport policy: $checks checks passed")
}
