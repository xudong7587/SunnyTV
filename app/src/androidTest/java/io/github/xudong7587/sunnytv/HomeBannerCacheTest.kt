package io.github.xudong7587.sunnytv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.storage.ConfigStore
import io.github.xudong7587.sunnytv.feature.AppModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

class HomeBannerCacheTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()

    @Test fun startupKeepsCachedRandomBannerUntilExplicitRefresh() {
        val server=ServerSocket(0,8,InetAddress.getByName("127.0.0.1"))
        val paths=CopyOnWriteArrayList<String>()
        val worker=Thread {
            while(!server.isClosed) {
                val socket=runCatching {server.accept()}.getOrNull() ?: break
                Thread {socket.use {s->runCatching {
                    s.soTimeout=5000
                    val reader=s.getInputStream().bufferedReader()
                    val path=reader.readLine()?.split(' ')?.get(1) ?: return@runCatching
                    paths+=path
                    while(!reader.readLine().isNullOrEmpty()) { /* consume headers */ }
                    val body=when {
                        path.contains("/Views") -> """{"Items":[{"Id":"library","Name":"测试媒体库","Type":"CollectionFolder","CollectionType":"movies"}]}"""
                        path.contains("/Latest") -> """[{"Id":"new","Name":"后台更新影片","Type":"Movie"}]"""
                        path.contains("SortBy=Random") -> """{"Items":[{"Id":"manual","Name":"手动刷新影片","Type":"Movie"}]}"""
                        else -> """{"Items":[]}"""
                    }.toByteArray()
                    s.getOutputStream().apply {write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray());write(body);flush()}
                }}}.apply {isDaemon=true;start()}
            }
        }.apply {isDaemon=true;start()}
        val store=ConfigStore(rule.activity)
        val settings=store.settings();val sources=store.sources()
        val source=SourceConfig("banner-cache-test",SourceKind.EMBY,"测试","http://127.0.0.1:${server.localPort}","user",secret="fixture")
        val app=rule.activity.application as SunnyApp
        val old=MediaEntry("cached",source.id,"缓存影片","Movie")
        val holder=ViewModelStore()
        lateinit var model:AppModel
        try {
            store.saveSources(listOf(source))
            store.saveSettings(settings.copy(activeSourceId=source.id,heroMode="random",heroAllLibraries=true))
            app.startup.write(source,io.github.xudong7587.sunnytv.core.storage.StartupCache.Snapshot(HomeFeed(latest=listOf(old)),emptyMap(),listOf(old),"random"),app.startup.generation)
            rule.runOnIdle {model=AppModel(app);holder.put("model",model)}
            rule.waitUntil(10000) {model.sourcesReady && model.feeds[source.id]?.latest?.firstOrNull()?.id=="new"}
            rule.runOnIdle {assertEquals(listOf("cached"),model.heroCandidates.map {it.id})}
            assertFalse(paths.any {it.contains("SortBy=Random")})
            rule.runOnIdle {model.refreshCarousel()}
            rule.waitUntil(10000) {!model.carouselRefreshing && model.heroCandidates.firstOrNull()?.id=="manual"}
            assertTrue(paths.any {it.contains("SortBy=Random")})
        } finally {
            rule.runOnIdle {holder.clear()}
            store.saveSources(sources);store.saveSettings(settings)
            server.close();worker.join(1000)
        }
    }
}
