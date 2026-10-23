package io.github.xudong7587.sunnytv

import androidx.lifecycle.lifecycleScope
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
    private var startupUpdateJob:kotlinx.coroutines.Job?=null
    private var returningFromUpdateInstaller=false
    private var pendingAutomaticInstall:java.io.File?=null
    override fun onStart() {
        super.onStart()
        if(returningFromUpdateInstaller) {returningFromUpdateInstaller=false;return}
        if(io.github.xudong7587.sunnytv.core.storage.ConfigStore(this).githubAutomaticUpdates()) {
            startupUpdateJob=lifecycleScope.launch {
                try {
                    val ready=io.github.xudong7587.sunnytv.feature.update.prepareAutomaticGithubUpdate(
                        true,BuildConfig.VERSION_CODE.toLong(),
                        {io.github.xudong7587.sunnytv.feature.update.GithubUpdates.latest()},
                        {release->
                            android.widget.Toast.makeText(this@MainActivity,"发现新版 ${release.versionName}，正在下载更新",android.widget.Toast.LENGTH_LONG).show()
                            io.github.xudong7587.sunnytv.feature.update.GithubUpdates.download(this@MainActivity,release.apk)
                        })
                    if(ready!=null && io.github.xudong7587.sunnytv.core.storage.ConfigStore(this@MainActivity).githubAutomaticUpdates()) {
                        returningFromUpdateInstaller=true
                        if(android.os.Build.VERSION.SDK_INT>=26 && !packageManager.canRequestPackageInstalls()) pendingAutomaticInstall=ready
                        io.github.xudong7587.sunnytv.feature.update.ExperimentalUpdater(this@MainActivity).install(ready)
                    }
                } catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(_:Exception) { /* Offline startup remains usable; manual retry is in software updates. */ }
            }
            return
        }
        if(io.github.xudong7587.sunnytv.feature.update.UpdateChannels.showWebDav && io.github.xudong7587.sunnytv.core.storage.ConfigStore(this).let {it.webDavUpdatesEnabled() && it.automaticUpdates()}) {
            startupUpdateJob=lifecycleScope.launch {
                try {
                    val ready=io.github.xudong7587.sunnytv.feature.update.ExperimentalUpdater(this@MainActivity).download()
                    if(ready!=null) {
                        returningFromUpdateInstaller=true
                        try {io.github.xudong7587.sunnytv.feature.update.ExperimentalUpdater(this@MainActivity).install(ready)}
                        catch(_:Exception) {returningFromUpdateInstaller=false;android.widget.Toast.makeText(this@MainActivity,"无法打开安装器，请在软件升级中手动安装",android.widget.Toast.LENGTH_LONG).show()}
                    }
                } catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(_:Exception) { /* Optional startup checks remain quiet offline. */ }
            }
        }
    }
    override fun onStop() {startupUpdateJob?.cancel();startupUpdateJob=null;super.onStop()}
    override fun onRestart() { super.onRestart(); model.afterPlayback() }
    override fun onResume() {
        super.onResume()
        pendingAutomaticInstall?.let {file->
            if(android.os.Build.VERSION.SDK_INT<26 || packageManager.canRequestPackageInstalls()) {
                pendingAutomaticInstall=null
                if(io.github.xudong7587.sunnytv.core.storage.ConfigStore(this).githubAutomaticUpdates()) {
                    returningFromUpdateInstaller=true
                    runCatching {io.github.xudong7587.sunnytv.feature.update.ExperimentalUpdater(this).install(file)}
                }
            }
        }
        window.decorView.post {applyPreferredDisplayMode(window,model.settings.displayModePreference)}
    }
}

