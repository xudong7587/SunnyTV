package io.github.xudong7587.sunnytv.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.feature.ui.LineIcon
import io.github.xudong7587.sunnytv.feature.ui.LocalMotion
import io.github.xudong7587.sunnytv.feature.ui.SunnyColors

/**
 * Lightweight player chrome for TV: no blur and no large shadow layer. The focused control gets
 * one crisp accent ring, a restrained glass fill and a small scale animation. Transport icons are
 * kept optically heavier than utility icons without changing the D-pad hit target.
 */
@Composable internal fun PlayerControl(label:String,icon:String,modifier:Modifier=Modifier,
    initial:Boolean=false,emphasis:Boolean=false,badge:String?=null,showFocusLabel:Boolean=true,onClick:()->Unit) {
    val requester=remember {FocusRequester()}
    var focused by remember {mutableStateOf(false)}
    val motion=LocalMotion.current
    val scale by animateFloatAsState(if(focused) 1.075f else 1f,motion.spring(),label="player-control-scale")
    val size=if(emphasis) 56.dp else 48.dp
    val fill=when {
        focused -> Color.White.copy(.16f)
        emphasis -> Color.White.copy(.10f)
        else -> Color.White.copy(.025f)
    }
    val ring=when {
        focused -> SunnyColors.Accent
        emphasis -> Color.White.copy(.20f)
        else -> Color.White.copy(.08f)
    }
    val glyph=if(focused) SunnyColors.Accent else Color.White.copy(if(emphasis) .96f else .84f)
    LaunchedEffect(initial) {if(initial) {withFrameNanos {};requester.requestFocus()}}
    Box(modifier.size(size).testTag("player:$icon").semantics {contentDescription=label}
        .graphicsLayer {scaleX=scale;scaleY=scale}
        .focusRequester(requester).onFocusChanged {focused=it.isFocused}
        .drawWithCache {
            val stroke=if(focused) 2.dp.toPx() else 1.dp.toPx()
            onDrawBehind {
                drawCircle(fill)
                drawCircle(ring,style=Stroke(stroke))
            }
        }
        .clickable(onClick=onClick),contentAlignment=Alignment.Center) {
        PlayerGlyph(icon,glyph,badge,emphasis)
        AnimatedVisibility(focused && showFocusLabel,
            enter=fadeIn(motion.fade(150))+scaleIn(motion.spring(),initialScale=.94f),
            exit=fadeOut(motion.fade(120))+scaleOut(motion.spring(),targetScale=.96f),
            modifier=Modifier.align(Alignment.TopCenter).offset(y=(-39).dp)) {
            Text(label,color=Color.White,fontSize=11.sp,fontWeight=FontWeight.Medium,maxLines=1,
                modifier=Modifier.wrapContentSize(unbounded=true)
                    .background(Color.Black.copy(.76f),RoundedCornerShape(10.dp))
                    .border(1.dp,Color.White.copy(.10f),RoundedCornerShape(10.dp))
                    .padding(horizontal=10.dp,vertical=5.dp))
        }
    }
}

