package io.github.xudong7587.sunnytv.feature.ui

import android.os.Build
import android.os.SystemClock
import io.github.xudong7587.sunnytv.BuildConfig
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import io.github.xudong7587.sunnytv.core.network.HttpPolicy
import io.github.xudong7587.sunnytv.feature.player.PlayerActivity
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.core.model.*
import kotlinx.coroutines.*

@Composable fun Field(label:String,value:String,onChange:(String)->Unit,modifier:Modifier=Modifier,password:Boolean=false,autoFocus:Boolean=false) {
    var focused by remember {mutableStateOf(false)}
    val focusRequester=remember {FocusRequester()}
    LaunchedEffect(Unit) {if(autoFocus) {delay(100);runCatching {focusRequester.requestFocus()}}}
    Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label,color=SunnyColors.Secondary,fontSize=12.sp)
        BasicTextField(value=value,onValueChange=onChange,singleLine=true,
            keyboardOptions=KeyboardOptions(keyboardType=if(password) KeyboardType.Password else KeyboardType.Text),
            visualTransformation=if(password) PasswordVisualTransformation() else VisualTransformation.None,
            cursorBrush=SolidColor(SunnyColors.Accent),textStyle=TextStyle(color=SunnyColors.Text,fontSize=15.sp),
            modifier=Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged {focused=it.isFocused}
                .background(SunnyColors.Background,RoundedCornerShape(9.dp))
                .border(if(focused) 2.dp else 1.dp,if(focused) SunnyColors.Accent else SunnyColors.Border,RoundedCornerShape(9.dp))
                .padding(14.dp))
    }
}

@Composable fun MessageDialog(text:String,onClose:()->Unit) {
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.widthIn(max=620.dp).background(SunnyColors.Surface,RoundedCornerShape(18.dp)).padding(28.dp),
            verticalArrangement=Arrangement.spacedBy(22.dp)) {
            Text("SunnyTV",color=SunnyColors.Accent,fontSize=21.sp,fontWeight=FontWeight.Bold)
            Text(text,color=SunnyColors.Text,fontSize=15.sp,lineHeight=24.sp)
            Action("知道了",id="dialog:ack",primary=true,autoFocus=true,onClick=onClose)
        }
    }
}

