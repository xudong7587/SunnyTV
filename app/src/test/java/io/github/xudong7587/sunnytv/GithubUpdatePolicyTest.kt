package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.feature.update.GithubUpdates
import org.junit.Assert.*
import org.junit.Test

class GithubUpdatePolicyTest {
    @Test fun releaseBrowserStaysOnOfficialRepository() {
        assertTrue(GithubUpdates.allowedPage(GithubUpdates.PAGE))
        assertTrue(GithubUpdates.allowedPage("https://github.com/xudong7587/SunnyTV/releases/tag/v0.1.0-dev34.4"))
        assertFalse(GithubUpdates.allowedPage("https://github.com/other/project/releases"))
        assertFalse(GithubUpdates.allowedPage("http://github.com/xudong7587/SunnyTV/releases"))
        assertFalse(GithubUpdates.allowedPage("https://github.com.evil.invalid/xudong7587/SunnyTV/releases"))
        assertFalse(GithubUpdates.allowedPage("https://user:secret@github.com/xudong7587/SunnyTV/releases"))
    }
    private fun entry(version:String,date:String,prerelease:Boolean=false,draft:Boolean=false)=org.json.JSONObject()
        .put("tag_name","v$version").put("html_url","${GithubUpdates.PAGE}/tag/v$version")
        .put("published_at",date).put("prerelease",prerelease).put("draft",draft)
        .put("assets",org.json.JSONArray(listOf("BUILD_INFO.txt","SIGNING_VALIDATION.json","SunnyTV-v$version.apk").map {org.json.JSONObject().put("name",it)}))
    @Test fun newestPublishedInstallableReleaseIncludesPrereleasesAndIgnoresDrafts() {
        val stable=entry("0.1.0","2026-10-10T12:00:00Z")
        val latest=entry("0.1.0-dev36.2","2026-10-14T02:00:00Z",true)
        val draft=entry("0.1.0-dev37","2026-10-15T02:00:00Z",true,true)
        val incomplete=entry("0.1.0-dev38","2026-10-16T02:00:00Z",true).put("assets",org.json.JSONArray())
        assertEquals("v0.1.0-dev36.2",GithubUpdates.latestEntry(org.json.JSONArray(listOf(draft,stable,incomplete,latest))).getString("tag_name"))
    }
    @Test fun manifestVersionMustMatchOfficialTagAndApk() {
        val v="0.1.0-dev36.2"
        val text="version=$v\nversionCode=49\napplicationId=io.github.xudong7587.sunnytv.debug\ndebuggable=false\n"
        val page="${GithubUpdates.PAGE}/tag/v$v"
        val apk="https://github.com/xudong7587/SunnyTV/releases/download/v$v/SunnyTV-v$v.apk"
        assertEquals(49L,GithubUpdates.parseReleaseInfo("v$v",page,apk,text).versionCode)
        assertThrows(IllegalArgumentException::class.java) {GithubUpdates.parseReleaseInfo("v$v",page,apk.replace(v,"0.1.0-dev36"),text)}
        assertThrows(IllegalArgumentException::class.java) {GithubUpdates.parseReleaseInfo("v$v",page,apk,text.replace("debuggable=false","debuggable=true"))}
        assertThrows(IllegalArgumentException::class.java) {GithubUpdates.parseReleaseInfo("v$v",page,apk,text.replace("versionCode=49","versionCode=0"))}
    }
}
