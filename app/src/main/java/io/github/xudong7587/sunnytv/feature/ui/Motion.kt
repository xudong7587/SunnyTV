package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import io.github.xudong7587.sunnytv.core.model.MotionPolicy

data class MotionTokens(val speed:Float=1f) {
    val enabled get()=speed>0f
    fun duration(ms:Int)=MotionPolicy.duration(ms,speed)
    fun <T> spring():FiniteAnimationSpec<T> = if(enabled)
        spring(dampingRatio=1f,stiffness=MotionPolicy.stiffness(speed)) else snap()
    fun <T> fade(ms:Int=300):TweenSpec<T> = tween(duration(ms),easing=CubicBezierEasing(.23f,1f,.32f,1f))
}
val LocalMotion=staticCompositionLocalOf {MotionTokens()}

@Stable class TvFocusMotion {
    var horizontal by mutableStateOf(false)
    fun record(event:KeyEvent) {
        if(event.type==KeyEventType.KeyDown) when(event.key) {
            Key.DirectionLeft,Key.DirectionRight -> horizontal=true
            Key.DirectionUp,Key.DirectionDown,Key.Tab -> horizontal=false
        }
    }
}
val LocalTvFocusMotion=staticCompositionLocalOf {TvFocusMotion()}

/** Apply only to a vertical scrolling surface. Horizontal rows override it below. */
@OptIn(ExperimentalFoundationApi::class)
@Composable fun StableVerticalViewport(hold:()->Boolean={false},content:@Composable ()->Unit) {
    val compact=LocalCompact.current
    val input=LocalTvFocusMotion.current
    val topInset=with(LocalDensity.current) {if(!compact && LocalNavVisible.current) 76.dp.toPx() else 0f}
    val currentHold by rememberUpdatedState(hold)
    val spec=remember(input,compact,topInset) {object:BringIntoViewSpec {
        override fun calculateScrollDistance(offset:Float,size:Float,containerSize:Float):Float {
            if(!compact && (input.horizontal || currentHold())) return 0f
            // Minimal reveal, no TV pivot re-centering every time a child changes width.
            return when {
                size>=containerSize && offset<=0 && offset+size>=containerSize -> 0f
                offset<topInset -> offset-topInset
                offset+size>containerSize -> offset+size-containerSize
                else -> 0f
            }
        }
    }}
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec,content=content)
}

@OptIn(ExperimentalFoundationApi::class)
private class HorizontalReveal(private val gutter:Float):BringIntoViewSpec {
    override fun calculateScrollDistance(offset:Float,size:Float,containerSize:Float):Float {
        val inset=gutter.coerceAtMost(((containerSize-size)/2f).coerceAtLeast(0f))
        return when {
            offset<inset -> offset-inset
            offset+size>containerSize-inset -> offset+size-containerSize+inset
            else -> 0f
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun HorizontalViewport(content:@Composable ()->Unit) {
    val gutter=with(LocalDensity.current) {24.dp.toPx()}
    val spec=remember(gutter) {HorizontalReveal(gutter)}
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec,content=content)
}

@Composable fun StableLazyRow(modifier:Modifier=Modifier,state:LazyListState=rememberLazyListState(),
    contentPadding:PaddingValues=PaddingValues(0.dp),horizontalArrangement:Arrangement.Horizontal=Arrangement.Start,
    verticalAlignment:Alignment.Vertical=Alignment.Top,reserveFocusSpace:Boolean=true,content:LazyListScope.()->Unit) {
    val direction=LocalLayoutDirection.current
    val safePadding=if(!reserveFocusSpace) contentPadding else PaddingValues(
        start=maxOf(24.dp,contentPadding.calculateStartPadding(direction)),
        end=maxOf(24.dp,contentPadding.calculateEndPadding(direction)),
        top=maxOf(18.dp,contentPadding.calculateTopPadding()),
        bottom=maxOf(18.dp,contentPadding.calculateBottomPadding()))
    HorizontalViewport {
        LazyRow(modifier,state=state,contentPadding=safePadding,horizontalArrangement=horizontalArrangement,
            verticalAlignment=verticalAlignment,content=content)
    }
}

/** The hero and first library row are one precomposed stage; animate only its scroll offset. */
suspend fun LazyListState.moveHomePage(index:Int,heroExtentPx:Float,motion:MotionTokens) {
    val target=if(index==0) 0 else heroExtentPx.toInt()
    if(!motion.enabled || firstVisibleItemIndex!=0) {scrollToItem(0,target);return}
    animateScrollBy(target-firstVisibleItemScrollOffset.toFloat(),animationSpec=motion.fade(420))
}
