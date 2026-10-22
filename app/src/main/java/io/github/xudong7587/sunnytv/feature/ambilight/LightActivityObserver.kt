package io.github.xudong7587.sunnytv.feature.ambilight

import android.app.Activity
import android.app.Application
import android.os.Bundle

/** Covers Home/Back/task switching, rather than relying on a playback or settings page's disposal. */
class LightActivityObserver(private val lights:()->AmbilightController):Application.ActivityLifecycleCallbacks {
    override fun onActivityStarted(activity:Activity) {lights().activityStarted(activity)}
    override fun onActivityStopped(activity:Activity) {lights().activityStopped(activity)}
    override fun onActivityCreated(activity:Activity,state:Bundle?) {}
    override fun onActivityResumed(activity:Activity) {}
    override fun onActivityPaused(activity:Activity) {}
    override fun onActivitySaveInstanceState(activity:Activity,state:Bundle) {}
    override fun onActivityDestroyed(activity:Activity) {}
}
