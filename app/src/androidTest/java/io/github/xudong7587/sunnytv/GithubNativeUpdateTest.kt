package io.github.xudong7587.sunnytv

import android.widget.Button
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import io.github.xudong7587.sunnytv.feature.update.GithubUpdateActivity
import org.junit.Assert.*
import org.junit.Test

/** Local live-GitHub smoke test; intentionally excluded from deterministic release CI. */
class GithubNativeUpdateTest {
    private fun views(root:View):List<View> = listOf(root)+(if(root is ViewGroup) (0 until root.childCount).flatMap {views(root.getChildAt(it))} else emptyList())
    @Test fun nativeScreenOpensAndShowsCurrentAndOfficialLatestVersion() {
        ActivityScenario.launch(GithubUpdateActivity::class.java).use {scenario->
            var complete=false
            val deadline=System.currentTimeMillis()+55000
            var text=""
            while(!complete && System.currentTimeMillis()<deadline) {
                scenario.onActivity {a->
                    val nodes=views(a.window.decorView)
                    assertFalse(nodes.any {it is android.webkit.WebView})
                    text=nodes.filterIsInstance<TextView>().joinToString("\n") {it.text}
                    assertTrue(text.contains("当前版本：${BuildConfig.VERSION_NAME}"))
                    complete=text.contains("官方最新版本：") || text.contains("无法检查") || text.contains("检查超时")
                }
                if(!complete) Thread.sleep(100)
            }
            assertTrue(text,complete)
            assertTrue(text,text.contains("官方最新版本："))
            scenario.onActivity {a->
                val nodes=views(a.window.decorView)
                assertTrue(nodes.filterIsInstance<Button>().any {it.text=="重新检查最新版本" && it.isEnabled})
            }
        }
    }
}
