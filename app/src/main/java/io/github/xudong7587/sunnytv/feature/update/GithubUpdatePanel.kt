package io.github.xudong7587.sunnytv.feature.update

import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.BuildConfig
import io.github.xudong7587.sunnytv.feature.ui.*
import kotlinx.coroutines.*
import java.io.File

@Composable internal fun GithubUpdatePanel(
    latest:suspend ()->GithubUpdates.Release={GithubUpdates.latest()},
    downloadOverride:(suspend (GithubUpdates.Release,(Int?)->Unit)->File)?=null,
    installOverride:((File)->Unit)?=null
) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val scope=rememberCoroutineScope()
    var release by remember {mutableStateOf<GithubUpdates.Release?>(null)}
    var status by remember {mutableStateOf("正在检查最新版本…")}
    var busy by remember {mutableStateOf(false)}
    var ready by remember {mutableStateOf<File?>(null)}
    var waitingPermission by remember {mutableStateOf(false)}
    fun install(file:File) {
        if(installOverride!=null) {installOverride(file);return}
        waitingPermission=Build.VERSION.SDK_INT>=26 && !context.packageManager.canRequestPackageInstalls()
        ExperimentalUpdater(context).install(file)
        status=if(waitingPermission) "请允许安装应用，返回后将自动打开安装确认" else "已打开系统安装确认"
    }
    suspend fun check() {
        busy=true
        try {
            val found=latest();release=found
            status=if(found.versionCode>BuildConfig.VERSION_CODE) "已发现最新版 ${found.versionName}" else "当前已是最新版本"
        } catch(e:CancellationException) {throw e}
        catch(_:Exception) {status="检查失败，点击版本按钮重试"}
        finally {busy=false}
    }
    LaunchedEffect(Unit) {check()}
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,event->
            if(event==Lifecycle.Event.ON_RESUME && waitingPermission &&
                (Build.VERSION.SDK_INT<26 || context.packageManager.canRequestPackageInstalls())) {
                waitingPermission=false
                ready?.let {runCatching {install(it)}.onFailure {status="无法打开安装器，请点击下载并更新重试"}}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        FocusTile("update:github",Modifier.fillMaxWidth(),onClick={if(!busy) scope.launch {check()}}) {
            Text("当前版本 ${BuildConfig.VERSION_NAME} · $status",color=SunnyColors.Text,fontSize=15.sp,modifier=Modifier.padding(16.dp))
        }
        FocusTile("update:github-download",Modifier.fillMaxWidth(),onClick={
            if(!busy) {
                val selected=release
                if(ready!=null) runCatching {install(ready!!)}.onFailure {status="无法打开系统安装器"}
                else if(selected!=null && selected.versionCode>BuildConfig.VERSION_CODE) scope.launch {
                    busy=true;status="正在下载…"
                    try {
                        val progress:(Int?)->Unit={percent->status=if(percent==null) "正在下载…" else if(percent==100) "下载完成，正在校验…" else "正在下载 $percent%"}
                        val file=downloadOverride?.invoke(selected,progress) ?: GithubUpdates.download(context,selected.apk,progress)
                        ready=file;install(file)
                    } catch(e:CancellationException) {throw e}
                    catch(_:Exception) {status="下载或安装失败，点击下载并更新重试"}
                    finally {busy=false}
                }
            }
        }) {
            Text("下载并更新",color=if(busy || (ready==null && (release?.versionCode ?: 0)<=BuildConfig.VERSION_CODE)) SunnyColors.Secondary else SunnyColors.Text,
                fontSize=15.sp,modifier=Modifier.padding(16.dp))
        }
    }
}
