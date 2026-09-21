package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.core.model.*

@Composable fun PosterWallCard(item:MediaEntry,mode:String,modifier:Modifier=Modifier,onClick:()->Unit) {
    PosterWallSlot(item,mode,modifier,"grid:${item.key}",onNeedContent={},onClick={onClick()})
}

/** Stable virtualized slot for large libraries. Empty slots are real focus targets so D-pad paging
 * never jumps back to the pinned toolbar while the next Emby page is still loading. */
@Composable fun PosterWallSlot(item:MediaEntry?,mode:String,modifier:Modifier=Modifier,focusId:String,loadGeneration:Int=0,
    onNeedContent:()->Unit,onClick:(MediaEntry)->Unit) {
    val ratio=when(mode) {"Thumb"->16f/9;"Banner"->3.6f;else->2f/3}
    // Re-check visible empty slots after each appended page. This lets a viewport that spans
    // multiple server pages continue filling without introducing a focusable “load more” row.
    LaunchedEffect(item,focusId,loadGeneration) {if(item==null) onNeedContent()}
    val model=LocalAppModel.current
    Column {
        FocusTile(focusId,modifier.fillMaxWidth().aspectRatio(ratio),onFocus={if(item==null) onNeedContent()},
            onLongPress=item?.let {{model.showItemActions(it)}},
            onClick={if(item==null) onNeedContent() else onClick(item)}) {
            if(item!=null) ArtworkView(item,when(mode) {"Thumb"->MediaLogic.wideArtwork(item);"Banner"->item.banner ?: item.thumb ?: item.backdrop;else->item.primary},
                Modifier.fillMaxSize(),if(mode=="Poster") 400 else 720,fit=false)
            else Box(Modifier.fillMaxSize().background(SunnyColors.SurfaceRaised.copy(.30f)))
        }
        if(item!=null) {
            Text(item.title,color=SunnyColors.Text,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=8.dp))
            Text(item.subtitle,color=SunnyColors.Secondary,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        } else Spacer(Modifier.height(37.dp))
    }
}
