package io.github.xudong7587.sunnytv

import android.os.Bundle
import kotlinx.coroutines.launch
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
import androidx.compose.ui.focus.*
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.InputMode
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
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
    private var keyboardFallback:((Int)->Boolean)?=null
    override fun onKeyDown(keyCode:Int,event:android.view.KeyEvent):Boolean {
        // Hardware/remote MENU jumps straight to settings; the player is a separate activity.
        if(keyCode==android.view.KeyEvent.KEYCODE_MENU && event.action==android.view.KeyEvent.ACTION_DOWN) {
            model.openSettingsFromMenu()
            return true
        }
        if(keyboardFallback?.invoke(keyCode)==true) return true
        return super.onKeyDown(keyCode,event)
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        WindowInsetsControllerCompat(window,window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            val input=LocalInputModeManager.current
            val focus=LocalFocusManager.current
            val configuration=LocalConfiguration.current
            val handset=remember(configuration) {isHandset(configuration)}
            val touchFirst=remember(configuration) {isTouchFirst(configuration)}
            val portrait=isPortrait(configuration)
            val topInset=handsetTopInset(WindowInsets.displayCutout.asPaddingValues().calculateTopPadding(),handset,portrait)
            DisposableEffect(input,focus) {
                keyboardFallback={code->
                    val direction=when(code) {
                        android.view.KeyEvent.KEYCODE_DPAD_UP->FocusDirection.Up
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN->FocusDirection.Down
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT->FocusDirection.Left
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT->FocusDirection.Right
                        else->null
                    }
                    if(direction==null) false else {input.requestInputMode(InputMode.Keyboard);focus.moveFocus(direction)}
                }
                onDispose {keyboardFallback=null}
            }
            LaunchedEffect(model.settings.displayModePreference) {
                applyPreferredDisplayMode(window,model.settings.displayModePreference)
            }
            CompositionLocalProvider(LocalAppModel provides model,LocalHandset provides handset,
                LocalTouchFirst provides touchFirst,LocalTopInset provides topInset) {
                ScaledUi(model.settings) {SunnyTheme(model.settings) { SunnyRoot(onPlay={ entry,fromStart -> model.play(entry,fromStart) { request ->
                    startActivity(PlayerActivity.intent(this,request))
                } }) }}
            }
        }
    }
    override fun onRestart() { super.onRestart(); model.afterPlayback() }
    override fun onResume() {
        super.onResume()
        window.decorView.post {applyPreferredDisplayMode(window,model.settings.displayModePreference)}
    }
}

@Composable internal fun SunnyRoot(onPlay:(MediaEntry,Boolean)->Unit) {
    val model=LocalAppModel.current; val stateHolder=rememberSaveableStateHolder()
    val route=model.route
    BackHandler(enabled=route!=Route.Home || model.busy) { model.back() }
    val compact=LocalConfiguration.current.screenWidthDp<600
    val bridge=remember(route) {NavigationBridge()}
    // dev22 acceptance: the library and detail pages keep the pinned navigation and brand again.
    val navVisible=true
    val scope=rememberCoroutineScope()
    val focus=LocalFocusManager.current
    val inputMotion=remember(route) {TvFocusMotion()}
    val motion=LocalMotion.current
    CompositionLocalProvider(LocalPageKey provides route.key(),LocalCompact provides compact,LocalNavigationBridge provides bridge,
        LocalNavVisible provides navVisible,LocalTvFocusMotion provides inputMotion) {
        Box(Modifier.fillMaxSize().background(SunnyColors.Background)
            .onPreviewKeyEvent {inputMotion.record(it);false}
            .onKeyEvent {event->
                // Bubble phase at the root so it also applies while the pinned navigation holds
                // focus (that bar is a sibling of the page content, not a child of it).
                if(event.type==KeyEventType.KeyDown && event.key==Key.DirectionUp) {
                    // The pinned navigation IS the top of the page: pressing Up there refreshes the
                    // carousel. Move-focus is not consulted first, because it may pick another tile
                    // in the same row and swallow the key.
                    when {
                        bridge.navActive -> model.refreshTopCarousel()
                        focus.moveFocus(FocusDirection.Up) -> Unit
                        navVisible -> scope.launch {bridge.revealTop?.invoke();bridge.enterNavigation()}
                        else -> model.refreshTopCarousel()
                    }
                    true
                } else false
            }) {
            Box(Modifier.fillMaxSize().onPreviewKeyEvent {event->
                if(event.key==Key.Back || event.key==Key.Escape) {
                    if(event.type==KeyEventType.KeyUp) model.back()
                    true
                } else if(event.key==Key.Menu && event.type==KeyEventType.KeyDown) {
                    // Remote MENU reaches settings directly on every page except the player.
                    model.openSettingsFromMenu()
                    true
                } else false
            }) {
                AnimatedContent(route,modifier=Modifier.fillMaxSize(),contentKey={it.key()},transitionSpec={
                    (fadeIn(motion.fade(400)) togetherWith fadeOut(motion.fade(400))).using(null)
                },label="page-transition") {visibleRoute->
                    val visibleBridge=remember(visibleRoute) {if(visibleRoute==route) bridge else NavigationBridge()}
                    CompositionLocalProvider(LocalPageKey provides visibleRoute.key(),LocalPageActive provides (visibleRoute==route),
                        LocalNavigationBridge provides visibleBridge,LocalNavVisible provides true) {
                    StableVerticalViewport {
                    stateHolder.SaveableStateProvider(visibleRoute.key()) {
                        when(visibleRoute) {
                            Route.Home -> HomeScreen(onPlay)
                            Route.Libraries -> LibrariesScreen()
                            is Route.Library -> LibraryScreen(visibleRoute.item,onPlay)
                            is Route.Detail -> DetailScreen(visibleRoute.item,onPlay)
                            Route.Cloud -> CloudScreen(onPlay)
                            is Route.Folder -> FolderScreen(visibleRoute,onPlay)
                            Route.Settings -> SettingsScreen()
                            Route.Search -> SearchScreen(onPlay)
                        }
                    }
                    }
                    }
                }
            }
            AnimatedVisibility(navVisible,enter=fadeIn(motion.fade(300)),exit=fadeOut(motion.fade(300))) {
                CompositionLocalProvider(LocalPageActive provides navVisible) {TopNav(bridge)}
            }
            if(model.sourcesReady && model.sources.none {it.kind==SourceKind.EMBY}) {
                AddSourceDialog(SourceKind.EMBY,onClose={},onConnected={model.navigate(Route.Home,root=true)},firstConnection=true)
            }
            if(model.busy) Text("正在连接媒体…  返回可取消",color=SunnyColors.Accent,fontSize=14.sp,
                modifier=Modifier.align(Alignment.BottomCenter).padding(18.dp).background(SunnyColors.Surface).padding(15.dp))
            if(model.message.isNotEmpty()) MessageDialog(model.message) { model.message="" }
            model.actionTarget?.let {target->
                ItemActionSheet(target,model.itemActions(target),onRun={model.runAction(it,target)},
                    onDelete={model.requestDelete(target)},onDismiss={model.dismissItemActions()})
            }
            model.pendingDelete?.let {target->
                ItemDeleteConfirm(target,onConfirm={model.confirmDelete(target)},onDismiss={model.cancelDelete()})
            }
        }
    }
}

