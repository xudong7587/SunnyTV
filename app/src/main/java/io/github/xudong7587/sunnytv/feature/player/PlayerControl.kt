package io.github.xudong7587.sunnytv.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.tv.material3.Text
import androidx.tv.material3.LocalTextStyle
import io.github.xudong7587.sunnytv.feature.ui.LineIcon
import io.github.xudong7587.sunnytv.feature.ui.LocalMotion
import io.github.xudong7587.sunnytv.feature.ui.SunnyColors
import io.github.xudong7587.sunnytv.feature.ui.glassButtonFace
import io.github.xudong7587.sunnytv.feature.ui.LocalNativeAppearance
import io.github.xudong7587.sunnytv.feature.ui.LocalSunnyPalette

/**
 * Lightweight player chrome for TV: no blur and no large shadow layer. The focused control gets
 * one crisp accent ring, a restrained glass fill and a small scale animation. Transport icons are
 * kept optically heavier than utility icons without changing the D-pad hit target.
 */
@Composable internal fun PlayerControl(label:String,icon:String,modifier:Modifier=Modifier,
    initial:Boolean=false,emphasis:Boolean=false,badge:String?=null,selected:Boolean=false,showFocusLabel:Boolean=true,labelBelow:Boolean=false,onClick:()->Unit) {
    val requester=remember {FocusRequester()}
    var focused by remember {mutableStateOf(false)}
    val motion=LocalMotion.current
    val liquid=LocalNativeAppearance.current.material=="glass"
    val monochrome=if(LocalSunnyPalette.current.background.luminance()<.3f) Color.White else Color.Black
    val scale by animateFloatAsState(if(focused && !liquid) 1.075f else 1f,motion.spring(),label="player-control-scale")
    val size=if(emphasis) 56.dp else 48.dp
    val fill=when {
        focused -> Color.White.copy(.16f)
        selected -> SunnyColors.Accent.copy(.12f)
        emphasis -> Color.White.copy(.10f)
        else -> Color.White.copy(.025f)
    }
    val ring=when {
        focused -> SunnyColors.Accent
        selected -> SunnyColors.Accent.copy(.48f)
        emphasis -> Color.White.copy(.20f)
        else -> Color.White.copy(.08f)
    }
    val glyph=if(liquid) monochrome else if(focused || selected) SunnyColors.Accent else Color.White.copy(if(emphasis) .96f else .84f)
    LaunchedEffect(initial) {if(initial) {withFrameNanos {};requester.requestFocus()}}
    Box(modifier.size(size).testTag("player:$icon").semantics {contentDescription=label}
        .graphicsLayer {scaleX=scale;scaleY=scale}
        .focusRequester(requester).onFocusChanged {focused=it.isFocused}
        .glassButtonFace(CircleShape,focused || selected)
        .drawWithCache {
            val stroke=if(focused) 2.dp.toPx() else 1.dp.toPx()
            onDrawBehind {
                if(!liquid) {
                    drawCircle(fill)
                    drawCircle(ring,style=Stroke(stroke))
                }
            }
        }
        .clickable(onClick=onClick),contentAlignment=Alignment.Center) {
        PlayerGlyph(icon,glyph,badge,emphasis)
        AnimatedVisibility((focused || icon=="subtitle-timing") && showFocusLabel,
            enter=fadeIn(motion.fade(150))+scaleIn(motion.spring(),initialScale=.94f),
            exit=fadeOut(motion.fade(120))+scaleOut(motion.spring(),targetScale=.96f),
            modifier=Modifier.align(Alignment.TopCenter).offset(y=if(labelBelow) size+8.dp else (-39).dp)) {
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
    // Text badges need more width than a 25dp icon: two 13sp CJK glyphs already
    // exceed it at normal font scale, clipping the second character.
    val glyphModifier=if(icon=="quality" || icon=="frame") Modifier.width(40.dp).height(32.dp) else Modifier.size(glyphSize)
    Box(glyphModifier,contentAlignment=Alignment.Center) {
        if(icon=="quality" || icon=="frame") {
            BasicText(badge ?: if(icon=="frame") "适应" else "原画",modifier=Modifier.fillMaxWidth(),
                style=LocalTextStyle.current.copy(color=color,fontWeight=FontWeight.ExtraBold,textAlign=TextAlign.Center,
                    lineHeight=TextUnit.Unspecified,letterSpacing=0.sp),
                autoSize=TextAutoSize.StepBased(minFontSize=6.sp,maxFontSize=13.sp,stepSize=.5.sp),
                maxLines=1,softWrap=false)
        } else if(icon=="speed") {
            // Speed is intentionally just the value: no pill, gauge or decorative color block.
            Text(badge ?: "1.0x",color=color,fontSize=9.5.sp,fontWeight=FontWeight.ExtraBold,maxLines=1)
        } else {
            LineIcon(icon,color,Modifier.fillMaxSize())
        }
        if(icon=="rewind" || icon=="forward") {
            Text(badge ?: "10",color=color,fontSize=8.5.sp,fontWeight=FontWeight.Bold,maxLines=1)
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
        .glassButtonFace(RoundedCornerShape(12.dp)).clickable(onClick=onClick).padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(30.dp).background(if(selected) SunnyColors.Accent.copy(.14f) else Color.White.copy(.05f),CircleShape),contentAlignment=Alignment.Center) {
            LineIcon(if(selected) "check" else "audio",if(selected) SunnyColors.Accent else Color.White.copy(.62f),Modifier.size(17.dp))
        }
        Text(label,color=Color.White,fontSize=14.sp,lineHeight=20.sp,fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines=3,overflow=TextOverflow.Ellipsis)
    }
}

