package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.R

/**
 * The player's brand loading mark, shared with the browse screens. Used when a carousel is
 * deliberately refreshed, so "reading" has one visual language everywhere.
 */
@Composable fun SunnyBrandLoading(visible:Boolean,modifier:Modifier=Modifier,scopeTag:String="browse:loading",
    scrim:Boolean=false) {
    if(!visible) return
    val transition=rememberInfiniteTransition(label="sunny-brand-loading")
    val y by transition.animateFloat(-9f,5f,infiniteRepeatable(
        animation=tween(620,easing=FastOutSlowInEasing),repeatMode=RepeatMode.Reverse),label="loading-y")
    val alpha by transition.animateFloat(.45f,1f,infiniteRepeatable(
        animation=tween(900,easing=LinearEasing),repeatMode=RepeatMode.Reverse),label="loading-alpha")
    Column(modifier.fillMaxSize().testTag(scopeTag).then(if(scrim) Modifier.background(Color.Black.copy(.45f)) else Modifier),
        verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.ic_sun_brand),"SunnyTV",
            Modifier.size(72.dp).graphicsLayer {translationY=y.dp.toPx()})
        Text("L O A D I N G",color=Color.White.copy(alpha),fontSize=13.sp,fontWeight=FontWeight.SemiBold,
            letterSpacing=3.sp,modifier=Modifier.padding(top=18.dp))
    }
}
