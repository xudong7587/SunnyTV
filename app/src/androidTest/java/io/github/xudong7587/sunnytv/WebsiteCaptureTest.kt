package io.github.xudong7587.sunnytv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.Route
import io.github.xudong7587.sunnytv.feature.ui.*
import java.net.ServerSocket
import java.io.File
import kotlin.concurrent.thread
import org.junit.Test
import org.junit.Rule
import org.json.JSONObject
import org.json.JSONArray

/** Captures production Compose screens with original, offline demo media. Never connects to a NAS. */
@OptIn(ExperimentalTestApi::class)
class WebsiteCaptureTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun captureOriginalDemoLibrary() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val titles=listOf("云上邮局","橘子海","月鹿之森","潮汐之外","无声证词","雪渡","墙内")
        val genres=listOf("动画冒险","文艺爱情","史诗奇幻","科幻","黑色犯罪","东方武侠","心理惊悚")
        val stories=listOf("一封没有地址的信，将云上邮局的送信人带往天空深处。每一次出发，都连接着一份等待。","海风吹动窗帘，落日照亮桌上的橘子。一次迟来的重逢，在海边缓缓展开。","月光照进古老森林，一头白鹿引领旅人寻找失落的归途。","海面升起一座银色巨构，潮汐开始流向天空。未知的引力，牵动一段远行。","一名目击者走上狭窄的楼梯。沉默之中，每个人都藏着另一个答案。","雪落渡口，一名行者等待最后一班船。一场无声的告别，留在山水之间。","旧墙后传来熟悉的声响。她伸出手，触到了一段不愿醒来的记忆。")
        val server=ServerSocket(0,16,java.net.InetAddress.getByName("127.0.0.1"))
        val rows=JSONArray(titles.mapIndexed {i,t->JSONObject().put("Id","$i").put("Name",t).put("Type","Movie")
            .put("ProductionYear",2026).put("CommunityRating",8.2+i*.1).put("Overview",stories[i])
            .put("RunTimeTicks",60000000000L).put("ImageTags",JSONObject().put("Primary","seven-v1")).put("BackdropImageTags",JSONArray(listOf("seven-v1")))})
        thread(isDaemon=true,name="original-showcase") {
            while(!server.isClosed) try {
                val socket=server.accept()
                thread(isDaemon=true) {socket.use {s->
                    val reader=s.getInputStream().bufferedReader();val request=reader.readLine().orEmpty()
                    while(!reader.readLine().isNullOrEmpty()) { }
                    val path=request.substringAfter(" ").substringBefore(" ")
                    val match=Regex("/Items/(\\d+)/Images/(\\w+)").find(path)
                    val bytes=if(match!=null) instrumentation.context.assets.open("showcase-seven/${match.groupValues[1].toInt()%titles.size}-${when(match.groupValues[2]) {"Primary"->"poster";"Logo"->"logo";else->"wide"}}.webp").use {it.readBytes()}
                        else JSONObject().put("Items",rows).put("TotalRecordCount",titles.size).toString().toByteArray()
                    val type=if(match!=null) "image/webp" else "application/json"
                    s.getOutputStream().apply {write("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray());write(bytes);flush()}
                }}
            } catch(_:Exception) { }
        }
        lateinit var model:AppModel
        lateinit var library:MediaEntry
        rule.activityRule.scenario.onActivity {activity->
            activity.window.decorView.systemUiVisibility=5894
            val source=SourceConfig("showcase",SourceKind.EMBY,"我的私人影院","http://127.0.0.1:${server.localPort}/emby/","demo",embyProfile=EmbyProfile.STANDARD)
            model=AppModel(activity.application,false,listOf(source))
            model.saveSettings(AppSettings(heroMode="latest",showNextUp=false,reduceMotion=true,darkTheme=false,accentIndex=2,heroIntervalSeconds=120,appearanceMaterial="soft",highPerformanceThemes=true,performanceMode="balanced",appearanceColor="custom",customThemeColor=0xFF111111L))
            val films=titles.mapIndexed {i,t->MediaEntry("$i",source.id,t,"Movie",overview=stories[i],year=2026,rating=8.2+i*.1,durationMs=6000000,positionMs=if(i<2) 1600000 else 0,
                genres=listOf(genres[i]),primary=Artwork("$i","Primary","seven-v1"),backdrop=Artwork("$i","Backdrop","seven-v1",0),thumb=Artwork("$i","Thumb","seven-v1"),logo=Artwork("$i","Logo","seven-v1"))}
            library=MediaEntry("20",source.id,"原创电影","CollectionFolder",isFolder=true,primary=Artwork("0","Backdrop","seven-v1",0))
            val series=library.copy(id="21",title="原创剧集",primary=Artwork("1","Backdrop","seven-v1",0))
            val doc=library.copy(id="22",title="自然与远方",primary=Artwork("2","Backdrop","seven-v1",0))
            model.feeds[source.id]=HomeFeed(libraries=listOf(library,series,doc),latest=films,resume=films.take(2))
            listOf(library,series,doc).forEach {model.libraryLatest[it.key]=films;model.pages[it.key]=MediaPage(films,films.size);model.recommendations[it.key]=films}
            activity.setContent {
                val input=LocalInputModeManager.current
                SideEffect {input.requestInputMode(InputMode.Keyboard)}
                CompositionLocalProvider(LocalAppModel provides model,LocalHandset provides false,LocalTouchFirst provides false) {
                    SunnyTheme(model.settings) {SunnyRoot {_,_->}}
                }
            }
        }
        fun shot(name:String) {
            rule.waitForIdle();Thread.sleep(2400)
            val image=instrumentation.uiAutomation.takeScreenshot()
            File(instrumentation.targetContext.cacheDir,"showcase-seven-$name.png").outputStream().use {check(image.compress(Bitmap.CompressFormat.PNG,100,it))};image.recycle()
        }
        shot("home-soft")
        rule.runOnIdle {model.navigate(Route.Library(library))}
        shot("library-soft")
        rule.onNodeWithTag("library-hero-play").performSemanticsAction(SemanticsActions.RequestFocus)
        rule.onNodeWithTag("library-hero-play").performKeyInput {pressKey(Key.DirectionDown)}
        shot("grid-soft")
        rule.runOnIdle {model.saveSettings(model.settings.copy(appearanceMaterial="glass"));model.navigate(Route.Home,root=true)}
        shot("glass-home")
        rule.runOnIdle {model.navigate(Route.Library(library))}
        rule.onNodeWithTag("library:grid").performTouchInput {swipeUp()}
        shot("glass-scroll")
        rule.onNodeWithTag("library:grid").performTouchInput {swipeUp()}
        shot("glass-posters")
        // Isolated native material study: unchanged TopNav and shader over the supplied artwork.
        // Centering the navigation crosses the colorful building instead of a flat sky.
        rule.activityRule.scenario.onActivity {activity->
            model.navigate(Route.Home,root=true)
            val artwork=instrumentation.context.assets.open("showcase-seven/0-wide.webp").use {BitmapFactory.decodeStream(it)}.asImageBitmap()
            activity.setContent {
                CompositionLocalProvider(LocalAppModel provides model,LocalHandset provides false,LocalTouchFirst provides false) {
                    SunnyTheme(model.settings) {
                        val backdrop=rememberGlassBackdrop(true)
                        val bridge=remember {NavigationBridge()}
                        Box(Modifier.fillMaxSize()) {
                            Image(artwork,null,Modifier.fillMaxSize().captureGlassBackdrop(backdrop),contentScale=ContentScale.Crop)
                            CompositionLocalProvider(LocalGlassBackdrop provides backdrop,LocalNavigationBridge provides bridge,LocalCompact provides false) {
                                Box(Modifier.align(Alignment.Center).fillMaxWidth()) {TopNav(bridge)}
                            }
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("nav:首页").performSemanticsAction(SemanticsActions.RequestFocus)
        shot("glass-study")
        server.close()
    }
}