@Composable private fun PlayerGlyph(icon:String,color:Color,badge:String?,emphasis:Boolean) {
    val glyphSize=if(emphasis) 30.dp else 25.dp
    Box(Modifier.size(glyphSize),contentAlignment=Alignment.Center) {
        if(icon=="speed") {
            // Speed is intentionally just the value: no pill, gauge or decorative color block.
            Text(badge ?: "1.0x",color=color,fontSize=9.5.sp,fontWeight=FontWeight.ExtraBold,maxLines=1)
        } else Canvas(Modifier.fillMaxSize()) {
            val w=size.width; val h=size.height
            fun x(v:Float)=w*v/24f
            fun y(v:Float)=h*v/24f
            fun triangle(points:List<Offset>) {drawPath(Path().apply {moveTo(points[0].x,points[0].y);lineTo(points[1].x,points[1].y);lineTo(points[2].x,points[2].y);close()},color)}
            when(icon) {
                "play" -> triangle(listOf(Offset(x(7f),y(4f)),Offset(x(20f),y(12f)),Offset(x(7f),y(20f))))
                "pause" -> {
                    drawRoundRect(color,Offset(x(6.5f),y(4f)),Size(x(4f),y(16f)),CornerRadius(x(1.2f)))
                    drawRoundRect(color,Offset(x(13.5f),y(4f)),Size(x(4f),y(16f)),CornerRadius(x(1.2f)))
                }
                "previous" -> {
                    drawRoundRect(color,Offset(x(4.5f),y(4f)),Size(x(2.5f),y(16f)),CornerRadius(x(.7f)))
                    triangle(listOf(Offset(x(18.5f),y(4.5f)),Offset(x(8f),y(12f)),Offset(x(18.5f),y(19.5f))))
                }
                "next" -> {
                    drawRoundRect(color,Offset(x(17f),y(4f)),Size(x(2.5f),y(16f)),CornerRadius(x(.7f)))
                    triangle(listOf(Offset(x(5.5f),y(4.5f)),Offset(x(16f),y(12f)),Offset(x(5.5f),y(19.5f))))
                }
                "rewind" -> {
                    // Counter-clockwise 10-second seek: one heavy circular arrow around the value.
                    drawArc(color,55f,-285f,false,style=Stroke(x(2.2f),cap=StrokeCap.Round),
                        topLeft=Offset(x(3.6f),y(3.6f)),size=Size(x(16.8f),y(16.8f)))
                    triangle(listOf(Offset(x(4.2f),y(5.0f)),Offset(x(9.1f),y(4.3f)),Offset(x(6.4f),y(8.4f))))
                }
                "forward" -> {
                    // Clockwise counterpart, mirrored so both transport icons read as a pair.
                    drawArc(color,-235f,285f,false,style=Stroke(x(2.2f),cap=StrokeCap.Round),
                        topLeft=Offset(x(3.6f),y(3.6f)),size=Size(x(16.8f),y(16.8f)))
                    triangle(listOf(Offset(x(19.8f),y(5.0f)),Offset(x(14.9f),y(4.3f)),Offset(x(17.6f),y(8.4f))))
                }
                "sleep" -> {
                    val moon=Path().apply {
                        moveTo(x(15.8f),y(3.5f));cubicTo(x(9.8f),y(4.2f),x(6.2f),y(9.2f),x(7.6f),y(14.3f))
                        cubicTo(x(9.0f),y(19.2f),x(14.3f),y(21.4f),x(19.1f),y(18.5f))
                        cubicTo(x(12.2f),y(18.8f),x(9.4f),y(9.0f),x(15.8f),y(3.5f));close()
                    }
                    drawPath(moon,color)
                    drawCircle(color,x(1.05f),Offset(x(18.2f),y(7.2f)))
                }
                "chapters" -> {
                    repeat(3) {i->
                        val yy=6f+i*6f
                        drawRoundRect(color,Offset(x(4f),y(yy)),Size(x(3f),y(3f)),CornerRadius(x(.6f)))
                        drawRoundRect(color,Offset(x(9f),y(yy+.5f)),Size(x(11f),y(2f)),CornerRadius(x(1f)))
                    }
                }
                "subtitle" -> {
                    drawRoundRect(color.copy(alpha=.28f),Offset(x(3f),y(5f)),Size(x(18f),y(14f)),CornerRadius(x(2.3f)))
                    drawRoundRect(color,Offset(x(6f),y(10f)),Size(x(5f),y(2.2f)),CornerRadius(x(1f)))
                    drawRoundRect(color,Offset(x(13f),y(10f)),Size(x(5f),y(2.2f)),CornerRadius(x(1f)))
                    drawRoundRect(color,Offset(x(6f),y(14f)),Size(x(12f),y(2.2f)),CornerRadius(x(1f)))
                }
                "audio" -> {
                    val speaker=Path().apply {moveTo(x(4f),y(10f));lineTo(x(8f),y(10f));lineTo(x(13f),y(6f));lineTo(x(13f),y(18f));lineTo(x(8f),y(14f));lineTo(x(4f),y(14f));close()}
                    drawPath(speaker,color)
                    drawArc(color,300f,120f,false,style=Stroke(x(1.8f),cap=StrokeCap.Round),topLeft=Offset(x(11f),y(7f)),size=Size(x(8f),y(10f)))
                }
                "cast" -> {
                    drawCircle(color,x(3.1f),Offset(x(10f),y(8f)))
                    drawOval(color,Offset(x(4.5f),y(13f)),Size(x(11f),y(7f)))
                    drawCircle(color.copy(alpha=.72f),x(2.3f),Offset(x(17.5f),y(9f)))
                    drawOval(color.copy(alpha=.72f),Offset(x(14f),y(14f)),Size(x(7f),y(5f)))
                }
                "frame" -> {
                    val t=x(2.4f); val l=x(6f)
                    drawRect(color,Offset(x(3f),y(3f)),Size(l,t));drawRect(color,Offset(x(3f),y(3f)),Size(t,l))
                    drawRect(color,Offset(x(15f),y(3f)),Size(l,t));drawRect(color,Offset(x(18.6f),y(3f)),Size(t,l))
                    drawRect(color,Offset(x(3f),y(18.6f)),Size(l,t));drawRect(color,Offset(x(3f),y(15f)),Size(t,l))
                    drawRect(color,Offset(x(15f),y(18.6f)),Size(l,t));drawRect(color,Offset(x(18.6f),y(15f)),Size(t,l))
                }
                "info" -> {
                    drawCircle(color,x(9f),Offset(x(12f),y(12f)))
                    drawCircle(Color.Black.copy(.72f),x(1.15f),Offset(x(12f),y(7.7f)))
                    drawRoundRect(Color.Black.copy(.72f),Offset(x(10.9f),y(10f)),Size(x(2.2f),y(7f)),CornerRadius(x(1.1f)))
                }
                "exit" -> {
                    drawRoundRect(color.copy(alpha=.72f),Offset(x(4f),y(4f)),Size(x(3f),y(16f)),CornerRadius(x(.8f)))
                    drawRoundRect(color,Offset(x(8f),y(10.7f)),Size(x(10f),y(2.6f)),CornerRadius(x(1.3f)))
                    triangle(listOf(Offset(x(15f),y(6.5f)),Offset(x(21f),y(12f)),Offset(x(15f),y(17.5f))))
                }
                else -> {
                    // Less common player actions keep the shared icon language, centered in the same box.
                    drawCircle(color.copy(alpha=.18f),x(9.5f),Offset(x(12f),y(12f)))
                }
            }
        }
        if(icon=="rewind" || icon=="forward") {
            Text(badge ?: "10",color=color,fontSize=8.5.sp,fontWeight=FontWeight.ExtraBold,maxLines=1)
        } else if(icon !in setOf("speed","play","pause","previous","next","sleep","chapters","subtitle","audio","cast","frame","info","exit","rewind","forward")) {
            LineIcon(icon,color,Modifier.size(18.dp))
        }
    }
}

