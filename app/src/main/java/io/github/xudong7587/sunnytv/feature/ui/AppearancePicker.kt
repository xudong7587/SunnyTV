package io.github.xudong7587.sunnytv.feature.ui

import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.core.model.*
import kotlin.math.*

@Composable fun MaterialPreviewCard(spec:AppearanceMaterial,modifier:Modifier=Modifier,onSelect:()->Unit) {
    val model=LocalAppModel.current
    val settings=model.settings
    val blocked=AppearancePolicy.blockedReason(spec.id,settings.highPerformanceThemes,model.app.lean(settings),Build.VERSION.SDK_INT,LocalView.current.isHardwareAccelerated)
    val selected=LocalNativeAppearance.current.material==spec.id
    // Neutral selector with a crisp schematic: miniature full-page blur hides material differences.
    val parent=LocalNativeAppearance.current
    CompositionLocalProvider(LocalNativeAppearance provides parent.copy(material="clear")) {
        FocusTile("material:${spec.id}",modifier.heightIn(min=56.dp).semantics {if(blocked!=null) disabled()},active=selected,
            onClick={if(blocked==null) onSelect()}) {
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=7.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                MaterialOptionSwatch(spec,settings,Modifier.size(42.dp).testTag("material-swatch:${spec.id}"))
                Text((if(selected) "✓ " else "")+spec.name,color=if(blocked==null) SunnyColors.Text else SunnyColors.Secondary,
                    fontSize=13.sp,fontWeight=FontWeight.Medium,maxLines=2,modifier=Modifier.weight(1f))
                if(blocked!=null) Text("·",color=SunnyColors.Secondary,fontSize=17.sp,
                    modifier=Modifier.semantics {contentDescription=blocked})
            }
        }
    }
}

@Composable fun MaterialSample(spec:AppearanceMaterial,settings:AppSettings,enabled:Boolean,modifier:Modifier=Modifier) {
    val systemSeed=LocalSystemMonet.current.seed
    val p=remember(spec.id,settings,systemSeed) {palette(settings.copy(appearanceMaterial=spec.id),systemSeed)}
    val a=NativeAppearance(spec.id,AppearancePolicy.cornerScale(settings.appearanceCorner),LocalNativeAppearance.current.shadows && (!spec.highCost || enabled))
    val source=rememberGlassBackdrop(spec.glass && enabled)
    CompositionLocalProvider(LocalSunnyPalette provides p,LocalNativeAppearance provides a,LocalGlassBackdrop provides source) {
        Box(modifier) {
            if(spec.glass) Box(Modifier.fillMaxSize().captureGlassBackdrop(source).clip(sunnyShape(10.dp)).background(p.background)) {
                Box(Modifier.fillMaxHeight().width(25.dp).background(p.accent))
                Box(Modifier.align(Alignment.BottomEnd).size(24.dp).background(p.accent3,CircleShape))
            }
            Box(Modifier.matchParentSize().padding(3.dp).sunnyPanel(10.dp)) {
                Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).size(13.dp,8.dp).background(p.accent,sunnyShape(3.dp)))
            }
        }
    }
}

