package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.Route

/** The focused square stays in place; only the media order rotates. */
@Composable fun RotatingHeroCards(items:List<MediaEntry>,selected:Int,onSelect:(Int)->Unit,
    modifier:Modifier=Modifier,id:String="home-carousel") {
    if(items.isEmpty()) return
    val model=LocalAppModel.current
    val index=selected.coerceIn(items.indices)
    val entry=items[index]
    val choose by rememberUpdatedState(onSelect)
    val currentIndex by rememberUpdatedState(index)
    val change:(Int)->Unit={delta->choose(Presentation.next(currentIndex,delta,items.size,true))}
    var direction by remember {mutableIntStateOf(1)}
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val containerWidth=maxWidth
        val side=(maxWidth*.55f).coerceAtMost(260.dp)
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=Alignment.Top) {
            Column(Modifier.width(side)) {
                FocusTile("$id:featured",Modifier.size(side).onPreviewKeyEvent {
                    if(it.type==KeyEventType.KeyDown && it.key in listOf(Key.DirectionLeft,Key.DirectionRight)) {
                        direction=if(it.key==Key.DirectionRight) 1 else -1;change(direction);true
                    } else false
                }.pointerInput(items.size) {
                    var drag=0f
                    detectHorizontalDragGestures(onDragStart={drag=0f},onDragEnd={
                        if(kotlin.math.abs(drag)>40.dp.toPx()) {direction=if(drag<0) 1 else -1;change(direction)}
                    }) {event,amount->event.consume();drag+=amount}
                },shape=RoundedCornerShape(18.dp),onClick={model.navigate(Route.Detail(entry))}) {
                    AnimatedContent(entry,transitionSpec={
                        val ms=if(model.settings.reduceMotion) 0 else 200
                        (fadeIn(tween(ms))+slideInHorizontally(tween(ms)) {it/8*direction}) togetherWith
                            (fadeOut(tween(ms))+slideOutHorizontally(tween(ms)) {-it/8*direction})
                    },label="hero-media") {media->
                        Box(Modifier.fillMaxSize()) {
                            ArtworkView(media,media.primary,Modifier.fillMaxSize(),700)
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Transparent,Color.Black.copy(.85f)))))
                            Text(media.title,color=Color.White,fontSize=18.sp,lineHeight=23.sp,fontWeight=FontWeight.SemiBold,maxLines=2,
                                overflow=TextOverflow.Ellipsis,modifier=Modifier.align(Alignment.BottomStart).padding(13.dp))
                        }
                    }
                }
                Text(entry.year.takeIf {it>0}?.toString().orEmpty(),color=SunnyColors.Secondary,fontSize=11.sp,modifier=Modifier.padding(top=8.dp))
            }
            val following=(1 until items.size).map {items[(index+it)%items.size]}
            val stripWidth=((containerWidth-side-9.dp-7.dp*(following.size-1).coerceAtLeast(0))/following.size.coerceAtLeast(1)).coerceAtLeast(20.dp)
            LazyRow(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                items(following,key={it.key}) {media->
                    Column(Modifier.width(stripWidth).animateItem()) {
                        FocusTile("$id:strip:${media.key}",Modifier.fillMaxWidth().height(side).focusProperties {canFocus=false},
                            shape=RoundedCornerShape(13.dp),onClick={direction=1;choose(items.indexOf(media))}) {
                            ArtworkView(media,media.primary,Modifier.fillMaxSize(),320)
                        }
                        Text(media.year.takeIf {it>0}?.toString().orEmpty(),color=SunnyColors.Secondary,fontSize=10.sp,modifier=Modifier.padding(top=8.dp))
                    }
                }
            }
        }
    }
}

@Composable fun HomeHeroContent(hero:MediaEntry?,candidates:List<MediaEntry>,selected:Int,onSelect:(Int)->Unit,
    resume:List<MediaEntry>,paused:Boolean,onPause:()->Unit,onPlay:(MediaEntry,Boolean)->Unit) {
    val compact=LocalCompact.current
    val model=LocalAppModel.current
    @Composable fun Copy() {
        Column(verticalArrangement=Arrangement.spacedBy(if(compact) 9.dp else 13.dp)) {
            Text(if(model.settings.heroMode=="resume") "为你继续" else "首映推荐",color=SunnyColors.Accent,fontSize=11.sp)
            if(hero!=null) {
                MediaTitle(hero,if(compact) 29.sp else 40.sp)
                Text(hero.subtitle,color=SunnyColors.Secondary,fontSize=12.sp)
                Text(hero.overview.ifBlank {"来自你的媒体库"},color=SunnyColors.Secondary,fontSize=13.sp,lineHeight=21.sp,
                    maxLines=if(compact) 2 else 3,overflow=TextOverflow.Ellipsis)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(hero.isPlayable || hero.type in setOf("Series","Season")) Action(if(hero.positionMs>0) "继续播放" else "立即播放",id="home-play",primary=true) {onPlay(hero,false)}
                    Action("查看详情",id="home-detail") {model.navigate(Route.Detail(hero))}
                    Action(if(paused) "自动轮播" else "暂停轮播",id="home-pause",active=paused,onClick=onPause)
                }
            } else Text("暂无推荐",color=SunnyColors.Text,fontSize=28.sp)
            if(resume.isNotEmpty()) {
                Text("继续播放",color=SunnyColors.Secondary,fontSize=11.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    resume.take(2).forEach {media->
                        FocusTile("quick-resume:${media.key}",Modifier.weight(1f),shape=RoundedCornerShape(24.dp),onClick={onPlay(media,false)}) {
                            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                ArtworkView(media,media.primary,Modifier.size(32.dp).clip(CircleShape),128)
                                Column(Modifier.weight(1f)) {
                                    Text(media.title,color=SunnyColors.Text,fontSize=11.sp,lineHeight=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    Text("剩余 ${MediaLogic.remainingMinutes(media.positionMs,media.durationMs)} 分钟",color=SunnyColors.Secondary,fontSize=9.sp,lineHeight=12.sp,maxLines=1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if(compact) Column(Modifier.fillMaxSize().padding(horizontal=18.dp).padding(top=pageTopPadding,bottom=20.dp),
        verticalArrangement=Arrangement.spacedBy(18.dp,Alignment.Bottom)) {
        Copy()
        RotatingHeroCards(candidates,selected,onSelect)
    } else Row(Modifier.fillMaxSize().padding(horizontal=30.dp).padding(top=pageTopPadding,bottom=28.dp),
        horizontalArrangement=Arrangement.spacedBy(24.dp),verticalAlignment=Alignment.Bottom) {
        Box(Modifier.weight(.4f)) {Copy()}
        RotatingHeroCards(candidates,selected,onSelect,Modifier.weight(.6f))
    }
}