@Composable internal fun SunnyRoot(onExit:(()->Unit)?=null,onPlay:(MediaEntry,Boolean)->Unit) {
    val model=LocalAppModel.current; val stateHolder=rememberSaveableStateHolder()
    val route=model.route
    val activity=androidx.activity.compose.LocalActivity.current
    val exitPolicy=remember {BackExitPolicy()}
    var exitPrompt by remember {mutableStateOf(false)}
    var promptSerial by remember {mutableIntStateOf(0)}
    val handleBack:()->Unit={
        when(exitPolicy.press(android.os.SystemClock.elapsedRealtime(),model.canGoBack)) {
            BackExitPolicy.Action.BACK -> {exitPrompt=false;model.back()}
            BackExitPolicy.Action.PROMPT -> {exitPrompt=true;promptSerial++}
            BackExitPolicy.Action.EXIT -> {exitPrompt=false;(onExit ?: {activity?.finish()})()}
        }
    }
    LaunchedEffect(promptSerial,exitPrompt) {if(exitPrompt) {kotlinx.coroutines.delay(2_000);exitPrompt=false}}
    BackHandler {handleBack()}
    val compact=LocalConfiguration.current.screenWidthDp<600
    val bridge=remember(route) {NavigationBridge()}
    // dev22 acceptance: the library and detail pages keep the pinned navigation and brand again.
    val navVisible=true
    val glassBackdrop=rememberGlassBackdrop(AppearancePolicy.spec(LocalNativeAppearance.current.material).glass)
    val floatingGlassScene=remember(glassBackdrop) {glassBackdrop?.let {GlassButtonSceneState(it)}}
    val scope=rememberCoroutineScope()
    val focus=LocalFocusManager.current
    val inputMotion=remember(route) {TvFocusMotion()}
    val motion=LocalMotion.current
    CompositionLocalProvider(LocalPageKey provides route.key(),LocalCompact provides compact,LocalNavigationBridge provides bridge,
        LocalNavVisible provides navVisible,LocalTvFocusMotion provides inputMotion,LocalGlassButtonScene provides floatingGlassScene,
        LocalChoiceBackdrop provides glassBackdrop) {
        Box(Modifier.fillMaxSize().background(SunnyColors.Background)
            .onPreviewKeyEvent {event->
                inputMotion.record(event)
                if(event.key==Key.Back || event.key==Key.Escape) {
                    if(event.type==KeyEventType.KeyUp) handleBack()
                    true
                } else {
                    if(event.type==KeyEventType.KeyDown) {exitPolicy.reset();exitPrompt=false}
                    false
                }
            }
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
            Box(Modifier.fillMaxSize().captureGlassBackdrop(glassBackdrop).onPreviewKeyEvent {event->
                if(event.key==Key.Menu && event.type==KeyEventType.KeyDown) {
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
                    GlassButtonScene {
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
            }
            AnimatedVisibility(navVisible,enter=fadeIn(motion.fade(300)),exit=fadeOut(motion.fade(300))) {
                CompositionLocalProvider(LocalPageActive provides navVisible,LocalGlassBackdrop provides glassBackdrop,
                    LocalGlassControls provides (glassBackdrop!=null)) {TopNav(bridge)}
            }
            if(model.sourcesReady && model.sources.none {it.kind==SourceKind.EMBY}) {
                FirstSourceDialog(onConnected={model.navigate(Route.Home,root=true)})
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
            if(exitPrompt) Text("再按一次返回退出软件",color=androidx.compose.ui.graphics.Color.White,
                modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=36.dp)
                    .background(androidx.compose.ui.graphics.Color.Black.copy(.88f),RoundedCornerShape(16.dp))
                    .padding(horizontal=24.dp,vertical=14.dp))
        }
    }
}

@Composable internal fun TopNav(bridge:NavigationBridge) {
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
            if(it.type==KeyEventType.KeyDown && it.key==Key.DirectionDown) {scope.launch {bridge.revealTop?.invoke();withFrameNanos {};bridge.enterContent(fromNavigation=true)};true} else false
        }.focusGroup(),horizontalArrangement=Arrangement.spacedBy(if(compact) 0.dp else 4.dp)) {
            // A single optical shell owns refraction; its navigation tiles stay flat.
            CompositionLocalProvider(LocalGlassControls provides (LocalNativeAppearance.current.material in setOf("glass","frosted","aurora"))) {
            listOf("首页" to Route.Home,"媒体库" to Route.Libraries,"搜索" to Route.Search,"设置" to Route.Settings).forEach { (label,target) ->
                FocusTile("nav:$label",Modifier.semantics {contentDescription=label},active=activeRoot==target,autoFocus=model.route==Route.Home && target==Route.Home,shape=sunnyInsetShape(28.dp,4.dp),button=true,
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
        }
        if(!compact) Spacer(Modifier.width(100.dp))
    }
}

