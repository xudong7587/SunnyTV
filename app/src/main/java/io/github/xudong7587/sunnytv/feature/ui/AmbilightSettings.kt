package io.github.xudong7587.sunnytv.feature.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.xudong7587.sunnytv.SunnyApp
import io.github.xudong7587.sunnytv.feature.ambilight.*

@Composable fun AmbilightSettings() {
    val lights=(LocalContext.current.applicationContext as SunnyApp).ambilight
    val config by lights.config.collectAsState()
    val status by lights.status.collectAsState()
    val connected by lights.connected.collectAsState()
    val metrics by lights.metrics.collectAsState()
    var calibration by remember {mutableStateOf(false)}
    var palette by remember {mutableStateOf(false)}
    var color by remember(config.fixedColor) {mutableStateOf("%06X".format(config.fixedColor))}
    var message by remember {mutableStateOf("")}
    var selected by remember {mutableIntStateOf(0)}
    val names=listOf("左上","右上","右下","左下")
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if(lights.hasPermissions())lights.connect() else message="需要蓝牙权限才能连接灯带；Android 11 还需允许定位并开启系统定位。"
    }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    fun connect() {
        val alreadyEnabled=config.enabled
        if(!alreadyEnabled)lights.save(config.copy(enabled=true))
        if(!lights.hasPermissions())permissions.launch(lights.permissions())
        else if(alreadyEnabled)lights.connect()
    }
    DisposableEffect(lights,lifecycle) {
        val observer=LifecycleEventObserver {_,event->
            if(event==Lifecycle.Event.ON_START)lights.enterSettings()
            if(event==Lifecycle.Event.ON_STOP) {palette=false;calibration=false;lights.leaveSettings()}
        }
        lifecycle.addObserver(observer)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))lights.enterSettings()
        onDispose {lifecycle.removeObserver(observer);lights.leaveSettings()}
    }
    Column(Modifier.fillMaxWidth().testTag("settings:ambilight"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("SP658E · 播放时同步视频四边，退出或暂停后显示固定色。",color=SunnyColors.Secondary,fontSize=14.sp)
        Text(status,color=SunnyColors.Accent,fontSize=17.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Action(if(connected) "重新连接" else "连接灯带",id="lights:connect",primary=true,alwaysShowLabel=true) {connect()}
            Action(if(config.enabled) "停用灯带控制" else "启用灯带控制",id="lights:enable",alwaysShowLabel=true) {
                if(config.enabled)lights.save(config.copy(enabled=false)) else connect()
            }
        }
        Action(if(config.sync) "画面同步：开启" else "画面同步：关闭",id="lights:sync",alwaysShowLabel=true) {
            lights.save(config.copy(sync=!config.sync))
        }
        Text("固定颜色",color=SunnyColors.Text,fontSize=20.sp)
        Action("打开色板",id="lights:palette",alwaysShowLabel=true) {palette=true}
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            listOf("暖橙" to 0xff4d05,"暖白" to 0xffbd78,"冷白" to 0xb8d4ff,"蓝色" to 0x2050ff).forEach {(name,rgb)->
                Action(name,id="lights:color:$name",active=config.fixedColor==rgb,alwaysShowLabel=true) {
                    lights.save(config.copy(fixedColor=rgb));lights.fixed()
                }
            }
        }
        Field("自定义颜色（六位 RRGGBB）",color,{color=it.filter {c->c.isDigit()||c.lowercaseChar() in 'a'..'f'}.take(6)},tag="lights:color-input")
        Action("应用固定颜色",id="lights:apply-color",alwaysShowLabel=true) {
            if(color.length==6) {lights.save(config.copy(fixedColor=color.toInt(16)));lights.fixed();message=""}
            else message="请输入六位颜色，例如 FF4D05。"
        }
        Text("灯带亮度：${(config.brightness*100/255)}%",color=SunnyColors.Text,fontSize=17.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Action("降低亮度",id="lights:dim",alwaysShowLabel=true) {lights.save(config.copy(brightness=(config.brightness-16).coerceAtLeast(1)))}
            Action("提高亮度",id="lights:bright",alwaysShowLabel=true) {lights.save(config.copy(brightness=(config.brightness+16).coerceAtMost(255)))}
            Action("刷新：${config.fps} 次/秒",id="lights:fps",alwaysShowLabel=true) {
                lights.save(config.copy(fps=when(config.fps){5->10;10->15;else->5}))
            }
        }
        Text("灯珠定位",color=SunnyColors.Text,fontSize=20.sp)
        Text("已带入此次保存的 ${config.layout.count} 个控制单元。每单元可能对应一小组灯珠。\n"+
            names.zip(config.layout.corners).joinToString(" · ") {"${it.first} ${it.second+1}"}+
            "\n上 / 右 / 下 / 左：${config.layout.lengths().joinToString(" / ")}",color=SunnyColors.Secondary,fontSize=14.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Action("设定末端与四角",id="lights:calibrate",alwaysShowLabel=true) {
                if(connected)calibration=true else message="请先连接灯带。"
            }
            Action("显示四边测试",id="lights:test-edges",alwaysShowLabel=true) {lights.testEdges()}
            Action("恢复固定色",id="lights:fixed",alwaysShowLabel=true) {lights.fixed()}
        }
        Text("四边测试：上红、右绿、下蓝、左黄。可选择一个角微调边界。",color=SunnyColors.Secondary,fontSize=13.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Action("微调：${names[selected]}",id="lights:corner",alwaysShowLabel=true) {selected=(selected+1)%4}
            fun adjust(delta:Int) {
                val c=config.layout.corners.toMutableList();c[selected]=Math.floorMod(c[selected]+delta,config.layout.count)
                val layout=LightLayout(config.layout.count,c[0],c[1],c[2],c[3])
                try {lights.save(config.copy(layout=layout));lights.testEdges(layout);message=""}
                catch(_:IllegalArgumentException) {message="该位置会导致四角交叉或超过七段，请反向微调。"}
            }
            Action("地址 −1",id="lights:corner-minus",alwaysShowLabel=true) {adjust(-1)}
            Action("地址 +1",id="lights:corner-plus",alwaysShowLabel=true) {adjust(1)}
        }
        Text(metrics,color=SunnyColors.Secondary,fontSize=13.sp)
        Text("蓝牙提交耗时是系统接受指令的时间，实际灯光延迟还需肉眼或摄像测量。",color=SunnyColors.Secondary,fontSize=12.sp)
        if(message.isNotBlank())Text(message,color=SunnyColors.Accent,fontSize=14.sp)
    }
    if(calibration) LightCalibration(lights,config.layout,onClose={calibration=false},onSave={layout->
        try {lights.save(config.copy(layout=layout));lights.fixed();calibration=false;message="定位已保存"}
        catch(_:IllegalArgumentException) {message="此布局超过七段限制，未保存"}
    })
    if(palette) LightPalette(config.fixedColor,onPreview=lights::previewColor,onClose={palette=false;lights.fixed()},onSave={rgb->
        lights.save(config.copy(fixedColor=rgb));palette=false;lights.fixed()
    })
}

