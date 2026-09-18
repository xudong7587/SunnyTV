package io.github.xudong7587.sunnytv.feature.player

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.Display
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
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import io.github.xudong7587.sunnytv.SunnyApp
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.ui.*
import kotlinx.coroutines.*
import io.github.xudong7587.sunnytv.core.playback.PlaybackReporter
import io.github.xudong7587.sunnytv.core.playback.StartupTiming
import io.github.xudong7587.sunnytv.core.playback.PlaybackFailure
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
    private var mp4EditListFallbackUsed=false
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
    private var mediaContext by mutableStateOf<PlayerMediaContext?>(null)
    private var dismissedSegments by mutableStateOf<Set<String>>(emptySet())
    private var metadataJob:Job?=null
    private var sleepJob:Job?=null
    private var sleepMinutes by mutableStateOf<Int?>(null)
    private var switchingEpisode by mutableStateOf(false)
    private var nextUpDismissed by mutableStateOf(false)
    private var playbackSpeed by mutableFloatStateOf(1f)
    private var tvPlayback = false
    private var returnControl by mutableStateOf("transport")
    private val settings get()=app.store.settings()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val payload=intent.getSerializableExtra("request") as? PlaybackRequest
        if(payload==null) {finish();return}
        request=payload
        val config=resources.configuration
        @Suppress("DEPRECATION")
        val playbackDisplayId=windowManager.defaultDisplay.displayId
        tvPlayback=(config.uiMode and Configuration.UI_MODE_TYPE_MASK)==Configuration.UI_MODE_TYPE_TELEVISION ||
            config.smallestScreenWidthDp>=600 || playbackDisplayId!=Display.DEFAULT_DISPLAY
        requestedOrientation=playerOrientation(tvPlayback)
        playbackSpeed=savedInstanceState?.getFloat("speed",1f) ?: 1f
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
    override fun onSaveInstanceState(outState: Bundle) {outState.putLong("position",player?.currentPosition ?: lastPosition);outState.putFloat("speed",playbackSpeed);super.onSaveInstanceState(outState)}
    override fun onStop() {
        player?.let {p ->
            lastPosition=p.currentPosition; app.store.savePosition(request.localKey,if(p.playbackState==Player.STATE_ENDED) 0 else lastPosition)
            resumePlayWhenReady = p.playWhenReady
            reporter?.close(lastPosition, rendered)
            p.release()
        }
        player=null; rendered=false; progressJob?.cancel(); reporter=null
        metadataJob?.cancel();metadataJob=null
        sleepJob?.cancel();sleepJob=null
        super.onStop()
    }
    private fun createPlayer(ignoreMp4EditLists:Boolean=mp4EditListFallbackUsed) {
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
        val extractors=DefaultExtractorsFactory().apply {
            if(ignoreMp4EditLists) setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
        }
        val sourceFactory=DefaultMediaSourceFactory(dataSource,extractors)
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
        p.setPlaybackSpeed(playbackSpeed)
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
            override fun onVideoSizeChanged(videoSize:VideoSize) {
                if(!tvPlayback && videoSize.width>0 && videoSize.height>0) {
                    requestedOrientation=playerOrientation(false,videoSize.width,videoSize.height,videoSize.pixelWidthHeightRatio)
                }
            }
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
                if(!rendered && !mp4EditListFallbackUsed && PlaybackFailure.isMp4IndexFailure(e,request.mimeHint)) {
                    mp4EditListFallbackUsed=true
                    status="正在使用 MP4 兼容模式重试…"
                    error=""
                    lastPosition=p.currentPosition.coerceAtLeast(0)
                    resumePlayWhenReady=p.playWhenReady
                    progressJob?.cancel();progressJob=null
                    reporter=null
                    player=null
                    app.store.savePlaybackDiagnostic("检测到 MP4 索引异常，已启用一次忽略 edit list 的兼容重试。\n错误码 ${e.errorCode} · 首帧前读取 · video/mp4")
                    window.decorView.post {
                        p.release()
                        if(!isFinishing && !isDestroyed && player==null) createPlayer(true)
                    }
                    return
                }
                error=PlaybackFailure.describe(e,if(rendered) "播放读取" else "首帧前读取",request.mimeHint)
                app.store.savePlaybackDiagnostic(error)
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
        loadPlayerMetadata()
        p.setMediaItem(media.build(),lastPosition);p.prepare();p.playWhenReady=resumePlayWhenReady
        progressJob=lifecycleScope.launch {
            var tick=0
            while(isActive) {
                delay(500)
                // Segment prompts also need position while the OSD is hidden.
                position=p.currentPosition;duration=p.duration.coerceAtLeast(0)
                tick++
                if(tick%20==0 && rendered) {
                    reporter?.progress(p.currentPosition, !p.isPlaying)
                    app.store.savePosition(request.localKey,p.currentPosition)
                }
            }
        }
    }
    private fun loadPlayerMetadata() {
        if(request.embyItemId.isBlank() || mediaContext?.item?.id==request.embyItemId) return
        metadataJob?.cancel()
        metadataJob=lifecycleScope.launch {
            val context=withContext(Dispatchers.IO) {
                val config=runCatching {app.store.sources().firstOrNull {it.id==request.sourceId}}.getOrNull()
                    ?: return@withContext null
                runCatching {app.emby(config).playerContext(request.embyItemId)}.getOrNull()
            }
            if(context!=null) mediaContext=context
        }
    }
    private fun switchEpisode(item:MediaEntry) {
        if(switchingEpisode) return
        lifecycleScope.launch {
            switchingEpisode=true
            try {
                val started=SystemClock.elapsedRealtime()
                val next=withContext(Dispatchers.IO) {
                    val config=app.store.sources().firstOrNull {it.id==request.sourceId} ?: error("source")
                    app.emby(config).playback(item,false)
                }.copy(requestedAtMs=started,sourceReadyAtMs=SystemClock.elapsedRealtime())
                startActivity(intent(this@PlayerActivity,next));finish()
            } catch(_:CancellationException) {throw CancellationException()}
            catch(_:Exception) {error="无法打开相邻剧集，请返回详情页重试。";controls=true}
            finally {switchingEpisode=false}
        }
    }
    private fun setSleepTimer(minutes:Int?) {
        sleepJob?.cancel();sleepJob=null;sleepMinutes=minutes
        if(minutes!=null) sleepJob=lifecycleScope.launch {delay(minutes*60_000L);finish()}
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
        val context=mediaContext
        val activeSkip=SegmentLogic.active(context?.skipSegments.orEmpty(),position,dismissedSegments)
        val remainingMs=(duration-position).coerceAtLeast(0)
        val showNextUp=context?.next!=null && !nextUpDismissed && rendered && duration>0 &&
            remainingMs in 1..90_000 && activeSkip==null
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory={androidContext->PlayerView(androidContext).apply {
                useController=false;keepScreenOn=true
                isFocusable=false
                descendantFocusability=android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }},update={view->view.player=player;view.resizeMode=resizeMode},modifier=Modifier.fillMaxSize())

            if(panel.isBlank()) PlayerTouchSurface(
                onTap={controls=!controls},
                onDoubleTap={region->
                    player?.let {p->
                        if(region==1) {
                            if(p.isPlaying) {p.pause();gestureNotice="已暂停"} else {p.play();gestureNotice="继续播放"}
                        } else if(p.isCurrentMediaItemSeekable && p.duration>0) {
                            seek(if(region==0) -30_000 else 30_000)
                            gestureNotice=if(region==0) "后退 30 秒" else "前进 30 秒"
                        } else gestureNotice="当前媒体暂不支持快进"
                    }
                },
                onStart={
                    gestureActive=true;gestureSeekTarget=null
                    gestureStartPosition=player?.currentPosition ?: 0
                    gestureStartBrightness=window.attributes.screenBrightness.takeIf {it>=0}
                        ?: (Settings.System.getInt(contentResolver,Settings.System.SCREEN_BRIGHTNESS,128)/255f)
                    gestureStartVolume=audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                },
                onDrag={region,dx,dy->
                    when(region) {
                        0 -> {
                            val level=(gestureStartBrightness+dy).coerceIn(.01f,1f)
                            window.attributes=window.attributes.apply {screenBrightness=level}
                            gestureNotice="亮度 ${(level*100).toInt()}%"
                        }
                        2 -> {
                            val max=audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                            val level=(gestureStartVolume+dy*max).toInt().coerceIn(0,max)
                            if(!audioManager.isVolumeFixed) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,level,0)
                            gestureNotice=if(audioManager.isVolumeFixed) "此设备音量固定" else "音量 ${level*100/max}%"
                        }
                        else -> player?.let {p->
                            if(p.isCurrentMediaItemSeekable && p.duration>0) {
                                gestureSeekTarget=PlayerGesturePolicy.seekTarget(gestureStartPosition,dx,p.duration)
                                gestureNotice="跳转至 ${clock(gestureSeekTarget!!)} / ${clock(p.duration)}"
                            } else gestureNotice="当前媒体暂不支持快进"
                        }
                    }
                },
                onEnd={commit->
                    if(commit) gestureSeekTarget?.let {target->player?.seekTo(target);position=target}
                    gestureSeekTarget=null;gestureActive=false
                })

            LaunchedEffect(gestureNotice,gestureActive) {
                if(!gestureActive && gestureNotice.isNotBlank()) {delay(1200);gestureNotice=""}
            }
            if(gestureNotice.isNotBlank()) Text(gestureNotice,color=Color.White,fontSize=20.sp,
                modifier=Modifier.align(Alignment.Center).background(Color.Black.copy(.72f),RoundedCornerShape(12.dp)).padding(18.dp))

            SunnyLoadingOverlay(error.isBlank() && (switchingEpisode || !rendered || status=="正在缓冲…"))

            if(error.isNotBlank()) {
                Column(Modifier.align(Alignment.Center).widthIn(max=650.dp).background(Color.Black.copy(.82f),RoundedCornerShape(18.dp)).padding(24.dp),
                    verticalArrangement=Arrangement.spacedBy(14.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(error,color=Color.White,fontSize=15.sp,lineHeight=23.sp)
                    if(!retryUsed) PlayerControl("重试此播放入口一次","repeat",initial=true) {
                        retryUsed=true;lastPosition=player?.currentPosition ?: lastPosition
                        player?.release();player=null;progressJob?.cancel()
                        reporter?.close(lastPosition,rendered);reporter=null;createPlayer()
                    }
                    PlayerControl("退出播放","exit",initial=retryUsed) {finish()}
                }
            }

            if(settings.diagnostics) Text("点击至首帧 ${millis(totalStartupMs)}  ·  源解析 ${millis(sourceStartupMs)}\n引擎首帧 ${millis(firstFrameMs)}  ·  首个响应头 ${millis(headerMs)}  ·  回报失败 $reportFailures\n${request.playMethod} · Media3",
                color=SunnyColors.Accent,fontSize=12.sp,modifier=Modifier.align(Alignment.TopEnd).padding(25.dp).background(Color.Black.copy(.62f),RoundedCornerShape(10.dp)).padding(12.dp))

            if(activeSkip!=null && rendered && error.isBlank() && panel.isBlank()) {
                SkipSegmentPrompt(activeSkip,onSkip={
                    player?.seekTo(activeSkip.endMs.coerceAtMost(duration.takeIf {it>0} ?: activeSkip.endMs))
                    dismissedSegments=dismissedSegments+activeSkip.id
                },onDismiss={dismissedSegments=dismissedSegments+activeSkip.id},
                    modifier=Modifier.align(Alignment.BottomEnd).padding(end=40.dp,bottom=if(controls) 190.dp else 42.dp))
            } else if(showNextUp && panel.isBlank() && error.isBlank()) {
                val next=context?.next
                if(next!=null) NextEpisodePrompt(next.title,(remainingMs/1000).coerceAtLeast(1),
                    onPlay={switchEpisode(next)},onDismiss={nextUpDismissed=true},
                    modifier=Modifier.align(Alignment.BottomEnd).widthIn(max=340.dp)
                        .padding(end=40.dp,bottom=if(controls) 190.dp else 42.dp))
            }

            if(controls && panel.isBlank() && error.isBlank()) {
                Box(Modifier.align(Alignment.TopStart).padding(start=40.dp,top=30.dp).width(300.dp).height(82.dp)) {PlayerMediaTitle()}
                LaunchedEffect(controls,playing,panel,error,gestureActive) {
                    if(playing && panel.isEmpty() && error.isEmpty() && !gestureActive) {delay(6000);controls=false}
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(.97f))))
                    .padding(start=18.dp,end=18.dp,top=44.dp,bottom=10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    if(selectionNotice.isNotBlank()) Text(selectionNotice,color=Color.White.copy(.7f),fontSize=12.sp)
                    PlayerProgress(position,duration,settings.seekStepSeconds*1000L) {seek(it)}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(clock(position),color=Color.White.copy(.72f),fontSize=12.sp)
                        Text(clock(duration),color=Color.White.copy(.72f),fontSize=12.sp)
                    }
                    Row(if(tvPlayback) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                        Row(horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically) {
                            context?.previous?.let {previous->PlayerControl("上一集","previous") {switchEpisode(previous)}}
                            PlayerControl("后退 ${settings.seekStepSeconds} 秒","rewind") {seek(-settings.seekStepSeconds*1000L)}
                            PlayerControl(if(playing) "暂停" else "播放",if(playing) "pause" else "play",initial=returnControl=="transport") {
                                player?.let {if(it.isPlaying) it.pause() else it.play()}
                            }
                            PlayerControl("前进 ${settings.seekStepSeconds} 秒","forward") {seek(settings.seekStepSeconds*1000L)}
                            context?.next?.let {next->PlayerControl("下一集","next") {switchEpisode(next)}}
                        }
                        Spacer(if(tvPlayback) Modifier.weight(1f) else Modifier.width(12.dp))
                        Row(horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically) {
                            PlayerControl("倍速 ${playbackSpeed}x","speed",initial=returnControl=="speed") {returnControl="speed";panel="speed"}
                            if(context?.item?.chapters?.isNotEmpty()==true) PlayerControl("章节","chapters",initial=returnControl=="chapters") {returnControl="chapters";panel="chapters"}
                            if(availableTracks.groups.any {it.type==C.TRACK_TYPE_TEXT} || request.subtitles.isNotEmpty()) PlayerControl("字幕","subtitle",initial=returnControl=="subtitle") {returnControl="subtitle";panel="subtitles"}
                            if(availableTracks.groups.any {it.type==C.TRACK_TYPE_AUDIO}) PlayerControl("音轨","audio",initial=returnControl=="audio") {returnControl="audio";panel="audio"}
                            if(context?.item?.people?.isNotEmpty()==true) PlayerControl("演职员","cast",initial=returnControl=="cast") {returnControl="cast";panel="cast"}
                            PlayerControl("画面："+when(resizeMode) {AspectRatioFrameLayout.RESIZE_MODE_ZOOM->"裁切";AspectRatioFrameLayout.RESIZE_MODE_FILL->"拉伸";else->"适应"},"frame") {
                                resizeMode=when(resizeMode) {AspectRatioFrameLayout.RESIZE_MODE_FIT->AspectRatioFrameLayout.RESIZE_MODE_ZOOM;AspectRatioFrameLayout.RESIZE_MODE_ZOOM->AspectRatioFrameLayout.RESIZE_MODE_FILL;else->AspectRatioFrameLayout.RESIZE_MODE_FIT}
                            }
                            PlayerControl(sleepMinutes?.let {"睡眠 $it 分钟"} ?: "睡眠定时","sleep",initial=returnControl=="sleep") {returnControl="sleep";panel="sleep"}
                            PlayerControl("播放信息","info",initial=returnControl=="info") {returnControl="info";panel="info"}
                            PlayerControl("退出播放","exit") {finish()}
                        }
                    }
                }
            }
            if(panel.isNotBlank()) PlayerPanel()
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
    @Composable private fun PlayerPanel() {
        when(panel) {
            "audio","subtitles" -> TrackPanel()
            "chapters" -> ChapterPanel()
            "cast" -> CastPanel()
            "sleep" -> SleepPanel()
            "speed" -> SpeedPanel()
            "info" -> InfoPanel()
        }
    }
    @Composable private fun SpeedPanel() {
        PlayerSheet("播放倍速",{panel=""}) {
            listOf(1f,1.25f,1.5f,2f).forEach {speed->
                PlayerOption(if(speed==1f) "1x" else if(speed==2f) "2x" else "${speed}x",selected=playbackSpeed==speed) {
                    playbackSpeed=speed
                    player?.setPlaybackSpeed(speed)
                    panel=""
                }
            }
        }
    }
    @Composable private fun TrackPanel() {
        val type=if(panel=="audio") C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
        val groups=availableTracks.groups.filter {it.type==type}
        PlayerSheet(if(type==C.TRACK_TYPE_AUDIO) "音轨" else "字幕",{panel=""}) {
            if(type==C.TRACK_TYPE_TEXT) PlayerOption("关闭字幕",selected=player?.trackSelectionParameters?.disabledTrackTypes?.contains(type)==true) {
                subtitleManuallySelected=true
                player?.let {it.trackSelectionParameters=it.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type,true).build()}
                panel=""
            }
            if(groups.isEmpty()) Text("此媒体没有可选择的轨道",color=Color.White.copy(.7f),fontSize=14.sp)
            LazyColumn(Modifier.heightIn(max=500.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                groups.forEach {group->
                    for(i in 0 until group.length) {
                        val format=group.getTrackFormat(i)
                        if(group.isTrackSupported(i)) item {
                            PlayerOption(listOfNotNull(format.label,format.language,format.sampleMimeType).distinct().joinToString(" · ").ifBlank {"轨道 ${i+1}"},
                                selected=group.isTrackSelected(i) && player?.trackSelectionParameters?.disabledTrackTypes?.contains(type)!=true) {
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
    @Composable private fun ChapterPanel() {
        val chapters=mediaContext?.item?.chapters.orEmpty().filter {it.startMs>=0}
        PlayerSheet("章节",{panel=""}) {
            if(chapters.isEmpty()) Text("此媒体没有章节",color=Color.White.copy(.7f))
            LazyColumn(Modifier.heightIn(max=500.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(chapters.size) {index->
                    val chapter=chapters[index]
                    PlayerOption("${clock(chapter.startMs)}  ·  ${chapter.name}") {
                        player?.seekTo(chapter.startMs);position=chapter.startMs;panel=""
                    }
                }
            }
        }
    }
    @Composable private fun CastPanel() {
        val people=mediaContext?.item?.people.orEmpty()
        PlayerSheet("演职员",{panel=""}) {
            if(people.isEmpty()) Text("没有可显示的演职员信息",color=Color.White.copy(.7f))
            LazyColumn(Modifier.heightIn(max=500.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(people.size) {index->
                    val person=people[index]
                    PlayerOption(listOf(person.name,person.role).filter {it.isNotBlank()}.joinToString(" · ")) {}
                }
            }
        }
    }
    @Composable private fun SleepPanel() {
        PlayerSheet("睡眠定时",{panel=""}) {
            PlayerOption("关闭睡眠定时",selected=sleepMinutes==null) {setSleepTimer(null);panel=""}
            listOf(15,30,60,90).forEach {minutes->
                PlayerOption("$minutes 分钟",selected=sleepMinutes==minutes) {setSleepTimer(minutes);panel=""}
            }
        }
    }
    @Composable private fun InfoPanel() {
        val video=availableTracks.groups.firstOrNull {it.type==C.TRACK_TYPE_VIDEO}?.let {g->
            (0 until g.length).firstOrNull {g.isTrackSelected(it)}?.let {g.getTrackFormat(it)}
        }
        val audio=availableTracks.groups.firstOrNull {it.type==C.TRACK_TYPE_AUDIO}?.let {g->
            (0 until g.length).firstOrNull {g.isTrackSelected(it)}?.let {g.getTrackFormat(it)}
        }
        PlayerSheet("播放信息",{panel=""}) {
            Text("${request.playMethod} · ${request.mimeHint ?: "自动识别"}",color=Color.White,fontSize=17.sp)
            video?.let {Text("视频 · ${it.sampleMimeType ?: it.codecs ?: "未知"} · ${it.width}×${it.height}",color=Color.White.copy(.76f),fontSize=14.sp)}
            audio?.let {Text("音频 · ${it.sampleMimeType ?: it.codecs ?: "未知"} · ${it.channelCount} 声道",color=Color.White.copy(.76f),fontSize=14.sp)}
            Text("Media3 · 原画直放 / Direct Stream 优先\n本面板不提供码率/质量切换按钮。",color=Color.White.copy(.62f),fontSize=13.sp,lineHeight=21.sp)
            if(settings.diagnostics) Text("首帧 ${millis(firstFrameMs)} · 首响应头 ${millis(headerMs)}",color=SunnyColors.Accent,fontSize=12.sp)
        }
    }
    companion object {
        fun intent(context:Context,request:PlaybackRequest)=Intent(context,PlayerActivity::class.java).putExtra("request",request)
        private fun millis(ms:Long):String = if(ms < 0) "—" else "${ms}ms"
        private fun clock(ms:Long):String {val seconds=ms.coerceAtLeast(0)/1000;return if(seconds>=3600) "%d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60) else "%02d:%02d".format(seconds/60,seconds%60)}
    }
}
