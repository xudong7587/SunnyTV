package io.github.xudong7587.sunnytv.feature.player

import android.content.Context
import android.content.Intent
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
        setContent {SunnyTheme {PlayerContent()}}
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
        val media=androidx.media3.common.MediaItem.Builder().setUri(request.stableUrl)
            .setMediaId(request.localKey.ifBlank {"session"})
            .setMediaMetadata(MediaMetadata.Builder().setTitle(request.title).build())
        request.mimeHint?.let {media.setMimeType(it)}
        media.setSubtitleConfigurations(request.subtitles.map {sub ->
            androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                .setMimeType(sub.mime).setLanguage(sub.language).setLabel(sub.title).build()
        })
        p.addListener(object:Player.Listener {
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
    override fun dispatchKeyEvent(event:KeyEvent):Boolean {
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
        return super.dispatchKeyEvent(event)
    }
    @Composable private fun PlayerContent() {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory={context->PlayerView(context).apply {
                useController=false;keepScreenOn=true
                isFocusable=false
                descendantFocusability=android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }},
                update={view->view.player=player;view.resizeMode=resizeMode},modifier=Modifier.fillMaxSize())
            if(status.isNotBlank() || error.isNotBlank()) {
                Text(error.ifBlank {status},color=SunnyColors.Text,fontSize=16.sp,
                    modifier=Modifier.align(Alignment.Center).widthIn(max=650.dp).background(Color.Black.copy(.75f)).padding(20.dp))
            }
            if(settings.diagnostics) Text("点击至首帧 ${millis(totalStartupMs)}  ·  源解析 ${millis(sourceStartupMs)}\n引擎首帧 ${millis(firstFrameMs)}  ·  首个响应头 ${millis(headerMs)}  ·  回报失败 $reportFailures\n${request.playMethod} · Media3 · 恢复或重试仅统计本次引擎，未取得的指标显示 —",
                color=SunnyColors.Accent,fontSize=12.sp,modifier=Modifier.align(Alignment.TopStart).padding(25.dp).background(Color.Black.copy(.7f)).padding(12.dp))
            if(controls && panel.isBlank()) {
                LaunchedEffect(controls,playing,panel,error) {
                    if(playing && panel.isEmpty() && error.isEmpty()) {delay(6000);controls=false}
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(.95f))))
                    .padding(start=40.dp,end=40.dp,top=45.dp,bottom=27.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text(request.title,color=SunnyColors.Text,fontSize=25.sp,fontWeight=FontWeight.Bold)
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
    @Composable private fun TrackPanel() {
        val type=if(panel=="audio") C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
        val groups=player?.currentTracks?.groups?.filter {it.type==type} ?: emptyList()
        Box(Modifier.fillMaxSize().background(Color.Black.copy(.5f)),contentAlignment=Alignment.CenterEnd) {
            LazyColumn(Modifier.width(360.dp).fillMaxHeight().background(SunnyColors.Surface).padding(25.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item {Text(if(type==C.TRACK_TYPE_AUDIO) "选择音轨" else "选择字幕",color=SunnyColors.Text,fontSize=24.sp,fontWeight=FontWeight.Bold)}
                item {PlayerButton("关闭面板",true) {panel=""}}
                if(type==C.TRACK_TYPE_TEXT) item {PlayerButton("关闭字幕") {
                    player?.let {it.trackSelectionParameters=it.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type,true).build()};panel=""
                }}
                if(groups.isEmpty()) item {Text("此媒体没有可选择的轨道",color=SunnyColors.Secondary,fontSize=14.sp)}
                groups.forEach {group->
                    for(i in 0 until group.length) {
                        val format=group.getTrackFormat(i)
                        if(group.isTrackSupported(i)) item {
                            PlayerButton(listOfNotNull(format.label,format.language,format.sampleMimeType).distinct().joinToString(" · ").ifBlank {"轨道 ${i+1}"}) {
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