@Composable internal fun LightPalette(initial:Int,onPreview:(Int)->Unit,onClose:()->Unit,onSave:(Int)->Unit) {
    val hsv=remember(initial) {FloatArray(3).also {android.graphics.Color.colorToHSV(initial or 0xff000000.toInt(),it)}}
    var hue by remember {mutableFloatStateOf(hsv[0])}
    var saturation by remember {mutableFloatStateOf(hsv[1])}
    val rgb=android.graphics.Color.HSVToColor(floatArrayOf(hue,saturation,1f)) and 0xffffff
    val requester=remember {FocusRequester()}
    LaunchedEffect(rgb) {onPreview(rgb)}
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.widthIn(max=740.dp).background(SunnyColors.Surface,RoundedCornerShape(18.dp)).padding(28.dp)
            .testTag("lights:palette-dialog").onPreviewKeyEvent {e->
                if(e.type!=KeyEventType.KeyDown)return@onPreviewKeyEvent false
                when(e.key) {
                    Key.DirectionLeft->{hue=(hue-3f+360f)%360f;true}
                    Key.DirectionRight->{hue=(hue+3f)%360f;true}
                    Key.DirectionUp->{saturation=(saturation-0.04f).coerceAtLeast(0f);true}
                    Key.DirectionDown->{saturation=(saturation+0.04f).coerceAtMost(1f);true}
                    Key.DirectionCenter,Key.Enter,Key.NumPadEnter->{onSave(rgb);true}
                    else->false
                }
            }.focusRequester(requester).focusable(),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text("固定色色板",color=SunnyColors.Text,fontSize=25.sp)
            Canvas(Modifier.fillMaxWidth().height(230.dp)) {
                drawRect(Brush.horizontalGradient(listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red)))
                drawRect(Brush.verticalGradient(listOf(Color.White,Color.Transparent)))
                val cursor=Offset(hue/360f*size.width,saturation*size.height)
                drawCircle(Color.Black,10.dp.toPx(),cursor,style=Stroke(4.dp.toPx()))
                drawCircle(Color.White,10.dp.toPx(),cursor,style=Stroke(2.dp.toPx()))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(32.dp).background(Color(rgb or 0xff000000.toInt()),RoundedCornerShape(6.dp)))
                Text("#%06X".format(rgb),color=SunnyColors.Accent,fontSize=22.sp)
            }
            Text("← / → 选择颜色 · ↑ / ↓ 调整饱和度\n确认键保存 · 返回键取消 · 灯带实时预览",color=SunnyColors.Secondary,fontSize=16.sp)
        }
        LaunchedEffect(Unit) {requester.requestFocus()}
    }
}

