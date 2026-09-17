package io.github.xudong7587.sunnytv.feature.ui

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

private fun Context.activity():Activity?=when(this) {is Activity->this;is ContextWrapper->baseContext.activity();else->null}

@Composable fun DisplayDiagnostics() {
    val view=LocalView.current
    val context=LocalContext.current
    val window=context.activity()?.window
    val samples=remember {ArrayDeque<Long>()}
    var dropped by remember {mutableIntStateOf(0)}
    DisposableEffect(window) {
        val listener=Window.OnFrameMetricsAvailableListener {_,metrics,dropCount->
            val ns=metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            if(ns>0) {if(samples.size==240) samples.removeFirst();samples.addLast(ns)}
            dropped+=dropCount
        }
        window?.addOnFrameMetricsAvailableListener(listener,Handler(Looper.getMainLooper()))
        onDispose {window?.removeOnFrameMetricsAvailableListener(listener)}
    }
    var info by remember {mutableStateOf("正在读取当前应用显示能力…")}
    LaunchedEffect(view) {
        while(true) {
            val display=view.display
            val mode=display?.mode
            val density=view.resources.displayMetrics.density
            val ordered=samples.sorted()
            val p95=if(ordered.isEmpty()) null else ordered[((ordered.size-1)*.95).toInt()]/1_000_000.0
            val memory=ActivityManager.MemoryInfo().also {(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)}
            info=buildString {
                append("当前显示：${mode?.physicalWidth ?: 0} × ${mode?.physicalHeight ?: 0} · ${display?.refreshRate ?: 0} Hz\n")
                append("应用窗口：${view.width} × ${view.height} px · ${(view.width/density).toInt()} × ${(view.height/density).toInt()} dp\n")
                append("支持模式："+display?.supportedModes?.joinToString {"${it.physicalWidth}×${it.physicalHeight} @ ${it.refreshRate}"}+"\n")
                append("系统可用内存：${memory.availMem/1024/1024} MB\n")
                append("当前诊断会话渲染帧 P95：${p95?.let {"%.2f ms".format(it)} ?: "—"} · ${ordered.size} 样本 · 回调丢失 $dropped\n")
                append("显示模式与渲染帧耗时不等于视频帧率；静止界面不会持续产出新帧。")
            }
            delay(1500)
        }
    }
    Text(info,color=SunnyColors.Secondary,fontSize=13.sp,lineHeight=22.sp)
}
