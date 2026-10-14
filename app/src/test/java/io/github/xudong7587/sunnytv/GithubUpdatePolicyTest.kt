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
    private fun entry(version:String,date:String,host:String="github.com")="""<entry><link href="https://$host/xudong7587/SunnyTV/releases/tag/v$version"/><updated>$date</updated></entry>"""
    @Test fun officialFeedIncludesNewestDevelopmentReleaseAndIgnoresOtherHosts() {
        val feed="""<feed xmlns="http://www.w3.org/2005/Atom">${entry("0.1.0","2026-10-10T12:00:00Z")}${entry("0.1.0-dev36.4","2026-10-14T03:00:00Z")}${entry("0.1.0-dev99","2026-10-15T03:00:00Z","evil.invalid")}</feed>"""
        assertEquals("${GithubUpdates.PAGE}/tag/v0.1.0-dev36.4",GithubUpdates.latestFeedPage(feed))
        assertThrows(IllegalArgumentException::class.java) {GithubUpdates.latestFeedPage("<!DOCTYPE feed [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>"+feed)}
        assertThrows(IllegalArgumentException::class.java) {GithubUpdates.latestFeedPage("<feed xmlns='http://www.w3.org/2005/Atom'>"+"<x>".repeat(40)+"</x>".repeat(40)+"</feed>")}
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
