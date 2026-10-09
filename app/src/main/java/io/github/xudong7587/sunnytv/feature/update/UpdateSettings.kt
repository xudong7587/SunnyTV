package io.github.xudong7587.sunnytv.feature.update

import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.feature.ui.*
import io.github.xudong7587.sunnytv.core.storage.ConfigStore
import kotlinx.coroutines.*
import java.io.File

@Composable private fun UpdateButton(label:String,id:String,modifier:Modifier=Modifier,onClick:()->Unit) {
    FocusTile(id,modifier,onClick=onClick) {
        Text(label,color=SunnyColors.Text,fontSize=15.sp,fontWeight=FontWeight.SemiBold,
            modifier=Modifier.padding(horizontal=18.dp,vertical=15.dp))
    }
}

@Composable fun UpdateSettings() {
    val context=LocalContext.current
    val keyboard=LocalSoftwareKeyboardController.current
    val store=remember {ConfigStore(context)}
    val saved=remember {store.updateConfig()}
    var url by remember {mutableStateOf(saved.first)}
    var user by remember {mutableStateOf(saved.second)}
    var password by remember {mutableStateOf(saved.third)}
    var automatic by remember {mutableStateOf(store.automaticUpdates())}
    var busy by remember {mutableStateOf(false)}
    var status by remember {mutableStateOf("")}
    var saveFeedback by remember {mutableStateOf("")}
    var confirmation by remember {mutableStateOf<String?>(null)}
    var ready by remember {mutableStateOf<File?>(null)}
    val scope=rememberCoroutineScope()
    var job by remember {mutableStateOf<Job?>(null)}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Field("WebDAV 升级目录（包含 SunnyTV-updata 的完整 HTTP/HTTPS 地址）",url,{url=it;saveFeedback=""})
        Field("WebDAV 用户名",user,{user=it;saveFeedback=""})
        Field("WebDAV 密码",password,{password=it;saveFeedback=""},password=true)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            UpdateButton("保存设置","update:save",Modifier.weight(1f)) {
                keyboard?.hide()
                try {
                    val address=io.github.xudong7587.sunnytv.core.network.HttpPolicy.serverBase(url)
                    store.saveUpdateConfig(address,user,password)
                    saveFeedback="设置已保存"
                    confirmation="设置已保存。下次打开 SunnyTV 会使用这份升级配置，密码已在本机加密保存。"
                } catch(_:Exception) {saveFeedback="保存失败";confirmation="保存失败，请检查 WebDAV 升级目录地址。"}
            }
            if(!busy) UpdateButton("检查更新","update:check",Modifier.weight(1f)) {
                keyboard?.hide()
                job=scope.launch {busy=true;ready=null;status="正在检查并下载更新…"
                    try {ready=ExperimentalUpdater(context).download();status=if(ready==null) "当前已是最新版本" else "更新已下载并校验完成，请安装更新"}
                    catch(e:CancellationException) {status="更新已取消";throw e}
                    catch(_:Exception) {status="升级失败：请检查已保存的 WebDAV 配置、目录权限及升级包"}
                    finally {busy=false}
                }
            } else UpdateButton("取消下载","update:cancel",Modifier.weight(1f)) {job?.cancel()}
        }
        if(saveFeedback.isNotBlank()) Text(saveFeedback,color=SunnyColors.Accent,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
        UpdateButton("GitHub 官方发行 · 下载升级","update:github",Modifier.fillMaxWidth()) {
            context.startActivity(android.content.Intent(context,GithubUpdateActivity::class.java))
        }
        FocusTile("update:auto",Modifier.fillMaxWidth().semantics {
            role=Role.Switch;toggleableState=if(automatic) ToggleableState.On else ToggleableState.Off
            stateDescription=if(automatic) "开启" else "关闭"
        },onClick={
            automatic=!automatic;store.setAutomaticUpdates(automatic)
        }) {
            Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("自动更新",color=SunnyColors.Text,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                    Text("每次启动 SunnyTV 自动检查、下载更新，并打开系统安装确认",color=SunnyColors.Secondary,fontSize=12.sp)
                }
                Text(if(automatic) "开启" else "关闭",color=SunnyColors.Secondary,fontSize=13.sp)
                Box(Modifier.size(width=44.dp,height=26.dp)
                    .background(if(automatic) SunnyColors.Accent else SunnyColors.Border,CircleShape)
                    .border(1.dp,SunnyColors.Secondary.copy(.25f),CircleShape).padding(4.dp)) {
                    Box(Modifier.align(if(automatic) Alignment.CenterEnd else Alignment.CenterStart).size(18.dp)
                        .background(if(automatic) SunnyColors.Background else SunnyColors.Text,CircleShape))
                }
            }
        }
        if(status.isNotBlank()) Text(status,color=SunnyColors.Accent,fontSize=14.sp,lineHeight=21.sp)
        ready?.let {file->UpdateButton("安装更新","update:install",Modifier.fillMaxWidth()) {
            try {ExperimentalUpdater(context).install(file);status="如刚开启安装权限，请返回后再次点安装更新"}
            catch(_:Exception) {status="设备无法打开安装器，请检查系统安装权限"}
        }}
        Text("首次需允许 SunnyTV 安装未知应用。检查更新使用已保存的设置；安装由系统确认。",color=SunnyColors.Secondary,fontSize=12.sp,lineHeight=19.sp)
    }
    confirmation?.let {message->MessageDialog(message) {confirmation=null}}
}
