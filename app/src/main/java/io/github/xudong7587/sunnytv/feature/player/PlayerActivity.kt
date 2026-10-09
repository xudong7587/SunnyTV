package io.github.xudong7587.sunnytv.feature.player

import io.github.xudong7587.sunnytv.BuildConfig

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.annotation.SuppressLint
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
import androidx.compose.ui.focus.focusProperties
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
import io.github.xudong7587.sunnytv.core.playback.PlaybackRecovery
import okhttp3.Interceptor
import java.util.concurrent.atomic.AtomicLong
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.xudong7587.sunnytv.source.transcode.TranscodeSource

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
    private var autoRetryAttempts=0
    /** Direct play is attempted first; the Emby server route is the one-way fallback when it fails. */
    private var useFallbackRoute=false
    private var fallbackRouteUsed=false
    private var progressJob: Job?=null
    private var reporter: PlaybackReporter? = null
    private var reporterHasStarted = false
    private var resumePlayWhenReady = true
    private var reportFailures by mutableIntStateOf(0)
    private var transcodeSourceRequest: PlaybackRequest? = null
    private var cachedTranscodeSource: TranscodeSource? = null
    private val transcodeSource: TranscodeSource?
        get() {
            if(transcodeSourceRequest !== request) {
                cachedTranscodeSource=TranscodeSource.from(app.http,request.stableUrl,BuildConfig.MEDIAINDEX_TRANSCODE_TEST_ORIGIN)
                    ?: request.sourceMediaUrl?.let {TranscodeSource.from(app.http,it,BuildConfig.MEDIAINDEX_TRANSCODE_TEST_ORIGIN)}
                transcodeSourceRequest=request
            }
            return cachedTranscodeSource
        }
    private var quality by mutableStateOf("original")
    private var qualityProfiles by mutableStateOf<List<String>>(emptyList())
    private var qualityNotice by mutableStateOf("")
    private var bufferNotice by mutableStateOf("")
    private var clientNetworkNotice by mutableStateOf("")
    private var qualitySwitching by mutableStateOf(false)
    private var qualityJob: Job? = null
    private var transcodeSession: TranscodeSource.Session? = null
    private var transcodeOffset = 0L
    private var transcodeDuration = 0L
    private var transcodePausedPosition: Long? = null
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
    private var audioOutputMaximum by mutableIntStateOf(0)
    private var audioManuallySelected=false
    private var routeSwitchJob:Job?=null
    private var mediaContext by mutableStateOf<PlayerMediaContext?>(null)
    private var dismissedSegments by mutableStateOf<Set<String>>(emptySet())
    private var metadataJob:Job?=null
    private var sleepJob:Job?=null
    private var sleepMinutes by mutableStateOf<Int?>(null)
    private var switchingEpisode by mutableStateOf(false)
    private var nextUpDismissed by mutableStateOf(false)
    private var playbackSpeed by mutableFloatStateOf(1f)
    private var orientationLocked by mutableStateOf(false)
    private var manualOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private fun fixedOrientation():Int {
        @Suppress("DEPRECATION") val rotation=windowManager.defaultDisplay.rotation
        val landscape=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
        val naturalLandscape=if(rotation%2==0) landscape else !landscape
        return fixedPlayerOrientation(rotation,naturalLandscape)
    }
    private var tvPlayback = false
    private var returnControl by mutableStateOf("transport")
    private var controlInteractionSerial by mutableIntStateOf(0)
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
        tvPlayback=((config.uiMode and Configuration.UI_MODE_TYPE_MASK)==Configuration.UI_MODE_TYPE_TELEVISION ||
            playbackDisplayId!=Display.DEFAULT_DISPLAY)
        orientationLocked=savedInstanceState?.getBoolean("orientationLocked") ?: false
        manualOrientation=savedInstanceState?.getInt("manualOrientation") ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        requestedOrientation=if(orientationLocked) manualOrientation else playerOrientation(tvPlayback)
        playbackSpeed=savedInstanceState?.getFloat("speed",1f) ?: 1f
        // Activity recreation and resume must not count time spent in the background.
        originalLaunch = savedInstanceState == null
        lastPosition=savedInstanceState?.getLong("position",request.startMs) ?: request.startMs
        audioOutputMaximum=savedInstanceState?.getInt("audioOutputMaximum",0) ?: 0
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        WindowInsetsControllerCompat(window,window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onBackPressedDispatcher.addCallback(this) {
            when {panel.isNotBlank()->panel=""; controls->controls=false; else->finish()}
        }
        // Video, subtitles and the translucent OSD keep bright controls on a dark surface in daylight too.
        setContent {ScaledUi(settings) {SunnyTheme(settings.copy(darkTheme=true)) {PlayerContent()}}}
    }

    // Targeting suppression: overriding the framework Activity key dispatch is a normal app pattern;
    // lint's RestrictedApi check only guards androidx' own group prefix, not this app.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // The OSD timeout is an idle timeout, not a fixed lifetime. Every D-pad/button operation
        // while controls are visible restarts it, so controls never disappear mid-navigation.
        if(event.action==KeyEvent.ACTION_DOWN && controls && panel.isBlank() && error.isBlank()) {
            controlInteractionSerial++
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        // Touch/air-mouse interactions should obey the same idle timeout as D-pad input.
        if(controls && panel.isBlank() && error.isBlank()) controlInteractionSerial++
    }

    override fun onStart() {super.onStart();if(::request.isInitialized && player==null) {
        if(quality=="original") createPlayer() else changeQuality(quality,lastPosition,resumePlayWhenReady)
    }}
    override fun onResume() {
        super.onResume()
        window.decorView.post {applyPreferredDisplayMode(window,settings.displayModePreference)}
    }
    override fun onSaveInstanceState(outState: Bundle) {outState.putInt("audioOutputMaximum",audioOutputMaximum);outState.putBoolean("orientationLocked",orientationLocked);outState.putInt("manualOrientation",manualOrientation);outState.putLong("position",absolutePosition());outState.putFloat("speed",playbackSpeed);super.onSaveInstanceState(outState)}

    override fun onStop() {
        routeSwitchJob?.cancel();routeSwitchJob=null
        player?.let {p ->
            lastPosition=absolutePosition(); app.store.savePosition(request.localKey,if(p.playbackState==Player.STATE_ENDED) 0 else lastPosition)
            resumePlayWhenReady = p.playWhenReady
            reporter?.close(lastPosition, reporterHasStarted)
            p.release()
        }
        player=null; rendered=false; progressJob?.cancel(); reporter=null;reporterHasStarted=false
        metadataJob?.cancel();metadataJob=null
        sleepJob?.cancel();sleepJob=null
        qualityJob?.cancel();qualityJob=null
        releaseTranscode()
        super.onStop()
    }
    private fun createPlayer(ignoreMp4EditLists:Boolean=mp4EditListFallbackUsed) {
        rendered=false;error="";firstFrameMs=-1;headerMs=-1;totalStartupMs=-1;sourceStartupMs=-1
        // Each player instance gets its own single automatic first-frame retry budget.
        autoRetryAttempts=0
        val includeSourceTime = originalLaunch
        originalLaunch = false
        val started=SystemClock.elapsedRealtime()
        val firstHeader=AtomicLong(-1)
        // Video gets the longer first-byte budget; header scoping, TLS and redirect limits are identical.
        val client=app.http.scopedClient(if(quality=="original") request.scope else null,playback=true).newBuilder()
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
            // Bounded recovery: one automatic retry, only for a transient transport failure that happens
            // before the first frame. Everything else stays fatal and keeps the explicit retry button.
            .setLoadErrorHandlingPolicy(object: androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(PlaybackRecovery.MAX_AUTO_RETRIES) {
                override fun getRetryDelayMsFor(loadErrorInfo: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                    val httpCode = generateSequence(loadErrorInfo.exception as Throwable?) { it.cause }
                        .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>()
                        .firstOrNull()?.responseCode
                    if(quality!="original" && TranscodeSource.retryPendingSegment(httpCode,loadErrorInfo.errorCount,
                            loadErrorInfo.loadEventInfo.uri.path?.endsWith(".ts")==true)) {
                        window.decorView.post { if(error.isBlank()) status="服务器正在准备转码分片，请稍候" }
                        return 1000L
                    }
                    // With an unused server route available, switching route beats repeating a request
                    // that already timed out; the same-URL retry stays for sources without a fallback.
                    val allowed=!hasServerFallback() && PlaybackRecovery.shouldRetry(autoRetryAttempts,
                        PlaybackFailure.errorCodeOf(loadErrorInfo.exception),rendered,
                        PlaybackFailure.causeNames(loadErrorInfo.exception))
                    if(!allowed) return C.TIME_UNSET
                    autoRetryAttempts++
                    window.decorView.post {if(error.isBlank()) status=PlaybackRecovery.RETRY_NOTICE}
                    return PlaybackRecovery.RETRY_DELAY_MS
                }
            })
        val p=ExoPlayer.Builder(this,AudioOutputFactory(this,audioOutputMaximum).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)).setMediaSourceFactory(sourceFactory)
            // High-bitrate sources (115 / STRM direct play) need real read-ahead: keep a deeper
            // buffer, size the target to the device heap and read in bigger chunks.
            .setLoadControl(DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000,120_000,2_500,5_000)
                .setTargetBufferBytes((Runtime.getRuntime().maxMemory()/8)
                    .coerceIn(48L*1024*1024,160L*1024*1024).toInt())
                .setAllocator(androidx.media3.exoplayer.upstream.DefaultAllocator(true,256*1024))
                .build())
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
        audioManuallySelected=false
        availableTracks=Tracks.EMPTY
        p.trackSelectionParameters=p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT,request.subtitlePreference=="none" && !request.explicitSubtitle)
            .build()
        val media=androidx.media3.common.MediaItem.Builder().setUri(currentPlaybackUrl())
            .setMediaId(request.localKey.ifBlank {"session"})
            .setMediaMetadata(MediaMetadata.Builder().setTitle(request.title).build())
        if(quality!="original") {
            media.setMimeType(MimeTypes.APPLICATION_M3U8)
            if(transcodeSession?.vod!=true) media.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(4000).build())
        } else request.mimeHint?.let {media.setMimeType(it)}
        media.setSubtitleConfigurations((if(quality=="original") request.subtitles else emptyList()).map {sub ->
            androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                .setMimeType(sub.mime).setLanguage(sub.language).setLabel(sub.title).setId(sub.id)
                .setSelectionFlags(if(sub.isDefault) C.SELECTION_FLAG_DEFAULT else 0).build()
        })
        p.addListener(object:Player.Listener {
            override fun onVideoSizeChanged(videoSize:VideoSize) {
                if(!tvPlayback && videoSize.width>0 && videoSize.height>0) {
                    if(!orientationLocked) requestedOrientation=playerOrientation(false,videoSize.width,videoSize.height,videoSize.pixelWidthHeightRatio)
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
                val audioOptions=tracks.groups.filter {it.type==C.TRACK_TYPE_AUDIO}.flatMap {g->(0 until g.length).map {g to it}}
                if(quality=="original" && !audioManuallySelected && !audioPreferenceApplied && request.audioOrdinal>=0 && audioOptions.isNotEmpty()) {
                    audioPreferenceApplied=true
                    val exact=audioOptions.getOrNull(request.audioOrdinal)
                    if(exact!=null && exact.first.isTrackSupported(exact.second)) {
                        p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,false)
                            .setOverrideForType(TrackSelectionOverride(exact.first.mediaTrackGroup,exact.second)).build()
                    } else selectionNotice="无法播放所选音轨，请选择其他音轨"
                } else if(quality=="original" && !audioManuallySelected && !audioPreferenceApplied && (request.audioTitle.isNotBlank() || request.audioLanguage.isNotBlank())) {
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
                if(rendered) reporter?.progress(absolutePosition(), !isPlaying)
            }
            override fun onPlayWhenReadyChanged(playWhenReady:Boolean,reason:Int) {
                if(quality=="original" || qualitySwitching) return
                if(!playWhenReady && transcodePausedPosition==null) {
                    pauseTranscode()
                } else if(playWhenReady && transcodePausedPosition!=null) {
                    val resumeAt=transcodePausedPosition!!
                    if(transcodeSession?.vod==true) transcodePausedPosition=null
                    else changeQuality(quality,resumeAt,true)
                }
            }
            override fun onPlaybackStateChanged(playbackState:Int) {
                status=when(playbackState) {Player.STATE_BUFFERING->"正在缓冲…";Player.STATE_ENDED->"播放结束";else->""}
                if(playbackState==Player.STATE_ENDED) {controls=true;app.store.savePosition(request.localKey,0)}
                if(playbackState==Player.STATE_READY && quality!="original" && !p.playWhenReady) pauseTranscode()
            }
            override fun onPlayerError(e:PlaybackException) {
                if(quality=="original" && !useFallbackRoute && !fallbackRouteUsed && hasServerFallback() &&
                    PlaybackRecovery.shouldUseServerFallback(e.errorCode,PlaybackFailure.causeNames(e))) {
                    // The television could not reach the STRM target itself (timeout, refused or a
                    // server-side error such as 409). Emby is on the same network as the source, so let
                    // the server read the item instead. One-way and once per playback.
                    fallbackRouteUsed=true;useFallbackRoute=true
                    status=PlaybackRecovery.FALLBACK_NOTICE
                    lastPosition=player?.currentPosition?.coerceAtLeast(0) ?: lastPosition
                    resumePlayWhenReady=player?.playWhenReady ?: resumePlayWhenReady
                    progressJob?.cancel();progressJob=null
                    reporter?.close(lastPosition,reporterHasStarted);reporter=null;reporterHasStarted=false
                    val previous=player
                    player=null
                    window.decorView.post {
                        previous?.release()
                        if(!isFinishing && !isDestroyed && player==null) createPlayer()
                    }
                    return
                }
                if(quality=="original" && !rendered && !mp4EditListFallbackUsed && PlaybackFailure.isMp4IndexFailure(e,request.mimeHint)) {
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
                error=PlaybackFailure.describe(e,if(rendered) "播放读取" else "首帧前读取",request.mimeHint,
                    mediaUrl=currentPlaybackUrl(),baseUrl=request.scope?.baseUrl,retryAttempts=autoRetryAttempts)
                if(quality!="original") {
                    val httpCode=generateSequence(e as Throwable?) {it.cause}
                        .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>()
                        .firstOrNull()?.responseCode
                    if(httpCode!=null) error="转码播放请求失败（HTTP $httpCode）\n$error"
                }
                app.store.savePlaybackDiagnostic(error)
                controls=true
            }
        })
        p.addAnalyticsListener(object:AnalyticsListener {
            private var measuredBytes=0L
            private var measuredMs=0L
            override fun onBandwidthEstimate(eventTime:AnalyticsListener.EventTime,totalLoadTimeMs:Int,totalBytesLoaded:Long,bitrateEstimate:Long) {
                if(totalLoadTimeMs<=0 || totalBytesLoaded<=0) return
                measuredBytes+=totalBytesLoaded;measuredMs+=totalLoadTimeMs
                if(measuredBytes<2*1024*1024 || measuredMs<2000) return
                val sourceRate=if(quality=="original") p.videoFormat?.bitrate?.toLong()?.takeIf {it>0}
                    ?: mediaContext?.item?.versions?.firstOrNull {it.id==request.mediaSourceId}?.bitrate?.coerceAtLeast(0) ?: 0 else
                    quality.substringAfter('@',"").toLongOrNull()?.times(1000000) ?: when(quality) {"4k"->15000000L;"1440p"->10000000L;"1080p"->8000000L;"720p"->4000000L;else->0L}
                val actualRate=measuredBytes*8000/measuredMs
                clientNetworkNotice=if(sourceRate>0 && actualRate<sourceRate) "播放器接收速度可能不足，建议在清晰度中选择低码率档位。" else ""
            }
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
                    reporter?.start(absolutePosition(), !p.isPlaying)
                    reporterHasStarted=reporter!=null
                }
            }
        })
        startReporter()
        loadPlayerMetadata()
        p.setMediaItem(media.build(),if(quality=="original" || transcodeSession?.vod==true) lastPosition else 0);p.prepare();p.playWhenReady=resumePlayWhenReady
        progressJob=lifecycleScope.launch {
            var tick=0
            while(isActive) {
                delay(500)
                // Segment prompts also need position while the OSD is hidden.
                position=absolutePosition();duration=if(quality=="original") p.duration.coerceAtLeast(0) else transcodeDuration
                tick++
                if(tick%20==0 && rendered) {
                    reporter?.progress(position, !p.isPlaying)
                    app.store.savePosition(request.localKey,position)
                    transcodeSession?.let {session->runCatching {transcodeSource?.control(session)}}
                    transcodeSession?.let {session->runCatching {bufferNotice=transcodeSource?.bufferNotice(session,position).orEmpty()}}
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
                    val api=app.emby(config)
                    val chosen=app.store.seriesSubtitle(config.id,item.seriesId)?.let {choice->
                        try {withTimeout(15_000) {api.ensureSeriesSubtitle(item,choice)}}
                        catch(e:TimeoutCancellationException) {null}
                        catch(e:CancellationException) {throw e}
                        catch(_:Exception) {null}
                    }
                    api.playback(item,false,preferServerStream=settings.preferServerPlayback).let {next->
                        if(chosen==null) next.copy(subtitlePreference=request.subtitlePreference)
                        else next.copy(explicitSubtitle=true,subtitleTrackId="emby-sub:${chosen.index}",subtitleTitle=chosen.title)
                    }
                }.copy(requestedAtMs=started,sourceReadyAtMs=SystemClock.elapsedRealtime())
                startActivity(intent(this@PlayerActivity,next));finish()
            } catch(_:CancellationException) {throw CancellationException()}
            catch(_:Exception) {error="无法打开相邻剧集，请返回详情页重试。";controls=true}
            finally {switchingEpisode=false}
        }
    }
    private fun replacePlayback(next:PlaybackRequest,preserveRoute:Boolean=false) {
        val old=player
        lastPosition=absolutePosition()
        resumePlayWhenReady=old?.playWhenReady ?: resumePlayWhenReady
        reporter?.close(lastPosition,reporterHasStarted);reporter=null;reporterHasStarted=false
        progressJob?.cancel();progressJob=null
        old?.release();player=null
        if(!preserveRoute) {
            qualityJob?.cancel();qualityJob=null;releaseTranscode()
            quality="original";transcodeOffset=0;transcodeDuration=0;transcodePausedPosition=null
        }
        request=next
        if(!preserveRoute) {useFallbackRoute=false;fallbackRouteUsed=false}
        createPlayer()
    }
    private fun applyDownloadedSubtitle(track:MediaTrack) {
        routeSwitchJob=lifecycleScope.launch {
            try {
                val refreshed=withContext(Dispatchers.IO) {
                    val config=app.store.sources().firstOrNull {it.id==request.sourceId} ?: error("source")
                    val api=app.emby(config)
                    api.playback(api.item(request.embyItemId),versionId=request.mediaSourceId,preferServerStream=settings.preferServerPlayback,
                        audioIndex=request.audioStreamIndex)
                }
                val next=if(request.audioCompatibility) request.copy(subtitles=refreshed.subtitles) else refreshed
                replacePlayback(next.copy(explicitSubtitle=true,subtitleTrackId="emby-sub:${track.index}",subtitleTitle=track.title))
                panel=""
            } catch(e:CancellationException) {throw e}
            catch(_:Exception) {selectionNotice="字幕已下载，重新播放后可选择使用";panel=""}
        }
    }
    private fun setSleepTimer(minutes:Int?) {
        sleepJob?.cancel();sleepJob=null;sleepMinutes=minutes
        if(minutes!=null) sleepJob=lifecycleScope.launch {delay(minutes*60_000L);finish()}
    }

    /** The route in use: the STRM's own URL first, Emby's server-side entry after a failed direct try. */
    private fun currentPlaybackUrl():String =
        transcodeSession?.url ?: if(useFallbackRoute) request.fallbackUrl?.takeIf {it.isNotBlank()} ?: request.stableUrl else request.stableUrl

    /** True while this playback still has an unused Emby server route to try. */
    private fun hasServerFallback():Boolean =
        !useFallbackRoute && !request.fallbackUrl.isNullOrBlank() && request.fallbackUrl != request.stableUrl

    private fun startReporter() {
        if (request.embyItemId.isBlank() || reporter!=null) return
        reporterHasStarted=false
        reporter = PlaybackReporter(request,
            source = {
                app.store.sources().firstOrNull { it.id == request.sourceId }?.let { app.emby(it) }
            },
            onFailure = { withContext(Dispatchers.Main) { reportFailures++ } }
        )
    }
    private fun absolutePosition():Long = transcodePausedPosition ?: player?.let {
        if(quality=="original") it.currentPosition.coerceAtLeast(0) else {
            val windowOffset=if(it.currentTimeline.isEmpty) 0 else it.currentTimeline.getWindow(it.currentMediaItemIndex,Timeline.Window()).positionInFirstPeriodMs
            (transcodeOffset+windowOffset+it.currentPosition.coerceAtLeast(0)).coerceAtMost(transcodeDuration)
        }
    } ?: lastPosition
    private fun seekToAbsolute(target:Long) {
        if(qualitySwitching) return
        if(quality=="original" || transcodeSession?.vod==true) {
            player?.seekTo(target);position=target
            if(transcodePausedPosition!=null) transcodePausedPosition=target
        }
        else changeQuality(quality,target.coerceIn(0,(transcodeDuration-1).coerceAtLeast(0)),player?.playWhenReady ?: false)
    }
    private fun seek(delta:Long) {seekToAbsolute(MediaLogic.seek(absolutePosition(),delta,duration))}
    private fun releaseTranscode() {
        val session=transcodeSession ?: return
        val source=transcodeSource
        transcodeSession=null
        // The bounded cleanup survives Activity.onStop; worker TTL is the crash fallback.
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {withTimeout(5000) {source?.control(session,true)}} catch(_:Exception) {} finally {cancel()}
        }
    }
    private fun pauseTranscode() {
        if(transcodePausedPosition!=null) return
        transcodePausedPosition=absolutePosition()
        val session=transcodeSession ?: return
        lifecycleScope.launch {
            try {transcodeSource?.pause(session)} catch(e:CancellationException) {throw e} catch(_:Exception) {
                qualityNotice="转码暂停未确认，继续播放时会重新建立会话"
            }
        }
    }
    private fun openQualityPanel() {
        panel="quality";returnControl="quality";qualityNotice="正在检查转码服务…"
        lifecycleScope.launch {
            qualityProfiles=try {transcodeSource?.capabilities().orEmpty()} catch(e:CancellationException) {throw e} catch(_:Exception) {emptyList()}
            qualityNotice=if(qualityProfiles.isEmpty()) "此入口未提供可用的转码服务，仍可选择原画" else "高于原画的选项不会放大画面"
        }
    }
    private fun changeQuality(next:String,target:Long=absolutePosition(),play:Boolean=player?.playWhenReady ?: resumePlayWhenReady) {
        if(qualitySwitching) return
        qualityJob=lifecycleScope.launch {
            qualitySwitching=true;controls=true;panel="";status="正在切换清晰度…";error=""
            val source=transcodeSource
            val previous=transcodeSession
            transcodeSession=null
            lastPosition=target;resumePlayWhenReady=play
            bufferNotice=""
            clientNetworkNotice=""
            // Quality changes are one viewing session, not an Emby stop/start pair.
            reporter?.progress(target,!play);progressJob?.cancel()
            player?.release();player=null;rendered=false
            try {
                if(previous!=null) source?.control(previous,true)
                transcodePausedPosition=null
                if(next=="original") {
                    quality="original";transcodeOffset=0;transcodeDuration=0;createPlayer()
                } else {
                    val session=source?.create(next,target) ?: throw IllegalStateException()
                    transcodeSession=session;transcodeOffset=if(session.vod) 0 else session.startMs;transcodeDuration=session.durationMs
                    quality=next;useFallbackRoute=false
                    createPlayer()
                }
            } catch(e:CancellationException) {throw e}
            catch(_:Exception) {
                quality="original";transcodeOffset=0;transcodeDuration=0;transcodePausedPosition=null
                error="清晰度切换失败，可选择原画或稍后重试";controls=true
            } finally {qualitySwitching=false}
        }
    }
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
        val progressFocus=remember {FocusRequester()}
        val audioOutputFocus=remember {FocusRequester()}
        val subtitleInk=SunnyColors.Text.toArgb()
        val subtitleAccent=SunnyColors.Accent.toArgb()
        val resolvedSubtitleFont by produceState<android.graphics.Typeface?>(null,settings.subtitleFontChoice,settings.customFontFile) {
            value=withContext(Dispatchers.IO) {subtitleTypeface(this@PlayerActivity,settings)}
        }
        val appliedSubtitle=remember {arrayOfNulls<Any>(1)}
        val activeSkip=SegmentLogic.active(context?.skipSegments.orEmpty(),position,dismissedSegments)
        val remainingMs=(duration-position).coerceAtLeast(0)
        val showNextUp=context?.next!=null && !nextUpDismissed && rendered && duration>0 &&
            remainingMs in 1..90_000 && activeSkip==null
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val titleWidth=if(tvPlayback) 300.dp else (maxWidth-120.dp).coerceIn(70.dp,300.dp)
            AndroidView(factory={androidContext->PlayerView(androidContext).apply {
                useController=false;keepScreenOn=true
                isFocusable=false
                descendantFocusability=android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }},update={view->
                view.player=player;view.resizeMode=resizeMode
                // 裁切 is Media3's own ZOOM: the aspect ratio is kept and the picture is grown until
                // the screen is filled, so a 18:9 file loses its sides and a 16:10 file its top and
                // bottom — and nothing more. The old extra 1.12 upscale cut a further 12% of the
                // frame away on every edge, which the user rejected as too much.
                view.scaleX=1f
                view.scaleY=1f
                val key=listOf(settings.subtitleEdge,settings.subtitlePosition,settings.subtitleBackground,
                    settings.subtitleScaleLevel,settings.subtitleFontChoice,subtitleInk,subtitleAccent,resolvedSubtitleFont)
                if(appliedSubtitle[0]!=key) {
                    appliedSubtitle[0]=key
                    applySubtitleAppearance(view,settings,subtitleInk,subtitleAccent,resolvedSubtitleFont)
                }
            },modifier=Modifier.fillMaxSize())

            if(panel.isBlank()) PlayerTouchSurface(
                onTap={controls=!controls},
                onDoubleTap={region->
                    player?.let {p->
                        if(region==1) {
                            if(p.isPlaying) {p.pause();gestureNotice="已暂停"} else {p.play();gestureNotice="继续播放"}
                        } else if(duration>0 && (quality!="original" || p.isCurrentMediaItemSeekable)) {
                            seek(if(region==0) -30_000 else 30_000)
                            gestureNotice=if(region==0) "后退 30 秒" else "前进 30 秒"
                        } else gestureNotice="当前媒体暂不支持快进"
                    }
                },
                onStart={
                    gestureActive=true;gestureSeekTarget=null
                    gestureStartPosition=absolutePosition()
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
                            if(duration>0 && (quality!="original" || p.isCurrentMediaItemSeekable)) {
                                gestureSeekTarget=PlayerGesturePolicy.seekTarget(gestureStartPosition,dx,duration)
                                gestureNotice="跳转至 ${clock(gestureSeekTarget!!)} / ${clock(duration)}"
                            } else gestureNotice="当前媒体暂不支持快进"
                        }
                    }
                },
                onEnd={commit->
                    if(commit) gestureSeekTarget?.let {target->seekToAbsolute(target)}
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
                        retryUsed=true;lastPosition=absolutePosition()
                        if(quality!="original") {
                            changeQuality(quality,lastPosition,player?.playWhenReady ?: resumePlayWhenReady)
                            return@PlayerControl
                        }
                        // A manual retry also tries the other route once: direct failed, so ask Emby.
                        if(hasServerFallback()) useFallbackRoute=true
                        player?.release();player=null;progressJob?.cancel()
                        reporter?.close(lastPosition,reporterHasStarted);reporter=null;reporterHasStarted=false;createPlayer()
                    }
                    PlayerControl("退出播放","exit",initial=retryUsed) {finish()}
                    if(transcodeSource!=null) PlayerControl("选择清晰度","quality",badge=TranscodeSource.selectedQualityBadge(quality)) {openQualityPanel()}
                }
            }

            if(settings.diagnostics) Text("点击至首帧 ${millis(totalStartupMs)}  ·  源解析 ${millis(sourceStartupMs)}\n引擎首帧 ${millis(firstFrameMs)}  ·  首个响应头 ${millis(headerMs)}  ·  回报失败 $reportFailures\n${request.playMethod} · Media3",
                color=SunnyColors.Accent,fontSize=12.sp,modifier=Modifier.align(Alignment.TopEnd).padding(25.dp).background(Color.Black.copy(.62f),RoundedCornerShape(10.dp)).padding(12.dp))

            if(activeSkip!=null && rendered && error.isBlank() && panel.isBlank()) {
                SkipSegmentPrompt(activeSkip,onSkip={
                    seekToAbsolute(activeSkip.endMs.coerceAtMost(duration.takeIf {it>0} ?: activeSkip.endMs))
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
                Row(Modifier.align(Alignment.TopEnd).padding(24.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    PlayerControl("音频输出","audio",modifier=Modifier.focusRequester(audioOutputFocus).focusProperties {down=progressFocus},
                        initial=returnControl=="audio-output",showFocusLabel=true) {returnControl="audio-output";panel="audio-output"}
                    if(!tvPlayback) PlayerControl(if(orientationLocked) "解锁自动旋转" else "锁定当前方向",if(orientationLocked) "lock" else "unlock",selected=orientationLocked,showFocusLabel=false) {
                        orientationLocked=!orientationLocked
                        if(orientationLocked) {manualOrientation=fixedOrientation();requestedOrientation=manualOrientation}
                        else {val size=player?.videoSize;requestedOrientation=playerOrientation(false,size?.width ?: 0,size?.height ?: 0,size?.pixelWidthHeightRatio ?: 1f)}
                    }
                }
            }
            if(controls && panel.isBlank() && error.isBlank()) {
                Box(Modifier.align(Alignment.TopStart).padding(start=if(tvPlayback) 40.dp else 24.dp,top=30.dp).width(titleWidth).height(82.dp)) {PlayerMediaTitle()}
                LaunchedEffect(controls,playing,panel,error,gestureActive,controlInteractionSerial) {
                    if(playing && panel.isEmpty() && error.isEmpty() && !gestureActive) {delay(6000);controls=false}
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(.97f))))
                    .padding(start=24.dp,end=24.dp,top=48.dp,bottom=14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    if(selectionNotice.isNotBlank()) Text(selectionNotice,color=Color.White.copy(.7f),fontSize=12.sp)
                    if(bufferNotice.isNotBlank()) Text(bufferNotice,color=Color.White.copy(.85f),fontSize=12.sp)
                    if(clientNetworkNotice.isNotBlank()) Text(clientNetworkNotice,color=Color.White.copy(.85f),fontSize=12.sp)

                    PlayerProgress(position,duration,settings.seekStepSeconds*1000L,Modifier.focusRequester(progressFocus).focusProperties {up=audioOutputFocus},
                        onSeekBy={seek(it)},onSeekTo={target->seekToAbsolute(target)})

                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(clock(position),color=Color.White.copy(.72f),fontSize=12.sp)
                        Text(clock(duration),color=Color.White.copy(.72f),fontSize=12.sp)
                    }
                    Row((if(tvPlayback) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())).focusProperties {up=progressFocus},verticalAlignment=Alignment.CenterVertically) {
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                            context?.previous?.let {previous->PlayerControl("上一集","previous") {switchEpisode(previous)}}
                            PlayerControl("后退 ${settings.seekStepSeconds} 秒","rewind") {seek(-settings.seekStepSeconds*1000L)}
                            PlayerControl(if(playing) "暂停" else "播放",if(playing) "pause" else "play",initial=returnControl=="transport",emphasis=true) {
                                player?.let {if(it.isPlaying) it.pause() else it.play()}
                            }
                            PlayerControl("前进 ${settings.seekStepSeconds} 秒","forward") {seek(settings.seekStepSeconds*1000L)}
                            context?.next?.let {next->PlayerControl("下一集","next") {switchEpisode(next)}}
                        }
                        Spacer(if(tvPlayback) Modifier.weight(1f) else Modifier.width(12.dp))
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                            PlayerControl("倍速 ${speedLabel(playbackSpeed)}","speed",initial=returnControl=="speed",badge=speedLabel(playbackSpeed)) {returnControl="speed";panel="speed"}
                            if(transcodeSource!=null) PlayerControl("清晰度："+TranscodeSource.qualityLabel(quality),"quality",badge=TranscodeSource.selectedQualityBadge(quality),initial=returnControl=="quality") {openQualityPanel()}
                            if(context?.item?.chapters?.isNotEmpty()==true) PlayerControl("章节","chapters",initial=returnControl=="chapters") {returnControl="chapters";panel="chapters"}
                            if(quality=="original" && (request.embyItemId.isNotBlank() || availableTracks.groups.any {it.type==C.TRACK_TYPE_TEXT} || request.subtitles.isNotEmpty())) PlayerControl("字幕","subtitle",initial=returnControl=="subtitle") {returnControl="subtitle";panel="subtitles"}
                            if(request.sourceTracks.any {it.type=="Audio"} || availableTracks.groups.any {it.type==C.TRACK_TYPE_AUDIO}) PlayerControl("音轨","audio",initial=returnControl=="audio") {returnControl="audio";panel="audio"}
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
            "subtitle-search" -> (mediaContext?.item ?: MediaEntry(request.embyItemId,request.sourceId,request.title,"Video")).let {item->app.store.sources().firstOrNull {it.id==request.sourceId}?.let {config->
                SubtitleSearchDialog(app.emby(config),app.store,item,request.mediaSourceId,{panel=""}) {applyDownloadedSubtitle(it)}
            }}
            "audio-output" -> AudioOutputPanel()
            "audio","subtitles" -> TrackPanel()
            "chapters" -> ChapterPanel()
            "cast" -> CastPanel()
            "sleep" -> SleepPanel()
            "speed" -> SpeedPanel()
            "quality" -> QualityPanel()
            "info" -> InfoPanel()
        }
    }
    @Composable private fun AudioOutputPanel() {
        PlayerSheet("音频输出",{panel=""}) {
            listOf(0 to "原始声道（默认）",2 to "双声道 · 电视扬声器",6 to "5.1 声道").forEach {(maximum,label)->
                PlayerOption(label,selected=audioOutputMaximum==maximum) {
                    if(audioOutputMaximum!=maximum) {
                        // Preserve chosen tracks, subtitles, speed and playback position across a sink rebuild.
                        val parameters=player?.trackSelectionParameters
                        audioOutputMaximum=maximum
                        replacePlayback(request,preserveRoute=true)
                        parameters?.let {player?.trackSelectionParameters=it}
                    }
                    panel=""
                }
            }
            Text("只合并超出所选数量的声道；不会把双声道扩成 5.1。",color=Color.White.copy(.7f),fontSize=13.sp)
        }
    }

    @Composable private fun QualityPanel() {
        PlayerSheet("清晰度",{panel=""}) {
            Text(if(qualitySwitching) "正在切换…" else qualityNotice,color=Color.White.copy(.7f),fontSize=14.sp)
            TranscodeSource.menuQualityOptions.filter { (id,_) -> id=="original" || id in qualityProfiles }.forEach {(id,label)->
                PlayerOption(label,selected=quality==id) {
                    if(!qualitySwitching && (id=="original" || id in qualityProfiles)) changeQuality(id)
                    else qualityNotice="此清晰度暂不可用"
                }
            }

        }
    }
    @Composable private fun SpeedPanel() {
        PlayerSheet("播放倍速",{panel=""}) {
            listOf(1f,1.25f,1.5f,2f).forEach {speed->
                PlayerOption(speedLabel(speed),selected=playbackSpeed==speed) {
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
            if(quality=="original" && type==C.TRACK_TYPE_TEXT && request.embyItemId.isNotBlank()) PlayerOption("查找并下载字幕") {panel="subtitle-search"}
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
                        item {
                            val supported=group.isTrackSupported(i)
                            PlayerOption(listOfNotNull(format.label,format.language,format.sampleMimeType).distinct().joinToString(" · ").ifBlank {"轨道 ${i+1}"}+
                                if(supported) "" else " · 设备不支持",
                                selected=group.isTrackSelected(i) && player?.trackSelectionParameters?.disabledTrackTypes?.contains(type)!=true) {
                                if(type==C.TRACK_TYPE_TEXT) subtitleManuallySelected=true
                                if(type==C.TRACK_TYPE_AUDIO) audioManuallySelected=true
                                if(!supported) {
                                    selectionNotice=if(type==C.TRACK_TYPE_AUDIO) "此音轨无法播放，请选择其他音轨" else "此字幕格式不受设备支持，请查找文本字幕"
                                } else player?.let {it.trackSelectionParameters=it.trackSelectionParameters.buildUpon()
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
                        seekToAbsolute(chapter.startMs);panel=""
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
        private fun speedLabel(speed:Float):String = if(speed % 1f == 0f) "%.1fx".format(speed) else "${speed}x"
        private fun clock(ms:Long):String {val seconds=ms.coerceAtLeast(0)/1000;return if(seconds>=3600) "%d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60) else "%02d:%02d".format(seconds/60,seconds%60)}
    }
}