@Composable private fun LightCalibration(lights:AmbilightController,initial:LightLayout,onClose:()->Unit,onSave:(LightLayout)->Unit) {
    var step by remember {mutableIntStateOf(0)}
    var count by remember {mutableIntStateOf(initial.count)}
    var position by remember {mutableIntStateOf(initial.count-1)}
    var corners by remember {mutableStateOf(initial.corners)}
    var error by remember {mutableStateOf("")}
    val requester=remember {FocusRequester()}
    val labels=listOf("灯带末端","左上角","右上角","右下角","左下角")
    fun confirm() {
        if(step==0) {
            if(position<3) {error="至少需要四个控制单元";return}
            count=position+1;corners=corners.map {it.coerceAtMost(count-1)};step=1;position=corners[0]
        } else {
            val updated=corners.toMutableList().also {it[step-1]=position};corners=updated
            if(step<4) {step++;position=corners[step-1]}
            else {
                val layout=LightLayout(count,updated[0],updated[1],updated[2],updated[3])
                if(layout.direction()==null) {error="四角顺序有交叉或重复，请按返回逐角检查。";return}
                try {Sp658eProtocol.edges(layout,intArrayOf(1,2,3,4));onSave(layout)}
                catch(_:IllegalArgumentException) {error="布局超出七段限制，请缩短多余灯带或调整四角。"}
            }
        }
    }
    fun back() {if(step>0) {step--;position=if(step==0)count-1 else corners[step-1];error=""} else onClose()}
    LaunchedEffect(step,position) {lights.marker(600,position)}
    DisposableEffect(Unit) {onDispose {lights.fixed()}}
    Dialog(onDismissRequest={back()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.widthIn(max=760.dp).background(SunnyColors.Surface,RoundedCornerShape(18.dp)).padding(28.dp)
            .testTag("lights:calibration").onPreviewKeyEvent {event->
                if(event.type!=KeyEventType.KeyDown)return@onPreviewKeyEvent false
                val delta=when(event.key){Key.DirectionLeft->-1;Key.DirectionRight->1;Key.DirectionUp->10;Key.DirectionDown->-10;else->0}
                if(delta!=0) {position=(position+delta).coerceIn(0,if(step==0)599 else count-1);error="";true}
                else if(event.key==Key.DirectionCenter || event.key==Key.Enter || event.key==Key.NumPadEnter) {confirm();true}
                else false
            }.focusRequester(requester).focusable(),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text("${step+1} / 5 · ${labels[step]}",color=SunnyColors.Accent,fontSize=26.sp)
            Text("面向电视正面，将白色标记移到${labels[step]}。",color=SunnyColors.Text,fontSize=19.sp)
            Text("← / → 移动 1 个单元 · ↑ / ↓ 移动 10 个单元\n确认键保存本步 · 返回键回到上一步",color=SunnyColors.Secondary,fontSize=16.sp)
            Text("当前地址 ${position+1}"+(if(step==0) " · 请找最后一个实际发亮的位置" else " / $count"),color=SunnyColors.Text,fontSize=22.sp)
            if(error.isNotBlank())Text(error,color=SunnyColors.Accent,fontSize=15.sp)
            Text("遥控器按确认继续；全部完成后才覆盖原定位。",color=SunnyColors.Secondary,fontSize=13.sp)
        }
        LaunchedEffect(Unit) {requester.requestFocus()}
    }
}