@Composable fun CustomColorDialog(initial:Long,onDismiss:()->Unit,onApply:(Long)->Unit) {
    val model=LocalAppModel.current;val page=LocalPageKey.current
    val prior=remember {model.focusMemory[page]}
    DisposableEffect(Unit) {onDispose {if(prior!=null) model.focusMemory[page]=prior}}
    val original=remember(initial) {MonoColor.toHsv(initial)}
    var hue by remember {mutableFloatStateOf(original[0])}
    var saturation by remember {mutableFloatStateOf(original[1])}
    var value by remember {mutableFloatStateOf(original[2])}
    var hex by remember {mutableStateOf(MonoColor.hex(initial))}
    val color=MonoColor.hsv(hue,saturation,value)
    fun update(h:Float,s:Float,v:Float) {hue=(h+360)%360;saturation=s.coerceIn(0f,1f);value=v.coerceIn(0f,1f);hex=MonoColor.hex(MonoColor.hsv(hue,saturation,value))}
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        // Picker controls stay neutral while editing; only Apply persists the seed.
        CompositionLocalProvider(LocalNativeAppearance provides LocalNativeAppearance.current.copy(material="clear")) {
            Column(Modifier.padding(16.dp).widthIn(max=480.dp).fillMaxWidth().heightIn(max=680.dp)
                .sunnyPanel(22.dp).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("自定义颜色",color=SunnyColors.Text,fontSize=22.sp,fontWeight=FontWeight.Bold)
                Text("自由选择主色，深浅层次会自动搭配",color=SunnyColors.Secondary,fontSize=12.sp)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
                    HueWheel(hue,saturation,value,Modifier.size(176.dp)) {h,s->update(h,s,value)}
                }
                Text("亮度",color=SunnyColors.Secondary,fontSize=12.sp)
                BrightnessBar(hue,saturation,value,Modifier.fillMaxWidth().height(28.dp)) {update(hue,saturation,it)}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    val seed=AppearanceColors.custom(color)
                    for(dark in listOf(false,true)) {
                        val pp=palette(AppSettings(darkTheme=dark,appearanceColor="custom",customThemeColor=color))
                        Column(Modifier.weight(1f).background(pp.surface,sunnyShape(10.dp)).border(1.dp,pp.border,sunnyShape(10.dp)).padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            AppearanceSwatch(seed,dark,false)
                            Text(if(dark) "深色搭配" else "浅色搭配",color=pp.text,fontSize=12.sp)
                        }
                    }
                }
                Field("颜色值（#RRGGBB）",hex,{input->
                    hex=input.take(7)
                    MonoColor.parseHex(hex)?.let {rgb->val hsv=MonoColor.toHsv(rgb);hue=hsv[0];saturation=hsv[1];value=hsv[2]}
                },tag="color:hex")
                if(MonoColor.parseHex(hex)==null) Text("请输入 6 位十六进制颜色",color=SunnyColors.Danger,fontSize=12.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Action("应用",id="dialog:color:apply",primary=true,alwaysShowLabel=true) {MonoColor.parseHex(hex)?.let(onApply)}
                    Action("取消",id="dialog:color:cancel",alwaysShowLabel=true,onClick=onDismiss)
                }
            }
        }
    }
}

