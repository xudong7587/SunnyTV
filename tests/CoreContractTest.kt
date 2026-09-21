package io.github.xudong7587.sunnytv.contract

import io.github.xudong7587.sunnytv.source.clouddrive.DavXmlParser
import io.github.xudong7587.sunnytv.core.playback.SessionEvents
import io.github.xudong7587.sunnytv.core.playback.StartupTiming
import io.github.xudong7587.sunnytv.core.playback.PlaybackRecovery
import io.github.xudong7587.sunnytv.core.network.HttpPolicy
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.source.strm.StrmParser

private var passed=0
private var failed=0
private fun test(name:String,block:()->Unit) {
    try {block();passed++;println("PASS  $name")} catch(e:Throwable) {failed++;println("FAIL  $name: ${e.message}")}
}
private fun eq(a:Any?,b:Any?) {check(a==b) {"expected=$b actual=$a"}}
private fun rejects(block:()->Unit) {var threw=false;try {block()} catch(_:IllegalArgumentException) {threw=true};check(threw) {"expected rejection"}}
fun main() = runContractSuite()

fun runContractSuite() {
    passed = 0
    failed = 0
    val stable="http://nas.test:8097/api/play/test_signed_token"
    test("extensionless MediaIndex URL") {eq(StrmParser.parse(stable.toByteArray()),stable)}
    test("UTF-8 BOM + CRLF") {eq(StrmParser.parse("\uFEFF$stable\r\n".toByteArray()),stable)}
    test("surrounding whitespace") {eq(StrmParser.parse("  $stable \n\n".toByteArray()),stable)}
    test("comment line") {eq(StrmParser.parse("#EXTM3U\n$stable\n".toByteArray()),stable)}
    test("signed query ordering preserved") {val u="$stable?a=2&a=1&sign=x%2Fy%2Bz+v";eq(StrmParser.parse(u.toByteArray()),u)}
    test("encoded Chinese path") {val u="https://cdn.test/%E4%B8%AD%E6%96%87%20a.mp4?q=%2F";eq(StrmParser.parse(u.toByteArray()),u)}
    test("encoded hash allowed") {HttpPolicy.validate("https://cdn.test/a%23b.mp4")}
    test("empty STRM rejected") {rejects {StrmParser.parse(byteArrayOf())}}
    test("two URLs rejected") {rejects {StrmParser.parse("$stable\nhttps://cdn.test/video".toByteArray())}}
    test("invalid UTF8 rejected") {rejects {StrmParser.parse(byteArrayOf(0xC3.toByte(),0x28))}}
    test("over-limit text rejected") {rejects {StrmParser.parse(ByteArray(65537))}}
    test("header syntax rejected") {rejects {StrmParser.parse("$stable|Cookie=secret".toByteArray())}}
    test("file URL rejected") {rejects {HttpPolicy.validate("file:///etc/passwd")}}
    test("javascript URL rejected") {rejects {HttpPolicy.validate("javascript:alert(1)")}}
    test("ftp URL rejected") {rejects {HttpPolicy.validate("ftp://example.test/a")}}
    test("embedded credentials rejected") {rejects {HttpPolicy.validate("https://u:p@cdn.test/a")}}
    test("literal # rejected") {rejects {HttpPolicy.validate("https://cdn.test/a#b.mp4")}}
    test("CRLF injection rejected") {rejects {HttpPolicy.validate("https://cdn.test/a\r\nCookie:x")}}
    test("unencoded space rejected") {rejects {HttpPolicy.validate("https://cdn.test/a b.mp4")}}
    test("HTTP LAN accepted") {HttpPolicy.validate("http://192.168.1.2:8097/api/play/sample")}
    test("IPv6 origin accepted") {HttpPolicy.validate("http://[::1]:8096/emby/")}
    test("base prefix retained") {eq(HttpPolicy.serverBase("https://nas.test/reverse/emby"),"https://nas.test/reverse/emby/")}
    test("base query rejected") {rejects {HttpPolicy.serverBase("https://nas.test/emby?api_key=x")}}
    test("same-origin scope") {check(HttpPolicy.isScoped("https://nas.test/emby/Items/1","https://nas.test/emby/"))}
    test("path sibling denied") {check(!HttpPolicy.isScoped("https://nas.test/emby-other/Items/1","https://nas.test/emby/"))}
    test("outside path denied") {check(!HttpPolicy.isScoped("https://nas.test/admin","https://nas.test/emby/"))}
    test("cross-host denied") {check(!HttpPolicy.isScoped("https://cdn.test/emby/a","https://nas.test/emby/"))}
    test("cross-port denied") {check(!HttpPolicy.isScoped("https://nas.test:8443/a","https://nas.test/"))}
    test("cross-scheme denied") {check(!HttpPolicy.isScoped("http://nas.test/a","https://nas.test/"))}
    test("default port equivalence") {check(HttpPolicy.isScoped("https://nas.test:443/a","https://nas.test/"))}
    test("case-insensitive host") {check(HttpPolicy.isScoped("https://NAS.TEST/a","https://nas.test/"))}
    test("credential headers stripped") {eq(HttpPolicy.cleanHeaders(mapOf("Authorization" to "x","X-Emby-Token" to "x","Cookie" to "x","Range" to "bytes=100-")),mapOf("Range" to "bytes=100-"))}
    test("case-insensitive sensitive headers") {eq(HttpPolicy.cleanHeaders(mapOf("x-EMBY-AUTHORIZATION" to "x","COOKIE" to "y")),emptyMap<String,String>())}
    test("relative redirect") {eq(HttpPolicy.redirect("https://nas.test/a/b","../stream",emptySet(),0),"https://nas.test/stream")}
    test("absolute CDN redirect") {eq(HttpPolicy.redirect("https://nas.test/a","https://cdn.test/a?sig=%2B",emptySet(),0),"https://cdn.test/a?sig=%2B")}
    test("HTTP to HTTPS upgrade") {HttpPolicy.redirect("http://nas.test/a","https://cdn.test/b",emptySet(),0)}
    test("HTTPS downgrade denied") {rejects {HttpPolicy.redirect("https://nas.test/a","http://cdn.test/b",emptySet(),0)}}
    test("redirect cycle denied") {rejects {HttpPolicy.redirect("https://nas.test/b","/a",setOf("https://nas.test/a"),1)}}
    test("self redirect denied") {rejects {HttpPolicy.redirect("https://nas.test/a","/a",emptySet(),0)}}
    test("redirect budget") {rejects {HttpPolicy.redirect("https://nas.test/a","/b",emptySet(),8)}}
    test("last redirect within budget") {HttpPolicy.redirect("https://nas.test/a","/b",emptySet(),7)}
    test("STRM extension case insensitive") {check(StrmParser.looksLikeStrm("https://nas.test/a.STRM?x=1"))}
    test("MediaIndex not mistaken for STRM") {check(!StrmParser.looksLikeStrm(stable))}
    test("STRM recursive cycle") {rejects {StrmParser.checkRecursion(stable,setOf(stable))}}
    test("STRM recursion limit") {rejects {StrmParser.checkRecursion(stable,setOf("a","b","c"))}}
    test("known mime mapping") {eq(MediaLogic.mime("mkv"),"video/x-matroska")}
    test("STRM mime left unspecified") {eq(MediaLogic.mime("strm"),null)}
    test("unknown mime not forced HLS") {eq(MediaLogic.mime(""),null)}
    test("progress clamped above") {eq(MediaLogic.progress(200,100),1f)}
    test("progress clamped below") {eq(MediaLogic.progress(-2,100),0f)}
    test("unknown duration") {eq(MediaLogic.progress(10,0),0f)}
    test("seek below zero") {eq(MediaLogic.seek(500,-1000,30000),0L)}
    test("seek beyond end") {eq(MediaLogic.seek(500,40000,30000),30000L)}
    test("remaining rounds up") {eq(MediaLogic.remainingMinutes(0,60001),2L)}
    val primary=Artwork("lib","Primary","p");val thumb=Artwork("lib","Thumb","t");val back=Artwork("lib","Backdrop","b",0)
    val lib=MediaEntry("lib","source1","电影","CollectionFolder",primary=primary,thumb=thumb,backdrop=back,isFolder=true)
    test("native library Primary wins") {eq(MediaLogic.libraryArtwork(lib),primary)}
    test("library falls back to Thumb") {eq(MediaLogic.libraryArtwork(lib.copy(primary=null)),thumb)}
    test("library falls back to Backdrop") {eq(MediaLogic.libraryArtwork(lib.copy(primary=null,thumb=null)),back)}
    test("wide card prefers Thumb") {eq(MediaLogic.wideArtwork(lib),thumb)}
    test("cross-source keys never collide") {check(lib.key!=lib.copy(sourceId="source2").key)}
    test("folders not playable") {check(!lib.isPlayable)}
    test("movie playable") {check(MediaEntry("1","s","电影","Movie").isPlayable)}

    test("scope literal traversal denied") {check(!HttpPolicy.isScoped("https://nas.test/emby/../admin","https://nas.test/emby/"))}
    test("scope encoded traversal denied") {check(!HttpPolicy.isScoped("https://nas.test/emby/%2e%2e/admin","https://nas.test/emby/"))}
    test("scope double encoded traversal denied") {check(!HttpPolicy.isScoped("https://nas.test/emby/%252e%252e/admin","https://nas.test/emby/"))}
    test("scope encoded separator traversal denied") {check(!HttpPolicy.isScoped("https://nas.test/emby/a%2F..%2F..%2Fadmin","https://nas.test/emby/"))}
    test("percent in genuine filename remains scoped") {check(HttpPolicy.isScoped("https://nas.test/dav/100%25.mp4","https://nas.test/dav/"))}
    test("base with dot segment denied") {rejects {HttpPolicy.serverBase("https://nas.test/emby/../")}}
    test("invalid port denied") {rejects {HttpPolicy.validate("https://nas.test:99999/a")}}
    test("positive seek overflow saturates") {eq(MediaLogic.seek(Long.MAX_VALUE-1,10,0),Long.MAX_VALUE)}
    test("negative seek overflow clamps") {eq(MediaLogic.seek(1,Long.MIN_VALUE,1000),0L)}
    test("remaining time cannot overflow") {check(MediaLogic.remainingMinutes(0,Long.MAX_VALUE)>0)}
    test("relative API URL retains reverse prefix") {eq(HttpPolicy.resolveReference("https://nas.test/reverse/emby/","Videos/1/stream?q=%2B"),"https://nas.test/reverse/emby/Videos/1/stream?q=%2B")}
    test("root relative API URL is not silently rewritten") {eq(HttpPolicy.resolveReference("https://nas.test/reverse/emby/","/emby/stream"),"https://nas.test/emby/stream")}
    test("HTTP reference preserves signed parameters") {eq(HttpPolicy.resolveReference("https://nas.test/","https://cdn.test/v?x=2&x=1&s=a%2Fb+v"),"https://cdn.test/v?x=2&x=1&s=a%2Fb+v")}

    fun prop(href:String,name:String="Movie",kind:String="",status:String="200 OK") = """<d:response><d:href>$href</d:href><d:propstat><d:prop><d:displayname>$name</d:displayname><d:resourcetype>$kind</d:resourcetype><d:getcontentlength>120</d:getcontentlength></d:prop><d:status>HTTP/1.1 $status</d:status></d:propstat></d:response>"""
    fun dav(vararg rows:String) = """<d:multistatus xmlns:d="DAV:">${rows.joinToString("")}</d:multistatus>""".toByteArray()
    fun parseDav(bytes:ByteArray) = DavXmlParser.parse(bytes,"https://nas.test/dav/","https://nas.test/dav/")
    test("DAV 207 XML self omitted") {eq(parseDav(dav(prop("/dav/"),prop("/dav/a.mp4"))).size,1)}
    test("DAV directory normalized trailing slash") {eq(parseDav(dav(prop("/dav/movies","电影","<d:collection/>"))).single().url,"https://nas.test/dav/movies/")}
    test("DAV collection recognized") {check(parseDav(dav(prop("/dav/movies","电影","<d:collection/>"))).single().directory)}
    test("DAV relative href resolves") {eq(parseDav(dav(prop("movie.mp4"))).single().url,"https://nas.test/dav/movie.mp4")}
    test("DAV cross origin href ignored") {eq(parseDav(dav(prop("https://evil.test/video"))).size,0)}
    test("DAV outside configured root ignored") {eq(parseDav(dav(prop("/private/video"))).size,0)}
    test("DAV failed propstat ignored") {eq(parseDav(dav(prop("/dav/a.mp4",status="404 Not Found"))).size,0)}
    test("DAV duplicate href removed") {eq(parseDav(dav(prop("/dav/a.mp4"),prop("/dav/a.mp4"))).size,1)}
    test("DAV file size parsed") {eq(parseDav(dav(prop("/dav/a.mp4"))).single().bytes,120L)}
    test("DAV displayname escaped entities") {eq(parseDav(dav(prop("/dav/a.mp4","A &amp; B"))).single().name,"A & B")}
    test("DAV percent UTF8 filename fallback") {eq(parseDav(dav(prop("/dav/%E4%B8%AD%E6%96%87.mp4",name=""))).single().name,"中文.mp4")}
    test("DAV literal plus preserved") {eq(parseDav(dav(prop("/dav/A+B.mp4",name=""))).single().name,"A+B.mp4")}
    test("DAV HTML login page rejected") {rejects {parseDav("<html><body>Login</body></html>".toByteArray())}}
    test("DAV DOCTYPE denied") {rejects {parseDav("<!DOCTYPE foo [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><d:multistatus xmlns:d='DAV:'/>".toByteArray())}}
    test("DAV XML byte limit") {rejects {parseDav(ByteArray(DavXmlParser.MAX_XML_BYTES+1))}}
    test("DAV invalid directory scope") {rejects {DavXmlParser.parse(dav(),"https://other.test/","https://nas.test/dav/")}}
    test("DAV no-ns response is not accepted") {rejects {parseDav("<multistatus/>".toByteArray())}}
    test("DAV XML empty collection is valid") {eq(parseDav(dav()).size,0)}
    test("DAV merges successful propstats") {
        val row="""<d:response><d:href>/dav/folder</d:href><d:propstat><d:prop><d:displayname>合集</d:displayname></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"""
        val node=parseDav(dav(row)).single();eq(node.name,"合集");check(node.directory)
    }
    test("reports ignore progress before start") {val q=SessionEvents();q.progress(50,false);eq(q.poll(),null)}
    test("reports start precedes progress") {val q=SessionEvents();q.begin(10,false);q.progress(50,false);eq(q.poll()?.event,"Playing");eq(q.poll()?.event,"Playing/Progress")}
    test("reports retain latest heartbeat only") {val q=SessionEvents();q.begin(0,false);q.progress(1,false);q.progress(2,true);q.poll();eq(q.poll()?.positionMs,2L);eq(q.poll(),null)}
    test("reports stop cannot discard start") {val q=SessionEvents();q.begin(0,false);q.progress(2,true);q.stop(4);eq(q.poll()?.event,"Playing");eq(q.poll()?.event,"Playing/Stopped");eq(q.poll(),null)}
    test("reports ignore heartbeat after stop") {val q=SessionEvents();q.begin(0,false);q.stop(10);q.progress(11,false);q.poll();eq(q.poll()?.positionMs,10L);eq(q.poll(),null)}
    test("reports duplicate start idempotent") {val q=SessionEvents();q.begin(1,false);q.begin(2,false);eq(q.poll()?.positionMs,1L);eq(q.poll(),null)}
    test("reports unplayed cancellation sends nothing") {val q=SessionEvents();q.stop(1);eq(q.poll(),null)}
    test("reports positions clamped") {val q=SessionEvents();q.begin(-4,false);eq(q.poll()?.positionMs,0L)}
    test("reports duplicate stop idempotent") {val q=SessionEvents();q.begin(0,false);q.poll();q.stop(1);q.stop(2);eq(q.poll()?.positionMs,1L);eq(q.poll(),null)}

    test("startup total includes source and handoff") {eq(StartupTiming.measure(100,300,350,900).totalMs,800L)}
    test("startup source interval") {eq(StartupTiming.measure(100,300,350,900).sourceMs,200L)}
    test("startup activity handoff interval") {eq(StartupTiming.measure(100,300,350,900).handoffMs,50L)}
    test("startup engine interval is independent") {eq(StartupTiming.measure(100,300,350,900).engineMs,550L)}
    test("startup unknown source does not invent a total") {eq(StartupTiming.measure(-1,-1,350,900).totalMs,null)}
    test("startup retry measures current engine only") {eq(StartupTiming.measure(-1,-1,350,900).engineMs,550L)}
    test("startup invalid ordering hides total") {eq(StartupTiming.measure(500,200,350,900).totalMs,null)}
    test("startup frame before engine is unavailable") {eq(StartupTiming.measure(100,200,350,300).engineMs,null)}
    test("startup zero timestamp is valid") {eq(StartupTiming.measure(0,0,0,0).totalMs,0L)}
    test("startup unknown marks are unavailable") {eq(StartupTiming.measure(-1,-1,-1,-1).totalMs,null)}
    test("startup no negative duration") {eq(StartupTiming.between(5,4),null)}
    test("startup large monotonic timestamps") {eq(StartupTiming.between(Long.MAX_VALUE-10,Long.MAX_VALUE),10L)}

    // dev22: subtitle appearance is pure policy shared by the player and the settings preview.
    test("subtitle scale clamps to the table") {eq(SubtitleAppearance.scale(-3),.75f);eq(SubtitleAppearance.scale(9),1.3f)}
    test("subtitle edge falls back to outline") {eq(SubtitleAppearance.edge("bogus"),SubtitleAppearance.EDGE_OUTLINE)}
    test("subtitle background falls back to black") {eq(SubtitleAppearance.background("nope"),SubtitleAppearance.BACKGROUND_BLACK)}
    test("subtitle position maps to bottom padding") {eq(SubtitleAppearance.bottomPaddingFraction(SubtitleAppearance.POSITION_TOP),.5f)}
    test("subtitle standard position stays near the bottom") {eq(SubtitleAppearance.bottomPaddingFraction(SubtitleAppearance.POSITION_STANDARD),.08f)}

    // dev26: playback gets a longer first-byte budget than API/image/STRM traffic, one bounded retry,
    // and host-only diagnostics that cannot leak a signed URL.
    test("playback budget exceeds the api budget") {
        check(HttpPolicy.PLAYBACK_READ_TIMEOUT_SECONDS>HttpPolicy.READ_TIMEOUT_SECONDS)
        check(HttpPolicy.PLAYBACK_CONNECT_TIMEOUT_SECONDS>=HttpPolicy.CONNECT_TIMEOUT_SECONDS)
    }
    test("playback budget stays bounded") {check(HttpPolicy.PLAYBACK_READ_TIMEOUT_SECONDS<=120)}
    test("host label keeps an explicit port") {eq(HttpPolicy.hostLabel("https://cdn.test:8443/a/b.mp4?sig=s%2Fcret"),"cdn.test:8443")}
    test("host label drops the default port") {eq(HttpPolicy.hostLabel("https://nas.test/emby/"),"nas.test")}
    test("host label lowercases the host") {eq(HttpPolicy.hostLabel("http://NAS.Test:8096/emby/"),"nas.test:8096")}
    test("host label refuses non-http input") {eq(HttpPolicy.hostLabel("file:///etc/passwd"),null)}
    test("host label refuses embedded credentials") {eq(HttpPolicy.hostLabel("https://u:p@cdn.test/a"),null)}
    test("host label tolerates empty input") {eq(HttpPolicy.hostLabel(""),null)}
    test("retry allowed once before the first frame") {check(PlaybackRecovery.shouldRetry(0,2001,false,listOf("SocketTimeoutException")))}
    test("retry allowed for a bare timeout code") {check(PlaybackRecovery.shouldRetry(0,2002,false,emptyList()))}
    test("retry denied after the first frame") {check(!PlaybackRecovery.shouldRetry(0,2001,true,listOf("SocketTimeoutException")))}
    test("retry denied after the single automatic attempt") {check(!PlaybackRecovery.shouldRetry(1,2001,false,listOf("SocketTimeoutException")))}
    test("dns failure is not retried") {check(!PlaybackRecovery.shouldRetry(0,2001,false,listOf("UnknownHostException")))}
    test("tls failure is not retried") {check(!PlaybackRecovery.shouldRetry(0,2002,false,listOf("SSLHandshakeException")))}
    test("bad http status is not retried") {check(!PlaybackRecovery.shouldRetry(0,2004,false,listOf("InvalidResponseCodeException")))}
    test("unrelated failure is not retried") {check(!PlaybackRecovery.shouldRetry(0,2000,false,listOf("IOException")))}
    test("retry budget is exactly one") {eq(PlaybackRecovery.MAX_AUTO_RETRIES,1)}
    test("waiting for the response is named") {eq(PlaybackRecovery.stageHint(1,false),"等待响应（连接或首字节）")}
    test("streaming the body is named") {eq(PlaybackRecovery.stageHint(2,false),"读取数据流")}
    test("wrapped response call is named when the type is lost") {eq(PlaybackRecovery.stageHint(null,true),"等待响应（连接或首字节）")}
    test("unknown stage stays unnamed") {eq(PlaybackRecovery.stageHint(null,false),null)}
    test("route label marks the emby host") {eq(PlaybackRecovery.routeLabel("https://nas.test/Videos/1/stream?sig=x","https://nas.test/emby/"),"nas.test（Emby 本机）")}
    test("route label marks a direct media source") {eq(PlaybackRecovery.routeLabel("http://cdn.test:5244/d/115/movie.mp4?sig=x","https://nas.test/emby/"),"cdn.test:5244（直连媒体源，不是 Emby）")}
    test("route label marks a source without emby") {eq(PlaybackRecovery.routeLabel("https://dav.test/movie.strm",null),"dav.test（独立媒体来源）")}
    test("route label needs a usable url") {eq(PlaybackRecovery.routeLabel(null,"https://nas.test/emby/"),null)}

    println("\nRESULT: $passed passed / $failed failed")
    check(failed==0) {"Contract tests failed"}
}
