package io.github.xudong7587.sunnytv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.storage.StartupCache
import io.github.xudong7587.sunnytv.core.storage.ConfigStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StartupCacheTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val source=SourceConfig("cache-fixture",SourceKind.EMBY,"fixture","https://fixture.invalid","user",secret="DO_NOT_CACHE")
    @Test fun privateFieldsAreNotSerializedAndSourceIdentityIsEnforced() {
        val cache=StartupCache(context);cache.clear()
        try {
            val entry=MediaEntry("movie",source.id,"测试影片","Movie",path="SECRET_MEDIA_URL",
                externalLinks=listOf(MediaLink("private","SECRET_LINK")),primary=Artwork("movie","Primary","tag"))
            cache.write(source,StartupCache.Snapshot(HomeFeed(latest=listOf(entry)),emptyMap()),cache.generation)
            val restored=cache.read(source)!!.feed.latest.single()
            assertEquals(entry.title,restored.title);assertEquals(entry.primary,restored.primary)
            assertEquals("",restored.path);assertTrue(restored.externalLinks.isEmpty())
            val text=File(context.cacheDir,"startup-v1").listFiles()!!.single().readText()
            listOf("DO_NOT_CACHE","SECRET_MEDIA_URL","SECRET_LINK","fixture.invalid").forEach {assertFalse(text.contains(it))}
            assertNull(cache.read(source.copy(userId="different-user")))
            assertNull(cache.read(source.copy(baseUrl="https://different.invalid")))
        } finally {cache.clear()}
    }
    @Test fun corruptionAndOversizedFilesAreCacheMissesNotCrashes() {
        val cache=StartupCache(context);cache.clear()
        try {
            cache.write(source,StartupCache.Snapshot(HomeFeed(),emptyMap()),cache.generation)
            val file=File(context.cacheDir,"startup-v1").listFiles()!!.single()
            file.writeText("{corrupt");assertNull(cache.read(source))
            file.writeBytes(ByteArray(600*1024));assertNull(cache.read(source))
        } finally {cache.clear()}
    }
    @Test fun delayedWriteCannotResurrectClearedCache() {
        val cache=StartupCache(context);val before=cache.generation;cache.clear()
        cache.write(source,StartupCache.Snapshot(HomeFeed(),emptyMap()),before)
        assertNull(cache.read(source))
    }
    @Test fun cacheAndPerformanceSettingsSurviveRestart() {
        val store=ConfigStore(context);val old=store.settings()
        try {
            PerformancePolicy.cacheSizesMiB.forEach {size->
                store.saveSettings(old.copy(artworkCacheMiB=size,performanceMode="low"))
                assertEquals(size,ConfigStore(context).settings().artworkCacheMiB)
                assertEquals("low",ConfigStore(context).settings().performanceMode)
            }
        } finally {store.saveSettings(old)}
    }
}