@Composable private fun HueWheel(hue:Float,saturation:Float,value:Float,modifier:Modifier,onChange:(Float,Float)->Unit) {
    var editing by remember {mutableStateOf(false)}
    var focused by remember {mutableStateOf(false)}
    val requester=remember {FocusRequester()}
    val currentChange by rememberUpdatedState(onChange)
    val ring=SunnyColors.Accent
    LaunchedEffect(Unit) {withFrameNanos {};requester.requestFocus()}
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Canvas(modifier.testTag("color:wheel").focusRequester(requester).onFocusChanged {focused=it.isFocused;if(!focused) editing=false}
            .onPreviewKeyEvent {e->
                if(e.type!=KeyEventType.KeyDown) false else when {
                    e.key in listOf(Key.Enter,Key.DirectionCenter,Key.NumPadEnter)->{editing=!editing;true}
                    editing && e.key in listOf(Key.Back,Key.Escape)->{editing=false;true}
                    editing && e.key==Key.DirectionLeft->{currentChange(hue-5,saturation);true}
                    editing && e.key==Key.DirectionRight->{currentChange(hue+5,saturation);true}
                    editing && e.key==Key.DirectionUp->{currentChange(hue,saturation+.05f);true}
                    editing && e.key==Key.DirectionDown->{currentChange(hue,saturation-.05f);true}
                    else->false
                }
            }.semantics {
                contentDescription="色盘"
                stateDescription="色相 ${hue.roundToInt()}，饱和度 ${(saturation*100).roundToInt()}%"
                customActions=listOf(CustomAccessibilityAction("增加色相") {currentChange(hue+5,saturation);true},
                    CustomAccessibilityAction("减少色相") {currentChange(hue-5,saturation);true},
                    CustomAccessibilityAction("增加饱和度") {currentChange(hue,saturation+.05f);true},
                    CustomAccessibilityAction("减少饱和度") {currentChange(hue,saturation-.05f);true})
            }.focusable().pointerInput(Unit) {
                awaitEachGesture {
                    val down=awaitFirstDown();down.consume()
                    fun change(point:Offset) {
                        val center=Offset(size.width/2f,size.height/2f);val delta=point-center
                        val r=(min(size.width,size.height)/2f-8.dp.toPx()).coerceAtLeast(1f)
                        currentChange((atan2(delta.y,delta.x)*180/PI).toFloat(),(delta.getDistance()/r).coerceIn(0f,1f))
                    }
                    change(down.position)
                    do {val event=awaitPointerEvent();val pointer=event.changes.firstOrNull {it.id==down.id} ?: break
                        change(pointer.position);pointer.consume()
                    } while(event.changes.any {it.pressed})
                }
            }) {
            val center=Offset(size.width/2,size.height/2);val radius=size.minDimension/2-8.dp.toPx()
            val colors=(0..6).map {Color(MonoColor.hsv(it*60f,1f,1f))}
            drawCircle(Brush.sweepGradient(colors,center),radius,center)
            drawCircle(Brush.radialGradient(listOf(Color.White,Color.Transparent),center,radius),radius,center)
            if(focused) drawCircle(ring, radius+4.dp.toPx(),center,style=Stroke(if(editing) 3.dp.toPx() else 1.dp.toPx()))
            val radians=hue*PI/180
            val dot=center+Offset(cos(radians).toFloat(),sin(radians).toFloat())*radius*saturation
            drawCircle(Color.Black.copy(.7f),7.dp.toPx(),dot,style=Stroke(3.dp.toPx()))
            drawCircle(Color.White,7.dp.toPx(),dot,style=Stroke(1.6.dp.toPx()))
        }
        Text(if(editing) "左右调色相 · 上下调浓淡 · 确定退出" else "拖动选色 · 遥控器按确定调整",color=SunnyColors.Secondary,fontSize=10.sp)
    }
}
@Composable private fun BrightnessBar(hue:Float,saturation:Float,value:Float,modifier:Modifier,onChange:(Float)->Unit) {
    var focused by remember {mutableStateOf(false)}
    val change by rememberUpdatedState(onChange)
    val edge=SunnyColors.Accent
    Canvas(modifier.testTag("color:brightness").onFocusChanged {focused=it.isFocused}
        .onPreviewKeyEvent {e->if(e.type==KeyEventType.KeyDown && e.key in listOf(Key.DirectionLeft,Key.DirectionRight)) {
            change((value+if(e.key==Key.DirectionRight) .025f else -.025f).coerceIn(0f,1f));true
        } else false}.semantics {
            contentDescription="颜色亮度";progressBarRangeInfo=ProgressBarRangeInfo(value,0f..1f)
            setProgress {change(it.coerceIn(0f,1f));true}
        }.focusable().pointerInput(Unit) {
            awaitEachGesture {
                val down=awaitFirstDown();down.consume();change((down.position.x/size.width).coerceIn(0f,1f))
                do {val event=awaitPointerEvent();val pointer=event.changes.firstOrNull {it.id==down.id} ?: break
                    change((pointer.position.x/size.width).coerceIn(0f,1f));pointer.consume()
                } while(event.changes.any {it.pressed})
            }
        }) {
        drawRoundRect(Brush.horizontalGradient(listOf(Color.Black,Color(MonoColor.hsv(hue,saturation,1f)))),cornerRadius=androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
        if(focused) drawRoundRect(edge,cornerRadius=androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),style=Stroke(3.dp.toPx()))
        val x=(value*size.width).coerceIn(3.dp.toPx(),size.width-3.dp.toPx())
        drawLine(Color.Black,Offset(x,2.dp.toPx()),Offset(x,size.height-2.dp.toPx()),5.dp.toPx())
        drawLine(Color.White,Offset(x,2.dp.toPx()),Offset(x,size.height-2.dp.toPx()),2.dp.toPx())
    }
}