@Composable fun SettingsScreen() {
    val model=LocalAppModel.current; val coroutine=rememberCoroutineScope()
    val compact=LocalCompact.current
    val context=LocalContext.current
    var directUrl by remember {mutableStateOf("")}
    var resetConfirm by remember {mutableStateOf(false)}
    var category by rememberSaveable {mutableStateOf("媒体来源")}
    var adding by remember {mutableStateOf<SourceKind?>(null)}
    var removing by remember {mutableStateOf<SourceConfig?>(null)}
    var chooser by remember {mutableStateOf("")}
    SettingsLayout(navigation={SettingsCategories(category) {category=it}}) {
        LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(13.dp),contentPadding=PaddingValues(bottom=30.dp)) {
            item {SectionTitle(category)}
            when(category) {
                "媒体来源" -> {
                    item {Text("来源独立保存 · 凭据本地加密 · 不修改 NAS 媒体文件",color=SunnyColors.Secondary,fontSize=13.sp)}
                    model.sources.filter {it.kind==SourceKind.EMBY}.forEach {source -> item {
                        Row(Modifier.fillMaxWidth().background(SunnyColors.Surface,RoundedCornerShape(12.dp)).padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {Text(source.name,color=SunnyColors.Text,fontSize=18.sp); Text(if(source.kind==SourceKind.EMBY) "Emby · ${source.username}" else "CloudDrive2 · WebDAV",color=SunnyColors.Secondary,fontSize=12.sp)}
                            Action("移除此来源",id="remove:${source.id}") {removing=source}
                        }
                    } }
                    item {Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {Action("＋ Emby",primary=true) {adding=SourceKind.EMBY}}}
                    item {Action("重置损坏的来源配置") {resetConfirm=true}}
                }
                "首页与外观" -> {
                    item {ToggleRow("深色主题","浅色与重点色独立保存，即时应用",model.settings.darkTheme) {model.saveSettings(model.settings.copy(darkTheme=!model.settings.darkTheme))}}
                    item {SectionTitle("重点色", "十组配色 · 当前：${Presentation.accents[model.settings.accentIndex.coerceIn(0,9)].first}")}
                    val columns=if(compact) 3 else 5
                    Presentation.accents.chunked(columns).forEachIndexed { row, pairs -> item {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            pairs.forEachIndexed { col, pair ->
                                val index=row*columns+col
                                FocusTile("accent:$index",Modifier.weight(1f),active=model.settings.accentIndex==index,
                                    onClick={model.saveSettings(model.settings.copy(accentIndex=index))}) {
                                    Column(Modifier.padding(9.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                        Row { Box(Modifier.weight(1f).height(22.dp).background(androidx.compose.ui.graphics.Color(pair.second)))
                                            Box(Modifier.weight(1f).height(22.dp).background(androidx.compose.ui.graphics.Color(pair.third))) }
                                        Text((if(model.settings.accentIndex==index) "✓ " else "")+pair.first,color=SunnyColors.Text,fontSize=11.sp)
                                    }
                                }
                            }
                        }
                    } }
                    item {Action("展现方式：${model.settings.artworkMode}") {chooser="artwork"}}
                    item {Action("首页轮播：${when(model.settings.heroMode) {"resume"->"继续观看";"latest"->"最新入库";else->"随机推荐"}}") {chooser="hero"}}
                    item {Action("自动切换：${model.settings.heroIntervalSeconds} 秒") {chooser="interval"}}
                    item {ToggleRow("使用全部媒体库","关闭后，勾选参与随机轮播的媒体库",model.settings.heroAllLibraries) {
                        model.saveSettings(model.settings.copy(heroAllLibraries=!model.settings.heroAllLibraries))
                    }}
                    item {
                        androidx.compose.animation.AnimatedVisibility(!model.settings.heroAllLibraries) {
                            Column(Modifier.fillMaxWidth().padding(start=18.dp).background(SunnyColors.Surface,RoundedCornerShape(16.dp))
                                .border(1.dp,SunnyColors.Border,RoundedCornerShape(16.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text("参与轮播的媒体库",color=SunnyColors.Accent,fontSize=14.sp)
                                val libraries=model.feeds.values.flatMap {it.libraries}
                                if(libraries.isEmpty()) Text("连接 Emby 后可选择媒体库",color=SunnyColors.Secondary,fontSize=12.sp)
                                libraries.forEach {library->
                                    val chosen=library.key in model.settings.heroLibraryKeys
                                    ToggleRow(library.title,model.sources.firstOrNull {it.id==library.sourceId}?.name.orEmpty(),chosen) {
                                        model.saveSettings(model.settings.copy(heroLibraryKeys=if(chosen) model.settings.heroLibraryKeys-library.key else model.settings.heroLibraryKeys+library.key))
                                    }
                                }
                                if(model.settings.heroLibraryKeys.isEmpty()) Text("请选择至少一个媒体库",color=SunnyColors.Secondary,fontSize=12.sp)
                            }
                        }
                    }
                    item {ToggleRow("沉浸背景","优先使用 Emby 已刮削的 Backdrop",model.settings.backdropEnabled) {model.saveSettings(model.settings.copy(backdropEnabled=!model.settings.backdropEnabled))}}
                    item {ToggleRow("高清图片","提高请求图片尺寸；不修改电视的系统分辨率",model.settings.highQualityArtwork) {model.saveSettings(model.settings.copy(highQualityArtwork=!model.settings.highQualityArtwork))}}
                    item {ToggleRow("减少动画","关闭焦点缩放，保留即时描边",model.settings.reduceMotion) {model.saveSettings(model.settings.copy(reduceMotion=!model.settings.reduceMotion))}}
                    item {ToggleRow("继续观看","显示服务端的续播记录",model.settings.showResume) {model.saveSettings(model.settings.copy(showResume=!model.settings.showResume))}}
                    item {ToggleRow("接着看下一集","显示 Emby NextUp 推荐",model.settings.showNextUp) {model.saveSettings(model.settings.copy(showNextUp=!model.settings.showNextUp))}}
                    item {Action("清理海报缓存") {coroutine.launch {withContext(Dispatchers.IO) {model.app.clearArtwork()}; model.message="海报缓存已清理"}}}
                }
                "播放" -> {
                    item {Action("默认字幕：${Presentation.subtitles.firstOrNull {it.first==model.settings.subtitlePreference}?.second ?: "跟随媒体默认"}") {chooser="subtitle"}}
                    item {Field("直接播放测试 · HTTP 媒体或 MediaIndex /api/play 入口",directUrl,{directUrl=it})}
                    item {Action("打开媒体地址") {try {HttpPolicy.validate(directUrl.trim());context.startActivity(PlayerActivity.intent(context,PlaybackRequest("",directUrl.trim(),"手动媒体地址",requestedAtMs=SystemClock.elapsedRealtime(),sourceReadyAtMs=SystemClock.elapsedRealtime())))} catch(_: Exception) {model.message="地址无效，仅支持完整 HTTP / HTTPS 媒体地址"}}}
                    item {Text("Media3 · 原画直放优先\n首版不自动申请服务端转码；不能解码时明确提示。\n左右方向键快进/快退；音轨与字幕在播放面板中选择。",color=SunnyColors.Secondary,fontSize=14.sp,lineHeight=24.sp)}
                    item {Action("快进步长：${model.settings.seekStepSeconds} 秒") {model.saveSettings(model.settings.copy(seekStepSeconds=if(model.settings.seekStepSeconds==10) 30 else 10))}}
                    item {Text("HDR、杜比视界、音频直通、字幕延迟和自动跨季续播需要后续真机验证；未提供假开关。",color=SunnyColors.Secondary,fontSize=13.sp,lineHeight=22.sp)}
                }
                "设备与诊断" -> {
                    item {DisplayDiagnostics()}
                    item {ToggleRow("显示播放诊断","首帧耗时以真实渲染事件记录；不是 prepare() 耗时",model.settings.diagnostics) {model.saveSettings(model.settings.copy(diagnostics=!model.settings.diagnostics))}}
                    item {Text("设备：${Build.MANUFACTURER} ${Build.MODEL}\nAndroid：${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\nABI：${Build.SUPPORTED_ABIS.joinToString()}\n应用：${BuildConfig.VERSION_NAME}\n网络日志不包含 Token、Cookie 或完整媒体地址。",color=SunnyColors.Secondary,fontSize=14.sp,lineHeight=25.sp)}
                }
                "关于" -> {
                    item {Text("SunnyTV",color=SunnyColors.Accent,fontSize=38.sp,fontWeight=FontWeight.Bold)}
                    item {Text("让自己的媒体库，回到大屏。\n\n独立 Android TV 客户端。\nKotlin · Compose for TV · Media3\nEmby · MediaIndex STRM\n\n${BuildConfig.VERSION_NAME} 是开发测试版，不是已通过电视验收的正式版。\n原创实现；本轮没有复制 Moonfin 源码或 LumiPlayer 品牌资产。",color=SunnyColors.Secondary,fontSize=14.sp,lineHeight=24.sp)}
                }
            }
        }
    }
    if(chooser.isNotEmpty()) ChoiceDialog(when(chooser) {"artwork"->"展现方式";"hero"->"首页轮播";"interval"->"轮播间隔";else->"默认字幕"},
        when(chooser) {"artwork"->listOf("Poster" to "海报 · Poster","Thumb" to "背景 · Thumb","Banner" to "横幅 · Banner")
            "hero"->listOf("random" to "随机推荐","latest" to "最新入库推荐","resume" to "继续观看")
            "interval"->listOf(3,5,8,12,20,30,60).map {it.toString() to "$it 秒"}
            else->listOf("default" to "跟随媒体默认")+Presentation.subtitles},
        when(chooser) {"artwork"->model.settings.artworkMode;"hero"->model.settings.heroMode;"interval"->model.settings.heroIntervalSeconds.toString();else->model.settings.subtitlePreference},
        onDismiss={chooser=""}) {value ->
        model.saveSettings(when(chooser) {"artwork"->model.settings.copy(artworkMode=value)
            "hero"->model.settings.copy(heroMode=value);"interval"->model.settings.copy(heroIntervalSeconds=value.toInt());else->model.settings.copy(subtitlePreference=value)})
        chooser=""
    }
    if(resetConfirm) Dialog(onDismissRequest={resetConfirm=false}) {
        Column(Modifier.background(SunnyColors.Surface,RoundedCornerShape(16.dp)).padding(25.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text("清除全部本地来源配置？",color=SunnyColors.Text,fontSize=20.sp)
            Text("不会删除 NAS 文件，所有来源需要重新登录。",color=SunnyColors.Secondary,fontSize=14.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Action("取消",id="dialog:reset-cancel",autoFocus=true) {resetConfirm=false}
                Action("确认重置") {model.resetSources();resetConfirm=false}
            }
        }
    }
    adding?.let {kind->AddSourceDialog(kind,onClose={adding=null})}
    removing?.let {source ->
        Dialog(onDismissRequest={removing=null}) {
            Column(Modifier.background(SunnyColors.Surface,RoundedCornerShape(16.dp)).padding(25.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Text("移除 ${source.name}？",color=SunnyColors.Text,fontSize=21.sp)
                Text("只删除电视上的登录配置和海报缓存，不删除服务端媒体。",color=SunnyColors.Secondary,fontSize=14.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {Action("取消",id="dialog:remove-cancel",autoFocus=true) {removing=null}; Action("确认移除") {model.removeSource(source.id); removing=null}}
            }
        }
    }
}

@Composable private fun SettingsLayout(navigation:@Composable ()->Unit,content:@Composable ()->Unit) {
    val modifier=Modifier.fillMaxSize().padding(horizontal=pageSidePadding).padding(top=pageTopPadding)
    if(LocalCompact.current) Column(modifier,verticalArrangement=Arrangement.spacedBy(14.dp)) {
        navigation();Box(Modifier.weight(1f)) {content()}
    } else Row(modifier,horizontalArrangement=Arrangement.spacedBy(28.dp)) {
        Box(Modifier.width(165.dp)) {navigation()};Box(Modifier.weight(1f)) {content()}
    }
}

@Composable private fun SettingsCategories(category:String,onSelect:(String)->Unit) {
    val categories=listOf("媒体来源","首页与外观","播放","设备与诊断","关于")
    @Composable fun Category(cat:String) {
        FocusTile("settings:$cat",active=category==cat,onClick={onSelect(cat)}) {focused->
            Text(cat,color=if(focused || category==cat) SunnyColors.Accent else SunnyColors.Secondary,fontSize=14.sp,modifier=Modifier.padding(14.dp))
        }
    }
    if(LocalCompact.current) androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        items(categories.size) {Category(categories[it])}
    } else Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("设置",color=SunnyColors.Text,fontSize=29.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(bottom=18.dp))
        categories.forEach {Category(it)}
    }
}

@Composable private fun ToggleRow(title:String,description:String,value:Boolean,onClick:()->Unit) {
    FocusTile("toggle:$title",Modifier.fillMaxWidth(),onClick=onClick) {
        Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {Text(title,color=SunnyColors.Text,fontSize=16.sp); Text(description,color=SunnyColors.Secondary,fontSize=12.sp,modifier=Modifier.padding(top=5.dp))}
            Text(if(value) "开启  ●" else "关闭  ○",color=if(value) SunnyColors.Accent else SunnyColors.Secondary,fontSize=14.sp)
        }
    }
}

@Composable fun AddSourceDialog(kind:SourceKind,onClose:()->Unit,onConnected:()->Unit=onClose,firstConnection:Boolean=false) {
    val model=LocalAppModel.current
    var name by remember {mutableStateOf(if(kind==SourceKind.EMBY) "家庭 Emby" else "CloudDrive2")}
    var base by remember {mutableStateOf("")}; var user by remember {mutableStateOf("")}; var password by remember {mutableStateOf("")}
    Dialog(onDismissRequest={if(!model.busy && !firstConnection) onClose()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.padding(18.dp).widthIn(max=700.dp).fillMaxWidth().imePadding().background(SunnyColors.Surface,RoundedCornerShape(18.dp)).padding(26.dp)
            .verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(13.dp)) {
            Text(if(firstConnection) "连接你的媒体库" else if(kind==SourceKind.EMBY) "添加 Emby" else "添加 CloudDrive2",color=SunnyColors.Text,fontSize=26.sp,fontWeight=FontWeight.Bold)
            Text(if(kind==SourceKind.EMBY) "使用普通用户登录；保留反向代理路径前缀。" else "填写已开启的 WebDAV 服务地址，不是 CD2 管理页面。",color=SunnyColors.Secondary,fontSize=13.sp)
            Field("来源名称",name,{name=it},autoFocus=true)
            Field("服务地址（完整 http:// 或 https:// 地址）",base,{base=it})
            Row(horizontalArrangement=Arrangement.spacedBy(15.dp)) {
                Field("用户名",user,{user=it},Modifier.weight(1f))
                Field("密码",password,{password=it},Modifier.weight(1f),password=true)
            }
            Text("HTTP 仅适合可信网络；公网连接请使用有效 HTTPS 证书。",color=SunnyColors.Secondary,fontSize=12.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Action(if(model.busy) "连接中…" else "验证并保存",primary=true) {if(!model.busy) model.addSource(kind,name,base,user,password,onConnected)}
                if(!firstConnection) Action("取消") {if(!model.busy) onClose()}
            }
        }
    }
}