@Composable private fun TopNav(bridge:NavigationBridge) {
    val model=LocalAppModel.current
    val activeRoot = when(model.route) {
        is Route.Library, is Route.Detail -> Route.Libraries
        is Route.Folder -> Route.Cloud
        else -> model.route
    }
    val compact=LocalCompact.current
    val topInset=LocalTopInset.current
    val scope=rememberCoroutineScope()
    // "Up" from page content returns to the tile of the page the user is on, not always 首页.
    LaunchedEffect(bridge,activeRoot) {
        bridge.navEntryId=when(activeRoot) {
            Route.Home -> "nav:首页"
            Route.Libraries -> "nav:媒体库"
            Route.Search -> "nav:搜索"
            Route.Settings -> "nav:设置"
            else -> null
        }
    }
    Row(Modifier.fillMaxWidth().padding(top=topInset).height(68.dp).padding(horizontal=if(compact) 12.dp else 28.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        // Only the wordmark carries the light-theme elevation. A shadow around the round logo mark
        // reads as a circular button, so the logo itself stays flat.
        val brandShadow=model.settings.shadowsEnabled && !model.settings.darkTheme
        Row(Modifier.padding(start=if(compact) 6.dp else 10.dp,end=8.dp,top=13.dp,bottom=13.dp),
            verticalAlignment=Alignment.CenterVertically) {
            val iconSize=if(compact) 30.dp else 36.dp
            // No shadow on the sun mark: any shadow behind a round glyph reads as a circular button.
            Image(painterResource(if(model.settings.darkTheme) R.drawable.ic_sun_brand else R.drawable.ic_sun_brand_light),
                "SunnyTV",Modifier.size(iconSize))
            if(!compact) Text("  SunnyTV",color=SunnyColors.Text,fontSize=19.sp,fontWeight=FontWeight.Bold,
                style=androidx.tv.material3.LocalTextStyle.current.copy(shadow=if(brandShadow)
                    androidx.compose.ui.graphics.Shadow(color=androidx.compose.ui.graphics.Color(0xFF3F3B36).copy(alpha=.38f),
                        offset=androidx.compose.ui.geometry.Offset(0f,2.4f),blurRadius=7f) else null))
        }
        Row(Modifier.cinemaGlass().padding(4.dp).onFocusChanged {focusState->
            bridge.navActive=focusState.hasFocus
            if(focusState.hasFocus) scope.launch {bridge.revealTop?.invoke()}
        }.onPreviewKeyEvent {
            if(it.type==KeyEventType.KeyDown && it.key==Key.DirectionDown) {scope.launch {bridge.revealTop?.invoke();withFrameNanos {};bridge.enterContent()};true} else false
        }.focusGroup(),horizontalArrangement=Arrangement.spacedBy(if(compact) 0.dp else 4.dp)) {
            listOf("首页" to Route.Home,"媒体库" to Route.Libraries,"搜索" to Route.Search,"设置" to Route.Settings).forEach { (label,target) ->
                FocusTile("nav:$label",Modifier.semantics {contentDescription=label},active=activeRoot==target,autoFocus=model.route==Route.Home && target==Route.Home,shape=RoundedCornerShape(50.dp),button=true,
                    focusLift=false,onClick={model.navigate(target,root=true)}) { focused ->
                    Row(Modifier.height(44.dp).widthIn(min=44.dp).padding(horizontal=10.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                        val ink=if(activeRoot==target || focused) SunnyColors.Accent else SunnyColors.Secondary
                        LineIcon(actionIcon(label),ink)
                        val motion=LocalMotion.current
                        AnimatedVisibility(focused,enter=expandHorizontally(motion.spring())+fadeIn(motion.fade(240)),
                            exit=shrinkHorizontally(motion.spring())+fadeOut(motion.fade(240))) {
                            Text(label,color=ink,fontSize=12.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(start=7.dp))
                        }
                    }
                }
            }
        }
        if(!compact) Spacer(Modifier.width(100.dp))
    }
}
