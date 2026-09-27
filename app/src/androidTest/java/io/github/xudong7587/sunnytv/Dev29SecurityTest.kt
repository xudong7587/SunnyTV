package io.github.xudong7587.sunnytv

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.ui.*
import io.github.xudong7587.sunnytv.source.clouddrive.DavXmlParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Dev29SecurityTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()

    @Test fun searchHidesOtherAccountsAndUpdatesWhenExistingCacheValuesChange() {
        lateinit var model: AppModel
        rule.activityRule.scenario.onActivity {activity ->
            val b=SourceConfig("B",SourceKind.EMBY,"B","https://b.invalid/")
            val a=SourceConfig("A",SourceKind.EMBY,"A","https://a.invalid/")
            model=AppModel(activity.application,false,listOf(b,a))
            model.pages["search:A"]=MediaPage(listOf(MediaEntry("a","A","AlphaPrivate","Movie")),1)
            model.pages["search:B"]=MediaPage(listOf(MediaEntry("b","B","AlphaOld","Movie")),1)
            activity.setContent {
                CompositionLocalProvider(LocalAppModel provides model) {
                    SunnyTheme(model.settings) {SearchScreen {_,_->}}
                }
            }
        }
        rule.onNode(hasSetTextAction()).performTextInput("Alpha")
        rule.onNodeWithText("AlphaPrivate").assertDoesNotExist()
        rule.onAllNodesWithText("AlphaOld").onFirst().assertExists()
        rule.runOnIdle {
            model.pages["search:B"]=MediaPage(listOf(MediaEntry("new","B","AlphaNew","Movie")),1)
        }
        rule.onNodeWithText("AlphaOld").assertDoesNotExist()
        rule.onAllNodesWithText("AlphaNew").onFirst().assertExists()
        rule.onNodeWithText("AlphaPrivate").assertDoesNotExist()
    }

    @Test fun androidParserRejectsSixMegabyteElementBombWithoutExhaustingHeap() {
        val xml=("<d:multistatus xmlns:d='DAV:'>"+"<x/>".repeat(1_500_000)+"</d:multistatus>").toByteArray()
        val error=runCatching {DavXmlParser.parse(xml,"https://dav.invalid/","https://dav.invalid/")}.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals("WebDAV XML 元素过多",error?.message)
    }

    @Test fun androidParserPreservesDavFoldersAndNamespaceChecks() {
        val xml="""<d:multistatus xmlns:d="DAV:"><d:response><d:href>/dav/folder</d:href><d:propstat>
            <d:prop><d:displayname>A &amp; B</d:displayname><d:resourcetype><d:collection/></d:resourcetype></d:prop>
            <d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>"""
        val node=DavXmlParser.parse(xml.toByteArray(),"https://dav.invalid/dav/","https://dav.invalid/dav/").single()
        assertEquals("A & B",node.name);assertTrue(node.directory)
        assertEquals("https://dav.invalid/dav/folder/",node.url)
    }
}
