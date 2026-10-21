package io.github.xudong7587.sunnytv.feature.ui

import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.toColorInt
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.github.xudong7587.sunnytv.core.color.MonetColors
import io.github.xudong7587.sunnytv.core.color.mcu.dynamiccolor.ColorSpec.SpecVersion
import io.github.xudong7587.sunnytv.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Immutable data class SystemMonet(val seed:Long=MonetPolicy.FALLBACK_SEED,val available:Boolean=false)
val LocalSystemMonet=staticCompositionLocalOf {SystemMonet()}
private const val OVERLAY_SETTING="theme_customization_overlay_packages"

/** Reads public system color roles/settings only. No wallpaper bitmap, storage permission or writes. */
internal fun readSystemMonet(context:Context,dark:Boolean,sdk:Int=Build.VERSION.SDK_INT):SystemMonet {
    if(sdk<31 || Build.VERSION.SDK_INT<31) return SystemMonet()
    if(sdk>=33) {
        val overlay=runCatching {Settings.Secure.getString(context.contentResolver,OVERLAY_SETTING)}.getOrNull()
        if(!overlay.isNullOrBlank()) runCatching {
            val json=JSONObject(overlay)
            val raw=json.optString("android.theme.customization.system_palette","")
            val seed=if(raw.isBlank()) context.getColor(android.R.color.system_accent1_500).toLong()
                else (if(raw.startsWith("#")) raw else "#$raw").toColorInt().toLong()
            val style=MonetPolicy.style(json.optString("android.theme.customization.theme_style","TONAL_SPOT").lowercase())
            val spec=if(sdk>=36) SpecVersion.SPEC_2025 else SpecVersion.SPEC_2021
            // The reference takes the platform primary as the key for the selected Monet style.
            val primary=MonetColors.scheme(seed,dark,style,spec).primary.toLong() and 0xFFFFFFFFL
            return SystemMonet(primary,true)
        }
    }
    return runCatching {
        val resource=if(dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600
        SystemMonet(context.getColor(resource).toLong() and 0xFFFFFFFFL,true)
    }.getOrDefault(SystemMonet())
}

@Composable internal fun rememberSystemMonet(enabled:Boolean,dark:Boolean):SystemMonet {
    if(!enabled) return SystemMonet()
    val context=LocalContext.current
    val configuration=LocalConfiguration.current
    val owner=LocalActivity.current as? LifecycleOwner
    var refresh by remember {mutableIntStateOf(0)}
    DisposableEffect(context,owner) {
        val lifecycleObserver=object:DefaultLifecycleObserver {
            override fun onResume(owner:LifecycleOwner) {refresh++}
        }
        owner?.lifecycle?.addObserver(lifecycleObserver)
        val colorObserver=object:ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange:Boolean) {refresh++}
        }
        val registered=runCatching {
            context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(OVERLAY_SETTING),false,colorObserver)
        }.isSuccess
        onDispose {
            owner?.lifecycle?.removeObserver(lifecycleObserver)
            if(registered) context.contentResolver.unregisterContentObserver(colorObserver)
        }
    }
    val colors by produceState(SystemMonet(),context,configuration,dark,refresh) {
        value=withContext(Dispatchers.Default) {readSystemMonet(context,dark)}
    }
    return colors
}
