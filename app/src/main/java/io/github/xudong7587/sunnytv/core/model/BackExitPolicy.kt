package io.github.xudong7587.sunnytv.core.model

/** Separate remote presses, measured on key-up; a held key cannot confirm exit. */
class BackExitPolicy {
    enum class Action { BACK, PROMPT, EXIT }
    private var lastPress:Long?=null
    private var count=0
    private var armedAt:Long?=null
    fun reset() {lastPress=null;count=0;armedAt=null}
    fun press(nowMs:Long,canGoBack:Boolean):Action {
        if(armedAt?.let {nowMs-it in 0..2_000}==true) {reset();return Action.EXIT}
        armedAt=null
        count=if(lastPress?.let {nowMs-it in 0..1_800}==true) count+1 else 1
        lastPress=nowMs
        return if(!canGoBack || count>=3) {armedAt=nowMs;Action.PROMPT} else Action.BACK
    }
}
