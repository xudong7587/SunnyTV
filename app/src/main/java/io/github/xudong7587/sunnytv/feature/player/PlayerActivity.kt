package io.github.xudong7587.sunnytv.feature.player

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.*
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.*
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.SunnyApp
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.ui.*
import kotlinx.coroutines.*
import io.github.xudong7587.sunnytv.core.playback.PlaybackReporter
import io.github.xudong7587.sunnytv.core.playback.StartupTiming
import okhttp3.Interceptor
import java.util.concurrent.atomic.AtomicLong
import coil.compose.AsyncImage
import coil.request.ImageRequest

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity: ComponentActivity() {
    private val app get() = application as SunnyApp
    private lateinit var request: PlaybackRequest
    private var player by mutableStateOf<ExoPlayer?>(null)
    private var controls by mutableStateOf(true)
    private var panel by mutableStateOf("")
    private var position by mutableLongStateOf(0)
    private var duration by mutableLongStateOf(0)
    private var playing by mutableStateOf(false)
    private var status by mutableStateOf("正在准备媒体…")
    private var error by mutableStateOf("")
    private var firstFrameMs by mutableLongStateOf(-1)
    private var totalStartupMs by mutableLongStateOf(-1)
    private var sourceStartupMs by mutableLongStateOf(-1)
    private var originalLaunch = true
    private var headerMs by mutableLongStateOf(-1)
    private var rendered = false
    private var lastPosition=0L
    private var resizeMode by mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    private var retryUsed=false
    private var progressJob: Job?=null
    private var reporter: PlaybackReporter? = null
    private var resumePlayWhenReady = true
    private var reportFailures by mutableIntStateOf(0)
    private var gestureActive by mutableStateOf(false)
    private var gestureNotice by mutableStateOf("")
    private var gestureStartPosition = 0L
    private var gestureSeekTarget: Long? = null
    private var gestureStartBrightness = .5f
    private var gestureStartVolume = 0
    private val audioManager get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var selectionNotice by mutableStateOf("")
    private var availableTracks by mutableStateOf(Tracks.EMPTY)
    private var subtitleManuallySelected=false
    private val settings get()=app.store.settings()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val payload=intent.getSerializableExtra("request") as? PlaybackRequest
        if(payload==null) {finish();return}
        request=payload
        // Activity recreation and resume must not count time spent in the background.
        originalLaunch = savedInstanceState == null
        lastPosition=savedInstanceState?.getLong("position",request.startMs) ?: request.startMs
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        WindowInsetsControllerCompat(window,window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onBackPressedDispatcher.addCallback(this) {
            when {panel.isNotBlank()->panel=""; controls->controls=false; else->finish()}
        }
        setContent {ScaledUi(settings) {SunnyTheme(settings.copy(darkTheme=true)) {PlayerContent()}}}
    }
    override fun onStart() {super.onStart();if(::request.isInitialized && player==null) createPlayer()}
    override fun onSaveInstanceState(outState: Bundle) {outState.putLong("position",player?.currentPosition ?: lastPosition);super.onSaveInstanceState(outState)}
    override fun onStop() {
        player?.let {p ->
            lastPosition=p.currentPosition; app.store.savePosition(request.localKey,if(p.playbackState==Player.STATE_ENDED) 0 else lastPosition)
            resumePlayWhenReady = p.playWhenReady
            reporter?.close(lastPosition, rendered)
            p.release()
        }
        player=null; rendered=false; progressJob?.cancel(); reporter=null
        super.onStop()
    }
    private fun createPlayer() {
        rendered=false;error="";firstFrameMs=-1;headerMs=-1;totalStartupMs=-1;sourceStartupMs=-1
        val includeSourceTime = originalLaunch
        originalLaunch = false
        val started=SystemClock.elapsedRealtime()
        val firstHeader=AtomicLong(-1)
        val client=app.http.scopedClient(request.scope).newBuilder()
            .addNetworkInterceptor(Interceptor {chain ->
                val response=chain.proceed(chain.request())
                firstHeader.compareAndSet(-1,SystemClock.elapsedRealtime()-started)
                response
            }).build()
        val dataSource=OkHttpDataSource.Factory(client).setUserAgent(io.github.xudong7587.sunnytv.core.network.HttpPolicy.USER_AGENT)
        val sourceFactory=DefaultMediaSourceFactory(dataSource)
            // No unbounded engine/source/HTTP retries: one explicit retry button below.
            .setLoadErrorHandlingPolicy(object: androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(0) {
                override fun getRetryDelayMsFor(loadErrorInfo: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo): Long = C.TIME_UNSET
            })
        val p=ExoPlayer.Builder(this).setMediaSourceFactory(sourceFactory)
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(15_000,40_000,1_000,2_500).build())
            .setSeekBackIncrementMs(settings.seekStepSeconds*1000L).setSeekForwardIncrementMs(settings.seekStepSeconds*1000L)
            .build()
        p.setAudioAttributes(AudioAttributes.DEFAULT,true)
        p.setHandleAudioBecomingNoisy(true)
        player=p
        selectionNotice=""
        var audioPreferenceApplied=false
        var subtitlePreferenceApplied=false
        subtitleManuallySelected=false
        availableTracks=Tracks.EMPTY
        p.trackSelectionParameters=p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT,request.subtitlePreference=="none" && !request.explicitSubtitle)
            .build()
        val media=androidx.media3.common.MediaItem.Builder().setUri(request.stableUrl)
            .setMediaId(request.localKey.ifBlank {"session"})
            .setMediaMetadata(MediaMetadata.Builder().setTitle(request.title).build())
        request.mimeHint?.let {media.setMimeType(it)}
        media.setSubtitleConfigurations(request.subtitles.map {sub ->
            androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                .setMimeType(sub.mime).setLanguage(sub.language).setLabel(sub.title).setId(sub.id)
                .setSelectionFlags(if(sub.isDefault) C.SELECTION_FLAG_DEFAULT else 0).build()
        })
        p.addListener(object:Player.Listener {
            override fun onTracksChanged(tracks:Tracks) {
                availableTracks=tracks
                fun choose(type:Int,title:String,language:String):Boolean {
                    val options=tracks.groups.filter {it.type==type}.flatMap {group->
                        (0 until group.length).filter {group.isTrackSupported(it)}.map {group to it}
                    }
                    if(options.isEmpty()) return false
                    val match=options.firstOrNull {(g,i)->title.isNotBlank() && g.getTrackFormat(i).label.equals(title,true)}
                        ?: options.firstOrNull {(g,i)->
                            val f=g.getTrackFormat(i)
                            if(type==C.TRACK_TYPE_TEXT) Presentation.languageMatches(language,f.language.orEmpty(),f.label.orEmpty()) ||
                                (language !in setOf("zh-Hans","zh","en") && language.isNotBlank() && f.language.equals(language,true))
                            else language.isNotBlank() && f.language.equals(language,true)
                        }
                    if(match!=null) {
                        p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type,false)
                            .setOverrideForType(TrackSelectionOverride(match.first.mediaTrackGroup,match.second)).build()
                    } else {
                        if(type==C.TRACK_TYPE_TEXT) p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type,true).build()
                        selectionNotice=if(type==C.TRACK_TYPE_TEXT) "未找到所选字幕，可在字幕面板重新选择" else "未找到所选音轨，已使用默认音轨"
                    }
                    return true
                }
                if(!audioPreferenceApplied && (request.audioTitle.isNotBlank() || request.audioLanguage.isNotBlank())) {
                    audioPreferenceApplied=choose(C.TRACK_TYPE_AUDIO,request.audioTitle,request.audioLanguage)
                }
                if(!subtitleManuallySelected && !subtitlePreferenceApplied && (request.explicitSubtitle || request.subtitlePreference !in setOf("default","none"))) {
                    val options=tracks.groups.filter {it.type==C.TRACK_TYPE_TEXT}.flatMap {g->(0 until g.length).map {g to it}}
                    val selected=SubtitleSelection.choose(options.map {(g,i)->val f=g.getTrackFormat(i)
                        SubtitleSelection.Option(f.id.orEmpty(),f.label.orEmpty(),f.language.orEmpty(),g.isTrackSupported(i))},
                        request.subtitlePreference,request.explicitSubtitle,request.subtitleTrackId,request.subtitleTitle,request.subtitleOrdinal)
                    if(selected!=null) {
                        subtitlePreferenceApplied=true
                        val (group,index)=options[selected]
                        p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false)
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup,index)).build()
                        selectionNotice=""
                    } else if(request.explicitSubtitle && options.isNotEmpty()) {
                        selectionNotice="所选字幕暂不可用，可在字幕面板选择此媒体的其他实际字幕"
                    }
                    // No match for a library preference leaves the media/player default selection intact.
                }
            }
            override fun onIsPlayingChanged(isPlaying:Boolean) {
                playing=isPlaying
                if(rendered) reporter?.progress(p.currentPosition, !isPlaying)
            }
            override fun onPlaybackStateChanged(playbackState:Int) {
                status=when(playbackState) {Player.STATE_BUFFERING->"正在缓冲…";Player.STATE_ENDED->"播放结束";else->""}
                if(playbackState==Player.STATE_ENDED) {controls=true;app.store.savePosition(request.localKey,0)}
            }
            override fun onPlayerError(e:PlaybackException) {
                error=when(e.errorCode) {
                    PlaybackException.ERROR_CODE_DECODING_FAILED,PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "电视当前解码器无法播放此媒体。首版不会自动转码或切换高负载软解。"
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "媒体服务返回错误状态。请检查 Emby/CD2 权限或 MediaIndex 令牌。"
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "媒体连接失败或超时，请检查网络。"
                    else->"播放失败（错误码 ${e.errorCode}），请使用另一份样本检查兼容性。"
                }
                controls=true
            }
        })
        p.addAnalyticsListener(object:AnalyticsListener {
            override fun onRenderedFirstFrame(eventTime:AnalyticsListener.EventTime,output:Any,renderTimeMs:Long) {
                if(!rendered) {
                    rendered=true
                    val timing = StartupTiming.measure(
                        if (includeSourceTime) request.requestedAtMs else -1,
                        if (includeSourceTime) request.sourceReadyAtMs else -1,
                        started, SystemClock.elapsedRealtime()
                    )
                    firstFrameMs=timing.engineMs ?: -1
                    totalStartupMs=timing.totalMs ?: -1
                    sourceStartupMs=timing.sourceMs ?: -1
                    headerMs=firstHeader.get()
                    reporter?.start(p.currentPosition, !p.isPlaying)
                }
            }
        })
        startReporter()
        p.setMediaItem(media.build(),lastPosition);p.prepare();p.playWhenReady=resumePlayWhenReady
        progressJob=lifecycleScope.launch {
            var tick=0
            while(isActive) {
                delay(500)
                // Updating these states does not rebuild the Android PlayerView (factory is retained).
                if(controls || settings.diagnostics) {position=p.currentPosition;duration=p.duration.coerceAtLeast(0)}
                tick++
                if(tick%20==0 && rendered) {
                    reporter?.progress(p.currentPosition, !p.isPlaying)
                    app.store.savePosition(request.localKey,p.currentPosition)
                }
            }
        }
    }
    private fun startReporter() {
        if (request.embyItemId.isBlank()) return
        reporter = PlaybackReporter(request,
            source = {
                app.store.sources().firstOrNull { it.id == request.sourceId }?.let { app.emby(it) }
            },
            onFailure = { withContext(Dispatchers.Main) { reportFailures++ } }
        )
    }
    private fun seek(delta:Long) {player?.let {it.seekTo(MediaLogic.seek(it.currentPosition,delta,it.duration))};position=player?.currentPosition ?: position}
    override fun onKeyDown(keyCode:Int, event:KeyEvent):Boolean {
        if(event.action==KeyEvent.ACTION_DOWN) {
            when(event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE->{player?.let {if(it.isPlaying) it.pause() else it.play()};return true}
                KeyEvent.KEYCODE_MEDIA_PLAY->{player?.play();return true}
                KeyEvent.KEYCODE_MEDIA_PAUSE->{player?.pause();return true}
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD->{seek(settings.seekStepSeconds*1000L);return true}
                KeyEvent.KEYCODE_MEDIA_REWIND->{seek(-settings.seekStepSeconds*1000L);return true}
            }
            if(!controls && panel.isEmpty()) {
                when(event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT->{seek(-settings.seekStepSeconds*1000L);return true}
                    KeyEvent.KEYCODE_DPAD_RIGHT->{seek(settings.seekStepSeconds*1000L);return true}
                    KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN->{controls=true;return true}
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
    @Composable private fun PlayerContent() {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory={context->PlayerView(context).apply {
                useController=false;keepScreenOn=true
                isFocusable=false
                descendantFocusability=android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }},
                update={view->view.player=player;view.resizeMode=resizeMode},modifier=Modifier.fillMaxSize())
            if(panel.isBlank()) PlayerTouchSurface(
                onTap = { controls = !controls },
                onDoubleTap = { region ->
                    player?.let { p ->
                        if(region == 1) {
                            if(p.isPlaying) { p.pause(); gestureNotice="已暂停" }
                            else { p.play(); gestureNotice="继续播放" }
                        } else if(p.isCurrentMediaItemSeekable && p.duration > 0) {
                            seek(if(region == 0) -30_000 else 30_000)
                            gestureNotice=if(region == 0) "后退 30 秒" else "前进 30 秒"
                        } else gestureNotice="当前媒体暂不支持快进"
                    }
                },
                onStart = {
                    gestureActive=true
                    gestureSeekTarget=null
                    gestureStartPosition=player?.currentPosition ?: 0
                    gestureStartBrightness=window.attributes.screenBrightness.takeIf { it >= 0 }
                        ?: (Settings.System.getInt(contentResolver,Settings.System.SCREEN_BRIGHTNESS,128)/255f)
                    gestureStartVolume=audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                },
                onDrag = { region, dx, dy ->
                    when(region) {
                        0 -> {
                            val level=(gestureStartBrightness+dy).coerceIn(.01f,1f)
                            window.attributes=window.attributes.apply { screenBrightness=level }
                            gestureNotice="亮度 ${(level*100).toInt()}%"
                        }
                        2 -> {
                            val audio=audioManager
                            val max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                            val level=(gestureStartVolume+dy*max).toInt().coerceIn(0,max)
                            if(!audio.isVolumeFixed) audio.setStreamVolume(AudioManager.STREAM_MUSIC,level,0)
                            gestureNotice=if(audio.isVolumeFixed) "此设备音量固定" else "音量 ${level*100/max}%"
                        }
                        else -> player?.let { p ->
                            if(p.isCurrentMediaItemSeekable && p.duration > 0) {
                                gestureSeekTarget=PlayerGesturePolicy.seekTarget(gestureStartPosition,dx,p.duration)
                                gestureNotice="跳转至 ${clock(gestureSeekTarget!!)} / ${clock(p.duration)}"
                            } else gestureNotice="当前媒体暂不支持快进"
                        }
                    }
                },
                onEnd = { commit ->
                    if(commit) gestureSeekTarget?.let { target -> player?.seekTo(target); position=target }
                    gestureSeekTarget=null
                    gestureActive=false
                },
            )
            LaunchedEffect(gestureNotice,gestureActive) {
                if(!gestureActive && gestureNotice.isNotBlank()) { delay(1200); gestureNotice="" }
            }
            if(gestureNotice.isNotBlank()) Text(gestureNotice,color=Color.White,fontSize=20.sp,
                modifier=Modifier.align(Alignment.Center).background(Color.Black.copy(.75f),RoundedCornerShape(12.dp)).padding(20.dp))
            if(status.isNotBlank() || error.isNotBlank()) {
                Text(error.ifBlank {status},color=SunnyColors.Text,fontSize=16.sp,
                    modifier=Modifier.align(Alignment.Center).widthIn(max=650.dp).background(Color.Black.copy(.75f)).padding(20.dp))
            }
            if(settings.diagnostics) Text("点击至首帧 ${millis(totalStartupMs)}  ·  源解析 ${millis(sourceStartupMs)}\n引擎首帧 ${millis(firstFrameMs)}  ·  首个响应头 ${millis(headerMs)}  ·  回报失败 $reportFailures\n${request.playMethod} · Media3 · 恢复或重试仅统计本次引擎，未取得的指标显示 —",
                color=SunnyColors.Accent,fontSize=12.sp,modifier=Modifier.align(Alignment.TopStart).padding(25.dp).background(Color.Black.copy(.7f)).padding(12.dp))
            if(controls && panel.isBlank()) {
                Box(Modifier.align(Alignment.TopStart).padding(start=36.dp,top=26.dp).width(250.dp).height(70.dp)) {PlayerMediaTitle()}
                LaunchedEffect(controls,playing,panel,error,gestureActive) {
                    if(playing && panel.isEmpty() && error.isEmpty() && !gestureActive) {delay(6000);controls=false}
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(.95f))))
                    .padding(start=40.dp,end=40.dp,top=45.dp,bottom=27.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text(request.title,color=SunnyColors.Text,fontSize=25.sp,lineHeight=31.sp,maxLines=2,fontWeight=FontWeight.Bold)
                    if(selectionNotice.isNotBlank()) Text(selectionNotice,color=SunnyColors.Secondary,fontSize=12.sp)
                    Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(.22f))) {
                        Box(Modifier.fillMaxWidth(MediaLogic.progress(position,duration)).fillMaxHeight().background(SunnyColors.Accent))
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        PlayerButton(if(playing) "暂停" else "播放",true) {player?.let {if(it.isPlaying) it.pause() else it.play()}}
                        PlayerButton("−${settings.seekStepSeconds}秒") {seek(-settings.seekStepSeconds*1000L)}
                        PlayerButton("+${settings.seekStepSeconds}秒") {seek(settings.seekStepSeconds*1000L)}
                        Text("${clock(position)} / ${clock(duration)}",color=SunnyColors.Secondary,fontSize=13.sp,modifier=Modifier.weight(1f))
                        PlayerButton("音轨") {panel="audio"}
                        PlayerButton("字幕") {panel="subtitles"}
                        PlayerButton("画面") {resizeMode=when(resizeMode) {AspectRatioFrameLayout.RESIZE_MODE_FIT->AspectRatioFrameLayout.RESIZE_MODE_ZOOM;AspectRatioFrameLayout.RESIZE_MODE_ZOOM->AspectRatioFrameLayout.RESIZE_MODE_FILL;else->AspectRatioFrameLayout.RESIZE_MODE_FIT}}
                        PlayerButton("退出") {finish()}
                    }
                    if(error.isNotBlank() && !retryUsed) PlayerButton("重试此播放入口一次") {
                        retryUsed=true;lastPosition=player?.currentPosition ?: lastPosition
                        player?.release();player=null;progressJob?.cancel()
                        reporter?.close(lastPosition, rendered)
                        reporter=null; createPlayer()
                    }
                }
            }
            if(panel.isNotBlank()) TrackPanel()
        }
    }
    @Composable private fun PlayerMediaTitle() {
        val logo=request.mediaLogo
        val source by produceState<SourceConfig?>(null,request.sourceId) {
            value=withContext(Dispatchers.IO) {runCatching {app.store.sources().firstOrNull {it.id==request.sourceId}}.getOrNull()}
        }
        var failed by remember(logo) {mutableStateOf(false)}
        var loaded by remember(logo) {mutableStateOf(false)}
        if(!loaded || failed || logo==null || source==null) Text(request.title,color=SunnyColors.Text,fontSize=22.sp,lineHeight=27.sp,maxLines=2)
        val config=source
        if(logo!=null && config!=null && !failed) {
            val image=remember(logo,config.id) {ImageRequest.Builder(this).data(app.emby(config).imageUrl(logo,720))
                .size(720,240).crossfade(false).build()}
            AsyncImage(image,contentDescription=request.title,imageLoader=app.images(config),contentScale=ContentScale.Fit,
                alignment=Alignment.CenterStart,modifier=Modifier.fillMaxSize(),onSuccess={loaded=true},onError={failed=true})
        }
    }
    @Composable private fun TrackPanel() {
        val type=if(panel=="audio") C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
        val groups=availableTracks.groups.filter {it.type==type}
        Box(Modifier.fillMaxSize().background(Color.Black.copy(.5f)),contentAlignment=Alignment.CenterEnd) {
            LazyColumn(Modifier.width(360.dp).fillMaxHeight().background(SunnyColors.Surface).padding(25.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item {Text(if(type==C.TRACK_TYPE_AUDIO) "选择音轨" else "选择字幕",color=SunnyColors.Text,fontSize=24.sp,fontWeight=FontWeight.Bold)}
                item {PlayerButton("关闭面板",true) {panel=""}}
                if(type==C.TRACK_TYPE_TEXT) item {PlayerButton("关闭字幕") {
                    subtitleManuallySelected=true
                    player?.let {it.trackSelectionParameters=it.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type,true).build()};panel=""
                }}
                if(groups.isEmpty()) item {Text("此媒体没有可选择的轨道",color=SunnyColors.Secondary,fontSize=14.sp)}
                groups.forEach {group->
                    for(i in 0 until group.length) {
                        val format=group.getTrackFormat(i)
                        if(group.isTrackSupported(i)) item {
                            PlayerButton((if(group.isTrackSelected(i) && player?.trackSelectionParameters?.disabledTrackTypes?.contains(type)!=true) "✓ " else "")+
                                listOfNotNull(format.label,format.language,format.sampleMimeType).distinct().joinToString(" · ").ifBlank {"轨道 ${i+1}"}) {
                                if(type==C.TRACK_TYPE_TEXT) subtitleManuallySelected=true
                                player?.let {it.trackSelectionParameters=it.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(type,false).setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup,i)).build()}
                                panel=""
                            }
                        }
                    }
                }
            }
        }
    }
    @Composable private fun PlayerButton(text:String,initial:Boolean=false,onClick:()->Unit) {
        val requester=remember {FocusRequester()};var focused by remember {mutableStateOf(false)}
        LaunchedEffect(Unit) {if(initial) {delay(80);runCatching {requester.requestFocus()}}}
        Box(Modifier.focusRequester(requester).onFocusChanged {focused=it.isFocused}
            .background(if(focused) SunnyColors.Accent else SunnyColors.SurfaceRaised,RoundedCornerShape(9.dp))
            .clickable(onClick=onClick).padding(horizontal=14.dp,vertical=11.dp)) {
            Text(text,color=if(focused) SunnyColors.Background else SunnyColors.Text,fontSize=13.sp)
        }
    }
    companion object {
        fun intent(context:Context,request:PlaybackRequest)=Intent(context,PlayerActivity::class.java).putExtra("request",request)
        private fun millis(ms:Long):String = if(ms < 0) "—" else "${ms}ms"
        private fun clock(ms:Long):String {val seconds=ms.coerceAtLeast(0)/1000;return if(seconds>=3600) "%d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60) else "%02d:%02d".format(seconds/60,seconds%60)}
    }
}
