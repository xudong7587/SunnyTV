package io.github.xudong7587.sunnytv.feature.player

import android.content.pm.ActivityInfo

/** Video dimensions from Media3 already account for rotation; pixel aspect ratio still applies. */
internal fun playerOrientation(tv:Boolean,width:Int=0,height:Int=0,pixelRatio:Float=1f):Int = when {
    tv -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    width>0 && height>0 && pixelRatio>0 && width*pixelRatio<height -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
}

/** Android rotation is measured from the display's natural orientation, which can be landscape on tablets. */
internal fun fixedPlayerOrientation(rotation:Int,naturalLandscape:Boolean):Int {
    val portrait=listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)
    val landscape=listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    return (if(naturalLandscape) landscape else portrait)[rotation.mod(4)]
}
