package io.github.xudong7587.sunnytv

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.Route
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/** Exercise the real coordinator and Emby adapter, including manual-choice priority. */
class LibraryAudioPlaybackTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun libraryPreferenceReachesPlaybackInfoButExplicitTrackWins() {
        val server=MockWebServer();val requests=CopyOnWriteArrayList<JSONObject>()
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.path.orEmpty().contains("PlaybackInfo")) {
                    requests.add(JSONObject(request.body.readUtf8()))
                    return MockResponse().setBody("""{"MediaSources":[{"Id":"source","Container":"mkv","SupportsDirectStream":true,"MediaStreams":[{"Index":2,"Type":"Audio","Language":"eng","Codec":"aac"},{"Index":9,"Type":"Audio","Language":"jpn","Codec":"aac"}]}]}""")
                }
                return MockResponse().setBody("""{"Items":[],"TotalRecordCount":0}""")
            }
        }
        server.start()
        val baseUrl=server.url("/").toString()
        lateinit var model:AppModel
        val library=MediaEntry("library","fixture","示例媒体库","CollectionFolder",isFolder=true)
        val tracks=listOf(MediaTrack(2,"Audio","eng","英语","aac"),MediaTrack(9,"Audio","jpn","日语","aac"))
        val item=MediaEntry("episode","fixture","示例单集","Episode",seriesId="series",tracks=tracks)
        try {
            rule.activityRule.scenario.onActivity {a->
                model=AppModel(a.application,false,listOf(SourceConfig("fixture",SourceKind.EMBY,"fixture",baseUrl,"fixture-user")))
                model.saveSettings(model.settings.copy(mediaAudioPreferences=mapOf("fixture:series" to "ja",item.key to "fr",library.key to "fr","fixture:unrelated-series" to "yue")))
                model.navigate(Route.Library(library));model.details[item.key]=item
            }
            fun play(target:MediaEntry=item):PlaybackRequest {
                val result=AtomicReference<PlaybackRequest?>()
                rule.runOnIdle {model.play(target,ready={result.set(it)})}
                rule.waitUntil(10_000) {result.get()!=null || (!model.busy && model.message.isNotBlank())}
                assertNotNull(model.message,result.get());return result.get()!!
            }
            val preferred=play()
            assertEquals("ja",preferred.audioPreference);assertEquals(9,preferred.audioStreamIndex)
            assertEquals(1,preferred.audioOrdinal);assertEquals(9,requests.last().getInt("AudioStreamIndex"))
            rule.runOnIdle {model.navigate(Route.Detail(item));model.selectedAudio[item.key]=tracks.first()}
            val explicit=play()
            assertEquals(2,explicit.audioStreamIndex);assertEquals("eng",explicit.audioLanguage)
            assertEquals("ja",explicit.audioPreference)
            assertEquals(2,requests.last().getInt("AudioStreamIndex"))
            rule.runOnIdle {model.navigate(Route.Detail(MediaEntry("unrelated-series","fixture","其他剧集","Series")))}
            val other=play(item.copy(id="other-film",type="Movie",seriesId=""))
            assertEquals("default",other.audioPreference)
            assertEquals(-1,other.audioStreamIndex)
        } finally {server.shutdown()}
    }
}
