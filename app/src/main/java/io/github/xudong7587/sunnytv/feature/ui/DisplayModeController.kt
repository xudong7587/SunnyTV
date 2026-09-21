package io.github.xudong7587.sunnytv.feature.ui

import android.view.Window
import io.github.xudong7587.sunnytv.core.model.DisplayModeCandidate
import io.github.xudong7587.sunnytv.core.model.DisplayModePolicy

/**
 * Requests a mode for this app window only. Android/TV firmware may reject or substitute it.
 * AUTO deliberately clears the request so selecting it can undo a previous 1080p/native choice.
 */
fun applyPreferredDisplayMode(window:Window,preference:String) {
    val display=window.decorView.display ?: return
    val attrs=window.attributes
    val selected=DisplayModePolicy.select(preference,display.supportedModes.map {
        DisplayModeCandidate(it.modeId,it.physicalWidth,it.physicalHeight,it.refreshRate)
    })
    if(selected==null) {
        attrs.preferredDisplayModeId=0
        attrs.preferredRefreshRate=0f
    } else {
        attrs.preferredDisplayModeId=selected.id
        attrs.preferredRefreshRate=selected.refreshRate
    }
    window.attributes=attrs
}