@Composable internal fun PlayerOption(label:String,selected:Boolean=false,initial:Boolean=false,onClick:()->Unit) {
    var focused by remember {mutableStateOf(false)}
    val requester=remember {FocusRequester()}
    val motion=LocalMotion.current
    val scale by animateFloatAsState(if(focused) 1.012f else 1f,motion.spring(),label="player-option-scale")
    LaunchedEffect(Unit) {if(initial) {withFrameNanos {};requester.requestFocus()}}
    Row(Modifier.fillMaxWidth().graphicsLayer {scaleX=scale;scaleY=scale}.focusRequester(requester).onFocusChanged {focused=it.isFocused}
        .background(when {focused->Color.White.copy(.14f);selected->SunnyColors.Accent.copy(.10f);else->Color.White.copy(.045f)},RoundedCornerShape(14.dp))
        .border(if(focused) 1.5.dp else 1.dp,when {focused->SunnyColors.Accent;selected->SunnyColors.Accent.copy(.55f);else->Color.White.copy(.08f)},RoundedCornerShape(14.dp))
        .clickable(onClick=onClick).padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(30.dp).background(if(selected) SunnyColors.Accent.copy(.14f) else Color.White.copy(.05f),CircleShape),contentAlignment=Alignment.Center) {
            LineIcon(if(selected) "check" else "audio",if(selected) SunnyColors.Accent else Color.White.copy(.62f),Modifier.size(17.dp))
        }
        Text(label,color=Color.White,fontSize=14.sp,lineHeight=20.sp,fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines=3,overflow=TextOverflow.Ellipsis)
    }
}
