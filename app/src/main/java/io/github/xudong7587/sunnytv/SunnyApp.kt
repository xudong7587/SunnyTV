package io.github.xudong7587.sunnytv

import android.app.Application
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.network.SafeHttp
import io.github.xudong7587.sunnytv.core.storage.ConfigStore
import io.github.xudong7587.sunnytv.core.storage.StartupCache
import io.github.xudong7587.sunnytv.source.emby.EmbySource
import io.github.xudong7587.sunnytv.source.clouddrive.WebDavSource
import java.io.File

class SunnyApp : Application() {
    val http by lazy { SafeHttp() }
    val store by lazy { ConfigStore(this) }
    val startup by lazy {StartupCache(this)}
    val lowRamDevice by lazy {
        (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice
    }
    fun lean(settings: AppSettings) = PerformancePolicy.lean(settings.performanceMode,lowRamDevice)
    private val loaders = mutableMapOf<String,ImageLoader>()
    // One global cache budget; adding sources must not multiply the memory budget.
    // Every request's key includes source id, media id, image tag and requested size.
    private val artworkMemory by lazy { MemoryCache.Builder(this).maxSizeBytes(PerformancePolicy.memoryBytes(Runtime.getRuntime().maxMemory(),lowRamDevice)).build() }
    private val artworkDisk by lazy { DiskCache.Builder().directory(File(cacheDir,"artwork"))
        .maxSizeBytes(PerformancePolicy.cacheSize(store.settings().artworkCacheMiB)*1024L*1024L).build() }
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        // Do not let engine exception logs expose signed media URLs. Our diagnostic panel
        // reports numeric error codes and elapsed times instead of exception.toString().
        androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF)
    }
    fun emby(config: SourceConfig) = EmbySource(config,http,store.deviceId)
    fun dav(config: SourceConfig) = WebDavSource(config,http)
    @Synchronized fun images(config: SourceConfig): ImageLoader = loaders.getOrPut(config.id) {
        ImageLoader.Builder(this).okHttpClient(http.scopedClient(emby(config).scope))
            .memoryCache(artworkMemory).diskCache(artworkDisk).crossfade(false).build()
    }
    @Synchronized fun clearArtwork() {
        loaders.values.forEach { it.shutdown() }; loaders.clear()
        artworkMemory.clear(); artworkDisk.clear();startup.clear()
    }
    @Synchronized fun removeArtwork(sourceId: String) {
        loaders.remove(sourceId)?.shutdown()
        // Purge all cached art on account removal, rather than retain private artwork.
        artworkMemory.clear(); artworkDisk.clear();startup.clear()
    }
    @Synchronized override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if(level >= TRIM_MEMORY_RUNNING_LOW) artworkMemory.clear()
    }
}
