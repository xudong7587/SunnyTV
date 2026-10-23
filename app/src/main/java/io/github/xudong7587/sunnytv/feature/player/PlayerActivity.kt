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
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
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
import io.github.xudong7587.sunnytv.source.transcode.CloudVideoSource

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity: ComponentActivity() {
    private val app get() = application as SunnyApp
    private lateinit var request: PlaybackRequest
    private var player by mutableStateOf<ExoPlayer?>(null)
    private var controls by mutableStateOf(true)
    private var realtimeInfo by mutableStateOf(false)
    private var realtimeSnapshot by mutableStateOf(PlaybackRealtimeInfo())
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
    // TV's SurfaceView is hosted directly by the window. OSD recomposition never owns its geometry.
    private var tvPlayerView:PlayerView?=null
    private var lightPlayerView:PlayerView?=null
    private var frameCropper:VideoFrameCropper?=null
    private var pictureArea=PictureArea()
    private var edgeSampler:io.github.xudong7587.sunnytv.feature.ambilight.VideoEdgeSampler?=null
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
    private var cloudSession: CloudVideoSource.Session? = null
    // For in-band AAC PCE only, preserve the user's time while reading layout metadata.
    private var cloudBootstrapTarget: Long? = null
    private var cloudVariants by mutableStateOf<List<CloudVideoSource.Variant>>(emptyList())
    private var cloudNotice by mutableStateOf("")
    private var cloudDiscoveryJob: Job? = null
    private var cloudAutoDiscovered=false
    private var cloudDiscoveryGeneration=0
    private var cloudLoading by mutableStateOf(false)
    private var cloudDelivery by mutableStateOf("direct")
    private val isCloudQuality get() = quality.startsWith("115:")
    private fun qualityBadge() = if(isCloudQuality) cloudSession?.variant?.badge ?: "115" else TranscodeSource.selectedQualityBadge(quality)
    private fun qualityLabel() = if(isCloudQuality) cloudSession?.variant?.label ?: "115 云端" else TranscodeSource.qualityLabel(quality)
    private var quality by mutableStateOf("original")
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
    private var originalSubtitleUrl:String?=null
    private var subtitleProgressMs by mutableLongStateOf(0L)
    private var subtitleAdjustmentJob:Job?=null
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
        subtitleProgressMs=savedInstanceState?.getLong("subtitleProgressMs",0L) ?: 0L
        realtimeInfo=app.store.realtimePlaybackInfo()
        val config=resources.configuration
        @Suppress("DEPRECATION")
        val playbackDisplayId=windowManager.defaultDisplay.displayId
        tvPlayback=((config.uiMode and Configuration.UI_MODE_TYPE_MASK)==Configuration.UI_MODE_TYPE_TELEVISION ||
            playbackDisplayId!=Display.DEFAULT_DISPLAY)
        orientationLocked=savedInstanceState?.getBoolean("orientationLocked") ?: false
        manualOrientation=savedInstanceState?.getInt("manualOrientation") ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        requestedOrientation=if(orientationLocked) manualOrientation else playerOrientation(tvPlayback)
        playbackSpeed=savedInstanceState?.getFloat("speed",1f) ?: 1f
        resizeMode=(savedInstanceState?.getInt("resizeMode",AspectRatioFrameLayout.RESIZE_MODE_FIT)
            ?: AspectRatioFrameLayout.RESIZE_MODE_FIT).takeIf {it in setOf(AspectRatioFrameLayout.RESIZE_MODE_FIT,
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM,AspectRatioFrameLayout.RESIZE_MODE_FILL)} ?: AspectRatioFrameLayout.RESIZE_MODE_FIT
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
        if(tvPlayback) {
            val host=FrameLayout(this).apply {setBackgroundColor(android.graphics.Color.BLACK)}
            tvPlayerView=createVideoView(this)
            host.addView(tvPlayerView,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))
            val chrome=ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {ScaledUi(settings) {SunnyTheme(settings.copy(darkTheme=true)) {PlayerContent()}}}
            }
            host.addView(chrome,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))
            setContentView(host)
        } else setContent {ScaledUi(settings) {SunnyTheme(settings.copy(darkTheme=true)) {PlayerContent()}}}
    }

    private fun createVideoView(context:Context)=PlayerView(context).apply {
        lightPlayerView=this
        useController=false;keepScreenOn=true
        isFocusable=false;isFocusableInTouchMode=false
        descendantFocusability=android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        clipChildren=true
        addOnLayoutChangeListener {view,_,_,_,_,_,_,_,_->applyVideoGeometry(view as PlayerView)}
        findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
            .addOnLayoutChangeListener {_,_,_,_,_,_,_,_,_->applyVideoGeometry(this)}
    }

    private fun applyVideoGeometry(view:PlayerView) {
        val size=view.player?.videoSize ?: return
        if(size.width<=0 || size.height<=0 || view.width<=0 || view.height<=0)return
        val nativeCrop=tvPlayback && resizeMode==AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        val bounds=VideoGeometry.bounds(view.width,view.height,size.width*size.pixelWidthHeightRatio/size.height,
            resizeMode==AspectRatioFrameLayout.RESIZE_MODE_ZOOM && !nativeCrop,
            resizeMode==AspectRatioFrameLayout.RESIZE_MODE_FILL || nativeCrop,pictureArea)
        val frame=view.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
        // On TV, let the decoder crop proportionally into the target surface. Some vendor video
        // planes stretch an oversized SurfaceView's entire buffer despite SurfaceFlinger's crop.
        // Encoded bars still expand the viewport; OSD remains outside this video frame.
        frame.setAspectRatio(0f)
        val params=frame.layoutParams as FrameLayout.LayoutParams
        if(params.width!=bounds.width || params.height!=bounds.height) {
            params.width=bounds.width;params.height=bounds.height;params.gravity=android.view.Gravity.CENTER
            frame.layoutParams=params
        }
        frame.translationX=bounds.x;frame.translationY=bounds.y
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
    }
        if(app.ambilight.config.value.enabled && edgeSampler==null) {
            edgeSampler=io.github.xudong7587.sunnytv.feature.ambilight.VideoEdgeSampler(app.ambilight,{
                lightPlayerView?.let {view->(view.videoSurfaceView as? android.view.SurfaceView)?.let {it to view}}
            },{rendered && player?.let {it.playWhenReady && it.playbackState!=Player.STATE_ENDED}==true && error.isBlank()}, {
                if(tvPlayback && resizeMode==AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                    val view=lightPlayerView;val size=player?.videoSize
                    if(view!=null && size!=null && size.height>0) {
                        val bounds=VideoGeometry.bounds(view.width,view.height,size.width*size.pixelWidthHeightRatio/size.height,true,false,pictureArea)
                        VideoGeometry.visibleFraction(view.width,view.height,bounds)
                    } else null
                } else null
            }).also {it.start()}
        }
    }
    override fun onResume() {
        super.onResume()
        window.decorView.post {applyPreferredDisplayMode(window,settings.displayModePreference)}
    }
    override fun onSaveInstanceState(outState: Bundle) {outState.putInt("resizeMode",resizeMode);outState.putLong("subtitleProgressMs",subtitleProgressMs);outState.putInt("audioOutputMaximum",audioOutputMaximum);outState.putBoolean("orientationLocked",orientationLocked);outState.putInt("manualOrientation",manualOrientation);outState.putLong("position",absolutePosition());outState.putFloat("speed",playbackSpeed);super.onSaveInstanceState(outState)}

    override fun onStop() {
        frameCropper?.close();frameCropper=null;pictureArea=PictureArea()
        edgeSampler?.stop();edgeSampler=null
        subtitleAdjustmentJob?.cancel();subtitleAdjustmentJob=null
        routeSwitchJob?.cancel();routeSwitchJob=null
        player?.let {p ->
            lastPosition=absolutePosition(); app.store.savePosition(request.localKey,if(p.playbackState==Player.STATE_ENDED) 0 else lastPosition)
            resumePlayWhenReady = p.playWhenReady
            reporter?.close(lastPosition, reporterHasStarted,duration)
            tvPlayerView?.player=null
            p.release()
        }
        player=null; rendered=false; progressJob?.cancel(); reporter=null;reporterHasStarted=false
        metadataJob?.cancel();metadataJob=null
        sleepJob?.cancel();sleepJob=null
        qualityJob?.cancel();qualityJob=null
        if(cloudLoading) cloudAutoDiscovered=false
        cloudDiscoveryGeneration++
        cloudDiscoveryJob?.cancel();cloudLoading=false
        releaseTranscode()
        super.onStop()
    }
    private fun createPlayer(ignoreMp4EditLists:Boolean=mp4EditListFallbackUsed) {
        rendered=false;error="";firstFrameMs=-1;headerMs=-1;totalStartupMs=-1;sourceStartupMs=-1
        cloudBootstrapTarget=null
        // Each player instance gets its own single automatic first-frame retry budget.
        autoRetryAttempts=0
        val includeSourceTime = originalLaunch
        originalLaunch = false
        val started=SystemClock.elapsedRealtime()
        val firstHeader=AtomicLong(-1)
        // Video gets the longer first-byte budget; header scoping, TLS and redirect limits are identical.
        val transport=if(isCloudQuality) io.github.xudong7587.sunnytv.core.network.CloudPlaybackTransport.client(app.http,cloudSession!!.url,request.scope,request.subtitles.map {it.url})
            else app.http.scopedClient(if(quality=="original") request.scope else null,playback=true)
        val client=transport.newBuilder()
            .addNetworkInterceptor(Interceptor {chain ->
                val response=chain.proceed(chain.request())
                firstHeader.compareAndSet(-1,SystemClock.elapsedRealtime()-started)
                response
            }).build()
        val rateMeter=PlaybackRateMeter().apply {sample(SystemClock.elapsedRealtime())}
        var videoDecoder="";var audioDecoder=""
        realtimeSnapshot=PlaybackRealtimeInfo()
        val dataSource=OkHttpDataSource.Factory(client).setUserAgent(io.github.xudong7587.sunnytv.core.network.HttpPolicy.USER_AGENT)
            .setTransferListener(object:androidx.media3.datasource.TransferListener {
                override fun onTransferInitializing(source:androidx.media3.datasource.DataSource,dataSpec:androidx.media3.datasource.DataSpec,isNetwork:Boolean) {}
                override fun onTransferStart(source:androidx.media3.datasource.DataSource,dataSpec:androidx.media3.datasource.DataSpec,isNetwork:Boolean) {}
                override fun onTransferEnd(source:androidx.media3.datasource.DataSource,dataSpec:androidx.media3.datasource.DataSpec,isNetwork:Boolean) {}
                override fun onBytesTransferred(source:androidx.media3.datasource.DataSource,dataSpec:androidx.media3.datasource.DataSpec,isNetwork:Boolean,bytesTransferred:Int) {
                    if(isNetwork) rateMeter.addBytes(bytesTransferred)
                }
            })
        val extractors=DefaultExtractorsFactory().apply {
            if(ignoreMp4EditLists) setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
        }
        // Bounded recovery: one automatic retry, only for a transient transport failure that happens
        // before the first frame. Everything else stays fatal and keeps the explicit retry button.
        val errorPolicy=object: androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(PlaybackRecovery.MAX_AUTO_RETRIES) {
                override fun getRetryDelayMsFor(loadErrorInfo: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                    val httpCode = generateSequence(loadErrorInfo.exception as Throwable?) { it.cause }
                        .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>()
                        .firstOrNull()?.responseCode
                    if(isCloudQuality) {
                        val delay=PlaybackRecovery.cloudRetryDelay(loadErrorInfo.errorCount,
                            PlaybackFailure.errorCodeOf(loadErrorInfo.exception),httpCode,
                            PlaybackFailure.causeNames(loadErrorInfo.exception)) ?: return C.TIME_UNSET
                        autoRetryAttempts++
                        // Retry the failed load in place: preserve buffered media, position and pause state.
                        return delay
                    }
                    if(quality!="original" && !isCloudQuality && TranscodeSource.retryPendingSegment(httpCode,loadErrorInfo.errorCount,
                            loadErrorInfo.loadEventInfo.uri.path?.endsWith(".ts")==true)) {
                        window.decorView.post { if(error.isBlank()) status="服务器正在准备转码分片，请稍候" }
                        return 1000L
                    }
                    // With an unused server route available, switching route beats repeating a request
                    // that already timed out; the same-URL retry stays for sources without a fallback.
                    val allowed=(quality!="original" || !hasServerFallback()) && PlaybackRecovery.shouldRetry(autoRetryAttempts,
                        PlaybackFailure.errorCodeOf(loadErrorInfo.exception),rendered,
                        PlaybackFailure.causeNames(loadErrorInfo.exception))
                    if(!allowed) return C.TIME_UNSET
                    autoRetryAttempts++
                    window.decorView.post {if(error.isBlank()) status=PlaybackRecovery.RETRY_NOTICE}
                    return PlaybackRecovery.RETRY_DELAY_MS
                }
            }
        val sourceFactory=DefaultMediaSourceFactory(dataSource,extractors).setLoadErrorHandlingPolicy(errorPolicy)
        val memoryInfo=android.app.ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(memoryInfo)
        val bufferBudget=io.github.xudong7587.sunnytv.core.playback.PlaybackBufferPolicy.targetBytes(
            Runtime.getRuntime().maxMemory(),memoryInfo.totalMem.coerceAtLeast(1L),
            memoryInfo.availMem.coerceAtLeast(1L),memoryInfo.lowMemory)
        val bufferAllocator=androidx.media3.exoplayer.upstream.DefaultAllocator(true,256*1024)
        val p=ExoPlayer.Builder(this,AudioOutputFactory(this,audioOutputMaximum).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)).setMediaSourceFactory(sourceFactory)
            // High-bitrate sources (115 / STRM direct play) need real read-ahead: keep a deeper
            // buffer, size the target to the device heap and read in bigger chunks.
            .setLoadControl(DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000,120_000,2_500,5_000)
                .setTargetBufferBytes(bufferBudget)
                // Keep the byte ceiling: forcing 30s at 80 Mbps would allocate ~300 MB
                // even on a small-heap TV. The larger budget extends actual read-ahead.
                .setPrioritizeTimeOverSizeThresholds(false)
                .setAllocator(bufferAllocator)
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
            if(!isCloudQuality && transcodeSession?.vod!=true) media.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(4000).build())
        } else request.mimeHint?.let {media.setMimeType(it)}
        media.setSubtitleConfigurations((if(quality=="original" || isCloudQuality) request.subtitles else emptyList()).map {sub ->
            androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                .setMimeType(sub.mime).setLanguage(sub.language).setLabel(sub.title).setId(sub.id)
                .setSelectionFlags(if(sub.isDefault) C.SELECTION_FLAG_DEFAULT else 0).build()
        })
        var cloudPrimeAttempted=false
        var primeTextParameters:androidx.media3.common.TrackSelectionParameters?=null
        val pceExtractors=PceHlsExtractorFactory(
            missingConfig={window.decorView.post {
                if(player===p && !cloudPrimeAttempted && lastPosition>0) {
                    cloudPrimeAttempted=true
                    cloudBootstrapTarget=p.currentPosition.coerceAtLeast(0)
                    primeTextParameters=p.trackSelectionParameters
                    p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build()
                    p.seekTo(0)
                }
            }},
            completeConfig={window.decorView.post {
                if(player===p && cloudBootstrapTarget!=null) {
                    val target=cloudBootstrapTarget!!
                    cloudBootstrapTarget=null
                    p.seekTo(target)
                    primeTextParameters?.let {p.trackSelectionParameters=it}
                    primeTextParameters=null
                }
            }}
        )
        p.addListener(object:Player.Listener {
            override fun onVideoSizeChanged(videoSize:VideoSize) {
                lightPlayerView?.let {applyVideoGeometry(it)}
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
                            else language.isNotBlank() && (f.language.equals(language,true) ||
                                AudioPreference.matches(language,f.language.orEmpty(),f.label.orEmpty()))
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
                } else if(!audioManuallySelected && !audioPreferenceApplied && (request.audioTitle.isNotBlank() || request.audioLanguage.isNotBlank())) {
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
                    } else if(request.explicitSubtitle) {
                        selectionNotice="所选字幕暂不可用，可在字幕面板选择此媒体的其他实际字幕"
                    }
                    // No match for a library preference leaves the media/player default selection intact.
                }
            }
            override fun onIsPlayingChanged(isPlaying:Boolean) {
                playing=isPlaying
                if(rendered) reporter?.progress(absolutePosition(), !isPlaying,duration)
            }
            override fun onPlayWhenReadyChanged(playWhenReady:Boolean,reason:Int) {
                if(quality=="original" || isCloudQuality || qualitySwitching) return
                if(!playWhenReady && transcodePausedPosition==null) {
                    pauseTranscode()
                } else if(playWhenReady && transcodePausedPosition!=null) {
                    val resumeAt=transcodePausedPosition!!
                    if(transcodeSession?.vod==true) transcodePausedPosition=null
                    else changeQuality(quality,resumeAt,true)
                }
            }
            override fun onPlaybackStateChanged(playbackState:Int) {
                status=when(playbackState) {
                    Player.STATE_BUFFERING->"正在缓冲…"
                    Player.STATE_ENDED->"播放结束"
                    else->""
                }
                if(playbackState==Player.STATE_ENDED) {
                    controls=true
                    app.store.savePosition(request.localKey,0)
                }
                if(playbackState==Player.STATE_READY && quality!="original" && !isCloudQuality && !p.playWhenReady) pauseTranscode()
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
                    reporter?.close(lastPosition,reporterHasStarted,duration);reporter=null;reporterHasStarted=false
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
                error=PlaybackFailure.describe(e,if(rendered) "播放读取" else "首帧前读取",if(quality=="original") request.mimeHint else MimeTypes.APPLICATION_M3U8,
                    mediaUrl=currentPlaybackUrl(),baseUrl=request.scope?.baseUrl,retryAttempts=autoRetryAttempts)
                if(quality!="original") {
                    if(isCloudQuality && cloudSession?.delivery=="direct" && e.errorCode in setOf(2001,2002))
                        error="115 直连连接失败，可在清晰度中选择「NAS 代理」后重新选择档位。\n$error"
                    val httpCode=generateSequence(e as Throwable?) {it.cause}
                        .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>()
                        .firstOrNull()?.responseCode
                    if(httpCode!=null) error=(if(isCloudQuality) "115 云端播放请求失败，可重新选择清晰度或切换连接方式" else "转码播放请求失败")+"（HTTP $httpCode）\n$error"
                }
                app.store.savePlaybackDiagnostic(error)
                controls=true
            }
        })
        p.addAnalyticsListener(object:AnalyticsListener {
            override fun onVideoDecoderInitialized(eventTime:AnalyticsListener.EventTime,decoderName:String,initializedTimestampMs:Long,initializationDurationMs:Long) {videoDecoder=decoderName}
            override fun onAudioDecoderInitialized(eventTime:AnalyticsListener.EventTime,decoderName:String,initializedTimestampMs:Long,initializationDurationMs:Long) {audioDecoder=decoderName}
            override fun onVideoDisabled(eventTime:AnalyticsListener.EventTime,decoderCounters:androidx.media3.exoplayer.DecoderCounters) {videoDecoder=""}
            override fun onAudioDisabled(eventTime:AnalyticsListener.EventTime,decoderCounters:androidx.media3.exoplayer.DecoderCounters) {audioDecoder=""}
            private var measuredBytes=0L
            private var measuredMs=0L
            override fun onBandwidthEstimate(eventTime:AnalyticsListener.EventTime,totalLoadTimeMs:Int,totalBytesLoaded:Long,bitrateEstimate:Long) {
                if(totalLoadTimeMs<=0 || totalBytesLoaded<=0) return
                measuredBytes+=totalBytesLoaded;measuredMs+=totalLoadTimeMs
                if(measuredBytes<2*1024*1024 || measuredMs<2000) return
                val sourceRate=if(isCloudQuality) cloudSession?.variant?.bandwidth ?: 0 else if(quality=="original") p.videoFormat?.bitrate?.toLong()?.takeIf {it>0}
                    ?: mediaContext?.item?.versions?.firstOrNull {it.id==request.mediaSourceId}?.bitrate?.coerceAtLeast(0) ?: 0 else
                    quality.substringAfter('@',"").toLongOrNull()?.times(1000000) ?: when(quality) {"4k"->15000000L;"1440p"->10000000L;"1080p"->8000000L;"720p"->4000000L;else->0L}
                val actualRate=measuredBytes*8000/measuredMs
                clientNetworkNotice=if(sourceRate>0 && actualRate<sourceRate) "播放器接收速度可能不足，建议在清晰度中选择低码率档位。" else ""
            }
            override fun onRenderedFirstFrame(eventTime:AnalyticsListener.EventTime,output:Any,renderTimeMs:Long) {
                if(cloudBootstrapTarget!=null) return
                if(!rendered) {
                    rendered=true
                    if(!cloudAutoDiscovered) {cloudAutoDiscovered=true;discoverCloudQuality()}
                    val timing = StartupTiming.measure(
                        if (includeSourceTime) request.requestedAtMs else -1,
                        if (includeSourceTime) request.sourceReadyAtMs else -1,
                        started, SystemClock.elapsedRealtime()
                    )
                    firstFrameMs=timing.engineMs ?: -1
                    totalStartupMs=timing.totalMs ?: -1
                    sourceStartupMs=timing.sourceMs ?: -1
                    headerMs=firstHeader.get()
                    reporter?.start(absolutePosition(), !p.isPlaying,if(quality=="original" || isCloudQuality) p.duration else transcodeDuration)
                    reporterHasStarted=reporter!=null
                }
            }
        })
        startReporter()
        loadPlayerMetadata()
        val item=media.build()
        val externalSelected=request.subtitles.any {SubtitleSelection.stableId(it.id)==SubtitleSelection.stableId(request.subtitleTrackId)}
        val embeddedSelected=request.explicitSubtitle && !externalSelected
        if(isCloudQuality || (quality=="original" && subtitleProgressMs!=0L)) {
            val videoItem=item.buildUpon().setSubtitleConfigurations(emptyList()).build()
            var video:androidx.media3.exoplayer.source.MediaSource=if(isCloudQuality)
                androidx.media3.exoplayer.source.CloudHlsSourceFactory.create(videoItem,dataSource,pceExtractors,errorPolicy)
            else sourceFactory.createMediaSource(videoItem)
            if(embeddedSelected && !isCloudQuality && subtitleProgressMs!=0L) {
                // The original media has its own scoped transport. Never send Emby credentials
                // through the cloud CDN client, nor use the cloud URL as the subtitle source.
                val originalData=OkHttpDataSource.Factory(app.http.scopedClient(request.scope,playback=true))
                val originalItem=MediaItem.Builder().setUri(originalSubtitleUrl ?: if(useFallbackRoute) request.fallbackUrl ?: request.stableUrl else request.stableUrl)
                    .setMimeType(request.mimeHint).build()
                val original=DefaultMediaSourceFactory(originalData,extractors).setLoadErrorHandlingPolicy(errorPolicy)
                    .createMediaSource(originalItem)
                val text=androidx.media3.exoplayer.source.FilteringMediaSource(original,C.TRACK_TYPE_TEXT)
                video=androidx.media3.exoplayer.source.FilteringMediaSource(video,
                    setOf(C.TRACK_TYPE_VIDEO,C.TRACK_TYPE_AUDIO,C.TRACK_TYPE_METADATA,C.TRACK_TYPE_CAMERA_MOTION))
                video=androidx.media3.exoplayer.source.MergingMediaSource(video,
                    androidx.media3.exoplayer.source.SubtitleOffsetMediaSource(text,subtitleProgressMs))
            }
            p.setMediaSource(androidx.media3.exoplayer.source.CloudHlsSourceFactory.mergeSubtitles(
                video,item,dataSource,errorPolicy,subtitleProgressMs),lastPosition)
        } else p.setMediaItem(item,if(quality=="original" || transcodeSession?.vod==true) lastPosition else 0)
        p.prepare();p.playWhenReady=resumePlayWhenReady
        progressJob=lifecycleScope.launch {
            var tick=0
            while(isActive) {
                delay(500)
                // Segment prompts also need position while the OSD is hidden.
                position=absolutePosition();duration=if(quality=="original" || isCloudQuality) p.duration.coerceAtLeast(0) else transcodeDuration
                tick++
                if(tick%2==0) {
                    val speed=rateMeter.sample(SystemClock.elapsedRealtime())
                    if(realtimeInfo) {
                        val video=p.videoFormat;val audio=p.audioFormat
                        realtimeSnapshot=PlaybackRealtimeInfo(
                            width=video?.width ?: 0,height=video?.height ?: 0,frameRate=video?.frameRate ?: 0f,
                            videoMime=video?.sampleMimeType,videoBitrate=video?.bitrate?.toLong() ?: 0,
                            streamBitrate=if(isCloudQuality) cloudSession?.variant?.bandwidth ?: 0 else if(quality=="original")
                                mediaContext?.item?.versions?.firstOrNull {it.id==request.mediaSourceId}?.bitrate ?: 0 else 0,
                            audioMime=audio?.sampleMimeType,sampleRate=audio?.sampleRate ?: 0,channels=audio?.channelCount ?: 0,
                            audioBitrate=audio?.bitrate?.toLong() ?: 0,bytesPerSecond=speed,
                            bufferedMs=(p.bufferedPosition-p.currentPosition).coerceAtLeast(0),
                            bufferBytes=bufferAllocator.totalBytesAllocated.toLong(),bufferBudgetBytes=bufferBudget.toLong(),
                            videoDecoder=videoDecoder,audioDecoder=audioDecoder)
                    }
                }
                if(tick%20==0 && rendered) {
                    reporter?.progress(position, !p.isPlaying,duration)
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
        switchingEpisode=true
        lifecycleScope.launch {
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
                    val preferred=AudioPreference.forMedia(settings.mediaAudioPreferences,item,request.audioPreferenceScope)
                    AudioPreference.apply(api.playback(item,false,preferServerStream=settings.preferServerPlayback),preferred)
                        .copy(audioPreferenceScope=request.audioPreferenceScope).let {next->
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
        reporter?.close(lastPosition,reporterHasStarted,duration);reporter=null;reporterHasStarted=false
        progressJob?.cancel();progressJob=null
        old?.release();player=null
        if(!preserveRoute) {
            qualityJob?.cancel();qualityJob=null;releaseTranscode()
            quality="original";transcodeOffset=0;transcodeDuration=0;transcodePausedPosition=null
            cloudDiscoveryGeneration++;cloudDiscoveryJob?.cancel();cloudVariants=emptyList();cloudNotice="";cloudLoading=false;cloudAutoDiscovered=false
        }
        if(next.stableUrl!=request.stableUrl || next.embyItemId!=request.embyItemId) {subtitleProgressMs=0L;originalSubtitleUrl=null}
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
                replacePlayback(next.copy(explicitSubtitle=true,subtitleTrackId="emby-sub:${track.index}",subtitleTitle=track.title),preserveRoute=isCloudQuality)
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
        cloudSession?.url ?: transcodeSession?.url ?: if(useFallbackRoute) request.fallbackUrl?.takeIf {it.isNotBlank()} ?: request.stableUrl else request.stableUrl

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
    private fun absolutePosition():Long = cloudBootstrapTarget ?: transcodePausedPosition ?: player?.let {
        if(quality=="original" || isCloudQuality) it.currentPosition.coerceAtLeast(0) else {
            val windowOffset=if(it.currentTimeline.isEmpty) 0 else it.currentTimeline.getWindow(it.currentMediaItemIndex,Timeline.Window()).positionInFirstPeriodMs
            (transcodeOffset+windowOffset+it.currentPosition.coerceAtLeast(0)).coerceAtMost(transcodeDuration)
        }
    } ?: lastPosition
    private fun seekToAbsolute(target:Long) {
        if(qualitySwitching) return
        if(cloudBootstrapTarget!=null) {cloudBootstrapTarget=target;position=target;return}
        if(quality=="original" || isCloudQuality || transcodeSession?.vod==true) {
            player?.seekTo(target);position=target
            if(transcodePausedPosition!=null) transcodePausedPosition=target
        }
        else changeQuality(quality,target.coerceIn(0,(transcodeDuration-1).coerceAtLeast(0)),player?.playWhenReady ?: false)
    }
    private fun seek(delta:Long) {seekToAbsolute(MediaLogic.seek(absolutePosition(),delta,duration))}
    private fun releaseTranscode() {
        val cloud=cloudSession
        val cloudSource=transcodeSource?.cloudVideo()
        cloudSession=null
        if(cloud!=null) CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {withTimeout(5000) {cloudSource?.stop(cloud)}} catch(_:Exception) {} finally {cancel()}
        }
        val session=transcodeSession ?: return
        val source=transcodeSource
        transcodeSession=null
        // The bounded cleanup survives Activity.onStop; worker TTL is the crash fallback.
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {withTimeout(5000) {source?.control(session,true)}} catch(_:Exception) {} finally {cancel()}
        }
    }
    private fun pauseTranscode() {
        if(isCloudQuality) return
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
        panel="quality";returnControl="quality";qualityNotice=""
        // NAS transcoding remains implemented but is hidden from the quality UI.
    }
    private fun discoverCloudQuality() {
        if(cloudLoading) return
        val source=transcodeSource?.cloudVideo() ?: return
        val generation=++cloudDiscoveryGeneration
        cloudDiscoveryJob?.cancel();cloudLoading=true;cloudNotice="正在查询 115 云端清晰度…"
        cloudDiscoveryJob=lifecycleScope.launch {
            try {
                val variants=source.discover()
                if(generation==cloudDiscoveryGeneration) {cloudVariants=variants;cloudNotice="云端已有清晰度，无需 NAS 重新编码"}
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                if(generation==cloudDiscoveryGeneration) {cloudVariants=emptyList();cloudNotice=if(e is io.github.xudong7587.sunnytv.core.network.SourceException) e.message.orEmpty() else "115 云端响应暂不兼容"}
            } finally {if(generation==cloudDiscoveryGeneration) cloudLoading=false}
        }
    }
    /** Track groups belong to one player. Carry semantic selection, never its old override. */
    private fun rememberSubtitleSelection() {
        val p=player ?: return
        if(C.TRACK_TYPE_TEXT in p.trackSelectionParameters.disabledTrackTypes) {
            request=request.copy(subtitlePreference="none",explicitSubtitle=false,subtitleTrackId="",subtitleTitle="",subtitleOrdinal=-1)
            return
        }
        val tracks=p.currentTracks.groups.filter {it.type==C.TRACK_TYPE_TEXT}
            .flatMap {g->(0 until g.length).map {g to it}}
        val chosen=tracks.firstOrNull {(g,i)->g.isTrackSelected(i)} ?: return
        val f=chosen.first.getTrackFormat(chosen.second)
        if(quality=="original") originalSubtitleUrl=currentPlaybackUrl()
        // Keep embedded identity. Emby cannot always extract subtitles from a STRM;
        // cloud playback reads the original text track instead of guessing a sidecar URL.
        request=request.copy(explicitSubtitle=true,subtitleTrackId=SubtitleSelection.stableId(f.id.orEmpty()),
            subtitleTitle=f.label.orEmpty(),
            subtitlePreference=f.language?.takeIf {it.isNotBlank()} ?: request.subtitlePreference,subtitleOrdinal=-1)
    }
    private fun changeQuality(next:String,target:Long=absolutePosition(),play:Boolean=player?.playWhenReady ?: resumePlayWhenReady) {
        if(qualitySwitching) return
        subtitleAdjustmentJob?.cancel();subtitleAdjustmentJob=null
        qualityJob=lifecycleScope.launch {
            qualitySwitching=true;controls=true;status="正在切换清晰度…"
            val source=transcodeSource
            val cloudSource=source?.cloudVideo()
            var newCloud:CloudVideoSource.Session?=null
            var newTranscode:TranscodeSource.Session?=null
            var committed=false
            var retiredNas=false
            try {
                // The existing NAS worker defaults to one slot. A NAS-to-NAS
                // change must release that slot before requesting its replacement.
                if(next!="original" && !next.startsWith("115:") && transcodeSession!=null) {
                    source?.control(transcodeSession!!,true)
                    transcodeSession=null;retiredNas=true
                }
                // Resolve before releasing the active player, so API failures keep playback intact.
                if(next.startsWith("115:")) newCloud=cloudSource?.create(next.removePrefix("115:"),cloudDelivery) ?: throw IllegalStateException()
                else if(next!="original") newTranscode=source?.create(next,target) ?: throw IllegalStateException()
                // Cloud resolution can take seconds while the old player continues.
                // Commit its current position/state instead of rewinding to menu-click time.
                val commitPosition=if(next.startsWith("115:") && player!=null) absolutePosition() else target
                val commitPlay=if(next.startsWith("115:") && player!=null) player!!.playWhenReady else play
                reporter?.progress(commitPosition,!commitPlay,duration);progressJob?.cancel()
                rememberSubtitleSelection()
                player?.release();player=null;rendered=false
                releaseTranscode()
                cloudSession=newCloud;transcodeSession=newTranscode;committed=true
                lastPosition=commitPosition;resumePlayWhenReady=commitPlay;transcodePausedPosition=null
                transcodeOffset=if(newTranscode?.vod==false) newTranscode.startMs else 0
                transcodeDuration=newTranscode?.durationMs ?: 0
                quality=next;bufferNotice="";clientNetworkNotice="";error="";panel=""
                if(next!="original") useFallbackRoute=false
                createPlayer()
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                qualityNotice=if(e is io.github.xudong7587.sunnytv.core.network.SourceException) e.message.orEmpty() else "清晰度切换失败，请重试或选择原片"
                if(retiredNas && !committed) {
                    progressJob?.cancel();player?.release();player=null
                    quality="original";transcodeOffset=0;transcodeDuration=0;transcodePausedPosition=null
                    lastPosition=target;resumePlayWhenReady=play
                    createPlayer()
                    qualityNotice="NAS 清晰度切换失败，已恢复原片"
                }
                panel="quality";controls=true
                if(player==null) error=qualityNotice
                else android.widget.Toast.makeText(this@PlayerActivity,qualityNotice,android.widget.Toast.LENGTH_LONG).show()
            } finally {
                if(!committed) withContext(NonCancellable) {
                    try {withTimeout(5000) {newCloud?.let {cloudSource?.stop(it)};newTranscode?.let {source?.control(it,true)}}} catch(_:Exception) {}
                }
                qualitySwitching=false
            }
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
        val activePlayer=player
        val activeResizeMode=resizeMode
        DisposableEffect(activeResizeMode,activePlayer) {
            if(activeResizeMode!=AspectRatioFrameLayout.RESIZE_MODE_FIT && activePlayer!=null) {
                frameCropper=VideoFrameCropper({lightPlayerView?.videoSurfaceView as? android.view.SurfaceView}) {area->
                    pictureArea=area;lightPlayerView?.let {applyVideoGeometry(it)}
                }.also {it.start()}
            }
            onDispose {frameCropper?.close();frameCropper=null;pictureArea=PictureArea()}
        }
        fun updateVideoView(view:PlayerView) {
            if(view.player!==activePlayer) view.player=activePlayer
            if(view.resizeMode!=activeResizeMode) view.resizeMode=activeResizeMode
            // Media3 calculates fit/zoom from the decoded size and pixel aspect ratio.
            // Avoid an additional view scale, which would crop a second time.
            view.scaleX=1f;view.scaleY=1f
            val scaling=if(tvPlayback && activeResizeMode==AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
                C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING else C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            val scalingChanged=activePlayer!=null && activePlayer.videoScalingMode!=scaling
            if(scalingChanged)activePlayer!!.videoScalingMode=scaling
            applyVideoGeometry(view)
            // NativeWindow's scaling mode travels with queued decoder buffers. While paused,
            // enqueue the same frame again so changing crop mode also updates the visible buffer.
            if(tvPlayback && scalingChanged && activePlayer!=null && !activePlayer.playWhenReady &&
                activePlayer.isCurrentMediaItemSeekable)activePlayer.seekTo(activePlayer.currentPosition)
            val key=listOf(settings.subtitleEdge,settings.subtitlePosition,settings.subtitleBackground,
                settings.subtitleScaleLevel,settings.subtitleFontChoice,subtitleInk,subtitleAccent,resolvedSubtitleFont)
            if(appliedSubtitle[0]!=key) {
                appliedSubtitle[0]=key
                applySubtitleAppearance(view,settings,subtitleInk,subtitleAccent,resolvedSubtitleFont)
            }
        }
        val activeSkip=SegmentLogic.active(context?.skipSegments.orEmpty(),position,dismissedSegments)
        val remainingMs=(duration-position).coerceAtLeast(0)
        val showNextUp=context?.next!=null && !nextUpDismissed && rendered && duration>0 &&
            remainingMs in 1..90_000 && activeSkip==null
        BoxWithConstraints(Modifier.fillMaxSize().background(if(tvPlayback) Color.Transparent else Color.Black)) {
            val titleWidth=if(tvPlayback) 300.dp else (maxWidth-120.dp).coerceIn(70.dp,300.dp)
            if(tvPlayback) SideEffect {tvPlayerView?.let {updateVideoView(it)}}
            else AndroidView(factory={createVideoView(it)},update={updateVideoView(it)},
                modifier=Modifier.fillMaxSize().testTag("player:video"))

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
                        reporter?.close(lastPosition,reporterHasStarted,duration);reporter=null;reporterHasStarted=false;createPlayer()
                    }
                    PlayerControl("退出播放","exit",initial=retryUsed) {finish()}
                    PlayerControl("选择清晰度","quality",badge=qualityBadge()) {openQualityPanel()}
                }
            }

            if(settings.diagnostics && !realtimeInfo) Text("点击至首帧 ${millis(totalStartupMs)}  ·  源解析 ${millis(sourceStartupMs)}\n引擎首帧 ${millis(firstFrameMs)}  ·  首个响应头 ${millis(headerMs)}  ·  回报失败 $reportFailures\n${request.playMethod} · Media3",
                color=SunnyColors.Accent,fontSize=12.sp,modifier=Modifier.align(Alignment.TopEnd).padding(top=85.dp,end=25.dp).background(Color.Black.copy(.62f),RoundedCornerShape(10.dp)).padding(12.dp))

            if(activeSkip!=null && rendered && error.isBlank() && panel.isBlank()) {
                SkipSegmentPrompt(activeSkip,onSkip={
                    if(SegmentLogic.opensNextEpisode(activeSkip,context?.next!=null)) {
                        switchEpisode(requireNotNull(context?.next))
                    } else {
                        seekToAbsolute(activeSkip.endMs.coerceAtMost(duration.takeIf {it>0} ?: activeSkip.endMs))
                        dismissedSegments=dismissedSegments+activeSkip.id
                    }
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
                    PlayerControl("实时信息","stats",selected=realtimeInfo,labelBelow=true,
                        modifier=Modifier.focusProperties {down=progressFocus}.semantics {
                            role=Role.Switch;toggleableState=if(realtimeInfo) ToggleableState.On else ToggleableState.Off
                            stateDescription=if(realtimeInfo) "开启" else "关闭"
                        }) {realtimeInfo=!realtimeInfo;app.store.setRealtimePlaybackInfo(realtimeInfo)}
                    PlayerControl("音频输出","audio",modifier=Modifier.focusRequester(audioOutputFocus).focusProperties {down=progressFocus},
                        initial=returnControl=="audio-output",labelBelow=true) {returnControl="audio-output";panel="audio-output"}
                    if(!tvPlayback) PlayerControl(if(orientationLocked) "解锁自动旋转" else "锁定当前方向",if(orientationLocked) "lock" else "unlock",selected=orientationLocked,labelBelow=true) {
                        orientationLocked=!orientationLocked
                        if(orientationLocked) {manualOrientation=fixedOrientation();requestedOrientation=manualOrientation}
                        else {val size=player?.videoSize;requestedOrientation=playerOrientation(false,size?.width ?: 0,size?.height ?: 0,size?.pixelWidthHeightRatio ?: 1f)}
                    }
                }
            }
            if(controls && panel.isBlank() && error.isBlank()) {
                if(!realtimeInfo) Box(Modifier.align(Alignment.TopStart).padding(start=if(tvPlayback) 40.dp else 24.dp,top=30.dp).width(titleWidth).height(82.dp)) {PlayerMediaTitle()}
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
                            PlayerControl("清晰度："+qualityLabel(),"quality",badge=qualityBadge(),initial=returnControl=="quality") {openQualityPanel()}
                            if(context?.item?.chapters?.isNotEmpty()==true) PlayerControl("章节","chapters",initial=returnControl=="chapters") {returnControl="chapters";panel="chapters"}
                            if((quality=="original" || isCloudQuality) && (request.embyItemId.isNotBlank() || availableTracks.groups.any {it.type==C.TRACK_TYPE_TEXT} || request.subtitles.isNotEmpty())) PlayerControl("字幕","subtitle",initial=returnControl=="subtitle") {returnControl="subtitle";panel="subtitles"}
                            if(request.sourceTracks.any {it.type=="Audio"} || availableTracks.groups.any {it.type==C.TRACK_TYPE_AUDIO}) PlayerControl("音轨","audio",initial=returnControl=="audio") {returnControl="audio";panel="audio"}
                            if(context?.item?.people?.isNotEmpty()==true) PlayerControl("演职员","cast",initial=returnControl=="cast") {returnControl="cast";panel="cast"}
                            val resizeLabel=when(resizeMode) {AspectRatioFrameLayout.RESIZE_MODE_ZOOM->"裁切";AspectRatioFrameLayout.RESIZE_MODE_FILL->"拉伸";else->"适应"}
                            PlayerControl("画面：$resizeLabel","frame",badge=resizeLabel,initial=returnControl=="frame") {
                                returnControl="frame"
                                resizeMode=when(resizeMode) {AspectRatioFrameLayout.RESIZE_MODE_FIT->AspectRatioFrameLayout.RESIZE_MODE_ZOOM;AspectRatioFrameLayout.RESIZE_MODE_ZOOM->AspectRatioFrameLayout.RESIZE_MODE_FILL;else->AspectRatioFrameLayout.RESIZE_MODE_FIT}
                            }
                            PlayerControl(sleepMinutes?.let {"睡眠 $it 分钟"} ?: "睡眠定时","sleep",initial=returnControl=="sleep") {returnControl="sleep";panel="sleep"}
                            PlayerControl("播放信息","info",initial=returnControl=="info") {returnControl="info";panel="info"}
                            PlayerControl("退出播放","exit") {finish()}
                        }
                    }
                }
            }
            if(realtimeInfo && panel.isBlank()) Text(realtimeSnapshot.text(),color=Color.White.copy(.88f),fontSize=12.sp,lineHeight=17.sp,
                modifier=Modifier.align(Alignment.TopStart).padding(start=if(tvPlayback) 28.dp else 16.dp,
                    top=if(controls && maxWidth<500.dp) 84.dp else 20.dp)
                    .widthIn(max=(maxWidth-32.dp).coerceAtMost(340.dp))
                    .testTag("player:realtime-overlay")
                    .background(Color.Black.copy(.48f),RoundedCornerShape(8.dp)).padding(horizontal=10.dp,vertical=7.dp))
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
            "subtitle-search" -> (mediaContext?.item ?: MediaEntry(request.embyItemId,request.sourceId,request.title,"Video")).let {item->app.store.sources().firstOrNull {it.id==request.sourceId && !it.isUgreen}?.let {config->
                SubtitleSearchDialog(app.emby(config),app.store,item,request.mediaSourceId,{panel=""}) {applyDownloadedSubtitle(it)}
            }}
            "subtitle-timing" -> SubtitleTimingPanel()
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
            (listOf("original" to "原画") + cloudVariants.filter {it.badge in setOf("4K","1080P")}.map {it.qualityId to it.label}).forEach {(id,label)->
                PlayerOption(label,selected=quality==id) {
                    if(id==quality && (id=="original" || cloudSession?.delivery==cloudDelivery)) panel=""
                    else if(!qualitySwitching && (id=="original" || cloudVariants.any {it.qualityId==id})) changeQuality(id)
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
    private fun openSubtitleTiming() {
        rememberSubtitleSelection()
        panel="subtitle-timing"
    }
    private fun adjustSubtitleProgress(deltaMs:Long) {
        subtitleProgressMs=(subtitleProgressMs+deltaMs).coerceIn(-300_000L,300_000L)
        subtitleAdjustmentJob?.cancel()
        // Coalesce repeated clicks. Reprepare at the same video position so a backwards
        // adjustment can recover cues already discarded by Media3, including while paused.
        subtitleAdjustmentJob=lifecycleScope.launch {
            delay(180)
            if(player!=null) {
                rememberSubtitleSelection()
                replacePlayback(request,preserveRoute=true)
            }
        }
    }
    @Composable private fun SubtitleTimingPanel() {
        val enabled=availableTracks.groups.any {it.type==C.TRACK_TYPE_TEXT && it.isSelected} &&
            player?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT)!=true
        SubtitleTimingOverlay(subtitleProgressMs,enabled,
            onAdjust={adjustSubtitleProgress(it)},onClose={panel=""})
    }
    @Composable private fun TrackPanel() {
        val type=if(panel=="audio") C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
        val groups=availableTracks.groups.filter {it.type==type}
        val source by produceState<SourceConfig?>(null,request.sourceId) {
            value=withContext(Dispatchers.IO) {runCatching {app.store.sources().firstOrNull {it.id==request.sourceId}}.getOrNull()}
        }
        PlayerSheet(if(type==C.TRACK_TYPE_AUDIO) "音轨" else "字幕",{panel=""},headerAction=if(type==C.TRACK_TYPE_TEXT) {
            {Text("⚙ 字幕调整",color=Color.White,modifier=Modifier.clickable {openSubtitleTiming()}.padding(10.dp))}
        } else null) {
            if(source?.isUgreen==false && (quality=="original" || isCloudQuality) && type==C.TRACK_TYPE_TEXT && request.embyItemId.isNotBlank()) PlayerOption("查找并下载字幕") {panel="subtitle-search"}
            if(type==C.TRACK_TYPE_TEXT) PlayerOption("关闭字幕",selected=player?.trackSelectionParameters?.disabledTrackTypes?.contains(type)==true) {
                subtitleManuallySelected=true
                player?.let {it.trackSelectionParameters=it.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type,true).build()}
                panel=""
            }
            if(groups.isEmpty()) Text("此媒体没有可选择的轨道",color=Color.White.copy(.7f),fontSize=14.sp)
            LazyColumn(Modifier.heightIn(max=500.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                val options=groups.flatMap {g->(0 until g.length).map {g to it}}.let {entries->
                    if(type==C.TRACK_TYPE_TEXT) entries.sortedBy {(g,i)->val f=g.getTrackFormat(i)
                        SubtitleLanguageOrder.priority(f.language.orEmpty(),f.label.orEmpty())} else entries
                }
                options.forEach { (group,i)->
                    run {
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

