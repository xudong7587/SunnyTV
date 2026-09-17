package io.github.xudong7587.sunnytv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.feature.*
import io.github.xudong7587.sunnytv.feature.ui.*
import io.github.xudong7587.sunnytv.feature.player.PlayerActivity
import io.github.xudong7587.sunnytv.core.model.*

class MainActivity: ComponentActivity() {
    private val model:AppModel by viewModels()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        WindowInsetsControllerCompat(window,window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            CompositionLocalProvider(LocalAppModel provides model) {
                SunnyTheme { SunnyRoot(onPlay={ entry,fromStart -> model.play(entry,fromStart) { request ->
                    startActivity(PlayerActivity.intent(this,request))
                } }) }
            }
        }
    }
    override fun onRestart() { super.onRestart(); model.afterPlayback() }
}

@Composable private fun SunnyRoot(onPlay:(MediaEntry,Boolean)->Unit) {
    val model=LocalAppModel.current; val stateHolder=rememberSaveableStateHolder()
    val route=model.route
    BackHandler(enabled=route!=Route.Home || model.busy) { model.back() }
    CompositionLocalProvider(LocalPageKey provides route.key()) {
        Box(Modifier.fillMaxSize().background(SunnyColors.Background)) {
            Column {
                TopNav()
                Box(Modifier.weight(1f)) {
                    stateHolder.SaveableStateProvider(route.key()) {
                        when(route) {
                            Route.Home -> HomeScreen(onPlay)
                            Route.Libraries -> LibrariesScreen()
                            is Route.Library -> LibraryScreen(route.item,onPlay)
                            is Route.Detail -> DetailScreen(route.item,onPlay)
                            Route.Cloud -> CloudScreen(onPlay)
                            is Route.Folder -> FolderScreen(route,onPlay)
                            Route.Settings -> SettingsScreen()
                            Route.Search -> SearchScreen(onPlay)
                        }
                    }
                }
            }
            if(model.busy) Text("正在连接媒体…  返回可取消",color=SunnyColors.Accent,fontSize=14.sp,
                modifier=Modifier.align(Alignment.BottomCenter).padding(18.dp).background(SunnyColors.Surface).padding(15.dp))
            if(model.message.isNotEmpty()) MessageDialog(model.message) { model.message="" }
        }
    }
}

@Composable private fun TopNav() {
    val model=LocalAppModel.current
    val activeRoot = when(model.route) {
        is Route.Library, is Route.Detail -> Route.Libraries
        is Route.Folder -> Route.Cloud
        else -> model.route
    }
    Row(Modifier.fillMaxWidth().height(78.dp).background(SunnyColors.Background).padding(horizontal=40.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("S",color=SunnyColors.Accent,fontSize=28.sp,fontWeight=FontWeight.Black)
            Text("  SunnyTV",color=SunnyColors.Text,fontSize=22.sp,fontWeight=FontWeight.Bold)
        }
        Row(Modifier.clip(RoundedCornerShape(50.dp))
            .background(SunnyColors.Surface.copy(alpha=.78f))
            .border(1.dp,SunnyColors.Border.copy(alpha=.65f),RoundedCornerShape(50.dp))
            .padding(5.dp).focusGroup(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            listOf("首页" to Route.Home,"媒体库" to Route.Libraries,"云盘" to Route.Cloud,"搜索" to Route.Search,"设置" to Route.Settings).forEach { (label,target) ->
                FocusTile("nav:$label",active=activeRoot==target,autoFocus=model.route==Route.Home && target==Route.Home,shape=RoundedCornerShape(50.dp),
                    onClick={model.navigate(target,root=true)}) { focused ->
                    Text(label,color=if(activeRoot==target || focused) SunnyColors.Accent else SunnyColors.Secondary,
                        fontSize=14.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(horizontal=16.dp,vertical=11.dp))
                }
            }
        }
        Text("PRIVATE CINEMA",color=SunnyColors.Secondary,fontSize=9.sp,letterSpacing=1.2.sp)
    }
}
