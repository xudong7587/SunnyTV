package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.R

@Composable fun SunnyAbout() {
    val dark=LocalAppModel.current.settings.darkTheme
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        ProjectHeading("SunnyTV",if(dark) R.drawable.ic_sun_brand else R.drawable.ic_sun_brand_light,
            "sunnytv","https://github.com/xudong7587/SunnyTV")
        Text("精致入眼，流畅入戏。",color=SunnyColors.Text,fontSize=18.sp,fontWeight=FontWeight.Medium)
        Text("为自己的媒体库，留一块好屏幕。SunnyTV 将 Emby、绿联影视中心与云端媒体汇在熟悉的大屏界面中，让找片、选片和播放自然衔接。\n\n清透的液态玻璃、细腻的海报排布和顺手的遥控器操作，让每次打开都轻快从容。原生播放器保留清晰度切换、字幕与画面比例设置，也能配合灯带，让观影更有氛围。",
            color=SunnyColors.Secondary,fontSize=14.sp,lineHeight=24.sp)
        Text("SunnyTV 持续开源，液态玻璃实现随项目一同分享。欢迎在 GitHub 查看源码、反馈问题，或参与改进。",
            color=SunnyColors.Secondary,fontSize=13.sp,lineHeight=22.sp)
    }
}

@Composable fun MediaIndexAbout() {
    Column(Modifier.padding(top=14.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        ProjectHeading("MediaIndex",R.drawable.media_index_icon,"mediaindex","https://github.com/xudong7587/media-index")
        Text("把媒体整理妥当，让播放触手可及。",color=SunnyColors.Text,fontSize=17.sp,fontWeight=FontWeight.Medium)
        Text("MediaIndex 是作者维护的自托管媒体整理项目，连接网盘资源与自己的媒体库。它支持媒体发现、网盘转存、STRM 生成和 Emby 同步，让分散的影片以海报和剧集的方式呈现。\n\n配合 SunnyTV，可通过 MediaIndex 提供的播放入口观看原画，并选择 115 已生成的 4K / 1080P 清晰度，无需 NAS 重新编码。可选清晰度取决于网盘文件和播放入口支持；普通 STRM 仍可按原地址播放。",
            color=SunnyColors.Secondary,fontSize=14.sp,lineHeight=24.sp)
    }
}

@Composable private fun ProjectHeading(name:String,icon:Int,id:String,url:String) {
    val compact=LocalCompact.current
    val uri=LocalUriHandler.current
    val model=LocalAppModel.current
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(if(compact) 10.dp else 14.dp)) {
        Image(painterResource(icon),"$name 项目标志",Modifier.size(if(compact) 38.dp else 48.dp))
        Text(name,color=SunnyColors.Text,fontSize=if(compact) 25.sp else 32.sp,fontWeight=FontWeight.Bold)
        FocusTile("about:$id",onClick={
            runCatching {uri.openUri(url)}.onFailure {model.message="请在浏览器访问 $url"}
        }) {
            Text("GitHub ↗",color=SunnyColors.Accent,fontSize=13.sp,
                modifier=Modifier.padding(horizontal=12.dp,vertical=10.dp))
        }
    }
}
