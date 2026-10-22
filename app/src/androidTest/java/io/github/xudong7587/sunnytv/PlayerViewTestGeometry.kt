package io.github.xudong7587.sunnytv

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Rect
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import io.github.xudong7587.sunnytv.feature.player.PlayerActivity

/** Inspect the real native video view for both TV and the phone's AndroidView host. */
internal fun actualPlayerView(activity:PlayerActivity):PlayerView {
    fun find(view:View):PlayerView? {
        if(view is PlayerView) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let {return it}
        return null
    }
    return checkNotNull(find(activity.window.decorView)) {"Native PlayerView missing"}
}
internal fun playerViewBounds(scenario:ActivityScenario<PlayerActivity>):Rect {
    var bounds=Rect.Zero
    scenario.onActivity {a->
        val v=actualPlayerView(a);val location=IntArray(2);v.getLocationInWindow(location)
        bounds=Rect(location[0].toFloat(),location[1].toFloat(),(location[0]+v.width).toFloat(),(location[1]+v.height).toFloat())
    }
    return bounds
}
