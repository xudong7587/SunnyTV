package io.github.xudong7587.sunnytv.feature.ambilight

/** Identity-based ownership survives overlapping Activity handoffs without treating them as exits. */
internal class LightPresence {
    private val activities=java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any,Boolean>())
    val foreground get()=activities.isNotEmpty()
    var player:Any?=null
        private set
    fun started(activity:Any) {activities.add(activity)}
    fun stopped(activity:Any) {activities.remove(activity)}
    fun play(owner:Any) {player=owner}
    fun stop(owner:Any):Boolean {
        if(player!==owner)return false
        player=null;return true
    }
    fun acceptsFrame(owner:Any,config:LightConfig,preview:Boolean)=
        foreground && player===owner && config.enabled && config.sync && !preview
    fun idleColor(config:LightConfig)=if(foreground)config.fixedColor else 0
}
