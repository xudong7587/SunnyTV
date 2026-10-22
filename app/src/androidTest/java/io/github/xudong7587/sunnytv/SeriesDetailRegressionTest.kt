package io.github.xudong7587.sunnytv

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.Route
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeriesDetailRegressionTest {
    // Use the real declared host so this same fixture can validate the signed release APK.
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun phoneSeriesDetailsSurviveLoadingSeasonsAndReturningAcrossMaterials() {
        lateinit var model:AppModel
        rule.activityRule.scenario.onActivity {activity->
            model=AppModel(activity.application,false)
            model.saveSettings(AppSettings(darkTheme=false,performanceMode="standard",highPerformanceThemes=true))
            activity.setContent {CompositionLocalProvider(LocalAppModel provides model,LocalHandset provides true,LocalTouchFirst provides true) {
                SunnyTheme(model.settings) {Box(Modifier.width(390.dp).height(780.dp)) {SunnyRoot(onExit={fail("Clicking media must not finish the activity")}) {_,_->}}}
            }}
        }
        for(material in listOf("clear","glass","frosted","aurora")) for(title in listOf("龙之家族","豪斯医生","九门")) {
            // Each opening case gets an independent identity. Reusing a key deliberately restores
            // that page's previous bottom scroll position, which is a different behavior to test.
            val series=MediaEntry("series-$material-$title","fixture",title,"Series",overview="剧情简介。".repeat(30),rating=8.6)
            rule.runOnIdle {
                model.saveSettings(model.settings.copy(appearanceMaterial=material))
                model.navigate(Route.Detail(series))
            }
            rule.waitForIdle()
            rule.onNodeWithTag("detail-resume").assertIsDisplayed()
            rule.runOnIdle {
                val cast=(1..20).map {MediaPerson("person-$it","演员 $it","角色 $it",null)}
                val resource=MediaVersion("version","测试版本",1920,1080,"SDR",1000,1000,"mp4",emptyList())
                model.details[series.key]=series.copy(people=listOf(cast.first())+cast,
                    versions=listOf(resource,resource.copy(name="同一资源的重复记录")))
                val seasons=(1..8).map {MediaEntry("season-$title-$it","fixture","第 $it 季","Season",seriesId=series.id)}
                val similar=(1..12).map {MediaEntry("similar-$it","fixture","相似剧集 $it","Series",rating=8.0)}
                model.children[series.key]=listOf(seasons.first())+seasons
                model.similar[series.key]=listOf(similar.first())+similar
            }
            rule.waitForIdle()
            rule.onNodeWithTag("detail-resume").assertIsDisplayed()
            rule.onNodeWithTag("detail:viewport").performScrollToNode(hasText("选择季"))
            rule.onNodeWithText("选择季").assertIsDisplayed()
            rule.onNodeWithTag("detail:viewport").performScrollToNode(hasText("演员表"))
            rule.onNodeWithText("演员表").assertIsDisplayed()
            rule.onNodeWithTag("detail:viewport").performScrollToNode(hasText("相似推荐"))
            rule.onNodeWithText("相似推荐").assertIsDisplayed()
            rule.runOnIdle {assertFalse(rule.activity.isFinishing);model.back()}
            rule.waitForIdle()
        }
    }
}
