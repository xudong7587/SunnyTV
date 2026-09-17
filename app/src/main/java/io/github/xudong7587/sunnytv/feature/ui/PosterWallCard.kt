package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.core.model.*

@Composable fun PosterWallCard(item:MediaEntry,mode:String,onClick:()->Unit) {
    Column {
        FocusTile("grid:${item.key}",Modifier.fillMaxWidth().aspectRatio(when(mode) {"Thumb"->16f/9;"Banner"->3.6f;else->2f/3}),onClick=onClick) {
            ArtworkView(item,when(mode) {"Thumb"->MediaLogic.wideArtwork(item);"Banner"->item.banner ?: item.thumb ?: item.backdrop;else->item.primary},
                Modifier.fillMaxSize(),if(mode=="Poster") 400 else 720,fit=mode=="Banner")
        }
        Text(item.title,color=SunnyColors.Text,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=8.dp))
        Text(item.subtitle,color=SunnyColors.Secondary,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}
