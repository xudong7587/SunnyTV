package io.github.xudong7587.sunnytv

import android.graphics.Bitmap
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
        val titles=listOf("逐光计划","海岸来信","山野之间","夜航星图","风的形状","远方有回声","慢慢生活","潮汐档案")
        val server=ServerSocket(0,16,java.net.InetAddress.getByName("127.0.0.1"))
        val rows=JSONArray(titles.mapIndexed {i,t->JSONObject().put("Id","$i").put("Name",t).put("Type","Movie")
            .put("ProductionYear",2026).put("CommunityRating",8.2+i*.1).put("Overview","一段关于相遇与发现的原创演示故事。这里的片名与画面仅用于展示 SunnyTV 界面。")
            .put("RunTimeTicks",60000000000L).put("ImageTags",JSONObject().put("Primary","original-v2")).put("BackdropImageTags",JSONArray(listOf("original-v2")))})
        thread(isDaemon=true,name="original-showcase") {
            while(!server.isClosed) try {
                val socket=server.accept()
                thread(isDaemon=true) {socket.use {s->
                    val reader=s.getInputStream().bufferedReader();val request=reader.readLine().orEmpty()
                    while(!reader.readLine().isNullOrEmpty()) { }
                    val path=request.substringAfter(" ").substringBefore(" ")
                    val match=Regex("/Items/(\\d+)/Images/(\\w+)").find(path)
                    val bytes=if(match!=null) instrumentation.context.assets.open("showcase/${match.groupValues[1].toInt()%8}-${if(match.groupValues[2]=="Primary") "poster" else "wide"}.png").use {it.readBytes()}
                        else JSONObject().put("Items",rows).put("TotalRecordCount",8).toString().toByteArray()
                    val type=if(match!=null) "image/png" else "application/json"
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
            model.saveSettings(AppSettings(heroMode="latest",showNextUp=false,reduceMotion=true,darkTheme=true,accentIndex=2,heroIntervalSeconds=120))
            val films=titles.mapIndexed {i,t->MediaEntry("$i",source.id,t,"Movie",overview="一段关于相遇与发现的原创演示故事。",year=2026,rating=8.2+i*.1,durationMs=6000000,positionMs=if(i<2) 1600000 else 0,
                genres=listOf("原创演示"),primary=Artwork("$i","Primary","original-v2"),backdrop=Artwork("$i","Backdrop","original-v2",0),thumb=Artwork("$i","Thumb","original-v2"))}
            library=MediaEntry("20",source.id,"原创电影","CollectionFolder",isFolder=true,primary=Artwork("0","Backdrop","original-v2",0))
            val series=library.copy(id="21",title="原创剧集",primary=Artwork("1","Backdrop","original-v2",0))
            val doc=library.copy(id="22",title="自然与远方",primary=Artwork("2","Backdrop","original-v2",0))
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
            File(instrumentation.targetContext.cacheDir,"showcase-$name.png").outputStream().use {check(image.compress(Bitmap.CompressFormat.PNG,100,it))};image.recycle()
        }
        shot("home")
        rule.runOnIdle {model.navigate(Route.Library(library))}
        shot("library")
        rule.onNodeWithTag("library-hero-play").performSemanticsAction(SemanticsActions.RequestFocus)
        rule.onNodeWithTag("library-hero-play").performKeyInput {pressKey(Key.DirectionDown)}
        shot("grid")
        rule.runOnIdle {model.saveSettings(model.settings.copy(darkTheme=false));model.navigate(Route.Home,root=true)}
        shot("light")
        server.close()
    }
}
