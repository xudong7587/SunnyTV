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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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

/**
 * Handset "scroll past the top": once the surface cannot scroll any further, a continued downward
 * drag triggers [onTrigger]. The consumed overscroll is not passed on, so the list stays still.
 */
@Composable fun Modifier.pullDownToRefresh(enabled:Boolean,atTop:()->Boolean,onTrigger:()->Unit):Modifier {
    if(!enabled) return this
    val threshold=with(LocalDensity.current) {96.dp.toPx()}
    val travelled=remember {floatArrayOf(0f)}
    val top=rememberUpdatedState(atTop)
    val trigger=rememberUpdatedState(onTrigger)
    return nestedScroll(remember(threshold) {object:NestedScrollConnection {
        override fun onPreScroll(available:Offset,source:NestedScrollSource):Offset {
            travelled[0]=0f
            return Offset.Zero
        }
        override fun onPostScroll(consumed:Offset,available:Offset,source:NestedScrollSource):Offset {
            // Any downward leftover while the surface cannot scroll back is a pull gesture; the
            // source enum differs between Compose versions, so it is not used as a filter.
            if(available.y<=0f || !top.value()) {
                travelled[0]=0f
                return Offset.Zero
            }
            travelled[0]+=available.y
            if(travelled[0]>=threshold) {travelled[0]=0f;trigger.value()}
            return Offset(0f,available.y)
        }
    }})
}

/**
 * Pull-to-refresh attached directly to a banner: a downward drag that starts on the banner while
 * its page is already at the top triggers [onTrigger] on release. The drag is consumed so the
 * banner does not fight the list's overscroll, and upward drags are left to normal scrolling.
 */
@Composable fun Modifier.pullDownOnBanner(enabled:Boolean,atTop:()->Boolean,onTrigger:()->Unit):Modifier {
    if(!enabled) return this
    val threshold=with(LocalDensity.current) {48.dp.toPx()}
    val top=rememberUpdatedState(atTop)
    val trigger=rememberUpdatedState(onTrigger)
    return pointerInput(threshold) {
        val slop=(viewConfiguration.touchSlop*.6f).coerceAtLeast(4f)
        awaitPointerEventScope {
            while(true) {
                val down=awaitFirstDown(requireUnconsumed=false)
                var pulled=0f
                var claiming=false
                while(true) {
                    val event=awaitPointerEvent()
                    val change=event.changes.firstOrNull {it.id==down.id} ?: break
                    if(!change.pressed) {
                        if(claiming && pulled>=threshold) trigger.value()
                        break
                    }
                    val dy=change.position.y-change.previousPosition.y
                    if(claiming) {
                        // Once claimed, keep the rest of this gesture away from the list.
                        if(dy>0f) {pulled+=dy;change.consume()}
                    } else if(top.value() && dy>0f) {
                        pulled+=dy
                        if(pulled>slop) {claiming=true;change.consume()}
                    } else if(dy<0f) {
                        // Upward drag: hand the gesture back to the list so the page still scrolls.
                        break
                    }
                }
            }
        }
    }
}

/** Apply only to a vertical scrolling surface. Horizontal rows override it below. */
@OptIn(ExperimentalFoundationApi::class)
@Composable fun StableVerticalViewport(hold:()->Boolean={false},content:@Composable ()->Unit) {
    StableVerticalViewport(hold=hold,topInsetOverride=null,content=content)
}

/**
 * [topInsetOverride] replaces the pinned-bar inset for the whole surface. Pages that switch between
 * a banner and a media area pass 0.dp, so a focused row can sit flush at the very top instead of
 * being pushed back under the bar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable fun StableVerticalViewport(hold:()->Boolean,topInsetOverride:Dp?,content:@Composable ()->Unit) {
    val compact=LocalCompact.current
    val input=LocalTvFocusMotion.current
    val topInset=with(LocalDensity.current) {
        topInsetOverride?.toPx() ?: if(!compact && LocalNavVisible.current) 76.dp.toPx() else 0f
    }
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

/**
 * Cross the home hero boundary without an oversized compound lazy item.
 * Hero (index 0) and libraries (index 1) are adjacent lazy items. We still use a single
 * controlled scroll so the global motion speed applies, but Compose can prefetch/measure the
 * first library row independently and can stop drawing the full hero once it leaves the viewport.
 */
suspend fun LazyListState.moveHomePage(index:Int,heroExtentPx:Float,motion:MotionTokens) {
    val targetIndex=index.coerceIn(0,1)
    val extent=heroExtentPx.coerceAtLeast(1f)
    val absoluteScroll=when(firstVisibleItemIndex) {
        0 -> firstVisibleItemScrollOffset.toFloat()
        1 -> extent + firstVisibleItemScrollOffset.toFloat()
        else -> {
            // Returning from deeper rows: put the adjacent boundary back in a known state first.
            scrollToItem(if(targetIndex==0) 0 else 1)
            if(targetIndex==0) 0f else extent
        }
    }
    val target=if(targetIndex==0) 0f else extent
    val delta=target-absoluteScroll
    if(kotlin.math.abs(delta)<1f) {
        if(firstVisibleItemIndex!=targetIndex || firstVisibleItemScrollOffset!=0) scrollToItem(targetIndex)
        return
    }
    if(!motion.enabled) scrollToItem(targetIndex) else animateScrollBy(delta,animationSpec=motion.fade(420))
    // Finish on an exact item boundary so the following focus request cannot trigger a corrective
    // bring-into-view scroll. This snap is sub-pixel/rounding cleanup after the visible animation.
    if(firstVisibleItemIndex!=targetIndex || firstVisibleItemScrollOffset!=0) scrollToItem(targetIndex)
}
