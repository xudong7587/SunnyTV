#include "sunny/json.hpp"
#include "sunny/media.hpp"
#include "sunny/navigation.hpp"
#include "sunny/touch.hpp"
#include <curl/curl.h>
#include <functional>
#include <iostream>
#include <stdexcept>

using namespace sunny;
int checks = 0;
void check(bool ok, const char* label) { ++checks; if (!ok) throw std::runtime_error(label); }
void rejects(const std::function<void()>& f, const char* label) { bool rejected = false; try { f(); } catch (...) { rejected = true; } check(rejected, label); }
class Mock : public Transport {
public:
    std::vector<Request> requests;
    std::vector<Response> replies;
    Response fetch(const Request& r, const std::atomic_bool*) override { requests.push_back(r); if (replies.empty()) throw std::runtime_error("Unexpected HTTP request"); auto v = replies.front(); replies.erase(replies.begin()); return v; }
    void reply(const std::string& body, long status = 200) { replies.push_back({status, body, "", {}}); }
};
int main() {
    curl_global_init(CURL_GLOBAL_DEFAULT);
    try {
        TouchTracker touch;
        touch.begin(1,{40,80});
        check(touch.end(1,{44,83}).gesture==Gesture::Tap,"finger tap activates");
        touch.begin(2,{400,500});
        auto drag=touch.move(2,{402,450});
        check(drag.gesture==Gesture::Scroll&&drag.dy==-50,"finger vertical drag scrolls");
        check(touch.end(2,{402,450}).gesture==Gesture::None,"drag release does not tap media");
        touch.begin(3,{800,400});touch.move(3,{680,403});
        check(touch.end(3,{680,403}).gesture==Gesture::SwipeLeft,"horizontal finger swipe");
        touch.begin(4,{500,300});touch.begin(5,{900,300});
        check(touch.end(5,{900,300}).gesture==Gesture::None&&touch.active(),"second finger cannot trigger first finger action");
        check(touch.end(4,{500,300}).gesture==Gesture::Tap,"original finger remains tracked");
        touch.begin(6,{50,50});touch.cancel();
        check(touch.end(6,{50,50}).gesture==Gesture::None,"cancelled gesture ignored");
        check(Bounds{10,20,100,50}.contains({10,20})&&!Bounds{10,20,100,50}.contains({110,70}),"touch hit bounds are precise");
        NavigationRepeat nav;
        check(nav.update(0, 8000, -8000, 0) == 0, "left stick drift ignored");
        check(nav.update(0, 20000, 0, 10) == NavRight, "left stick right initial press");
        check(nav.update(0, 14000, 0, 100) == 0, "stick boundary hysteresis");
        check(nav.update(0, 20000, 0, 359) == 0, "no repeat before delay");
        check(nav.update(0, 20000, 0, 360) == NavRight, "held stick repeats after delay");
        check(nav.update(0, 20000, 0, 400) == 0, "repeat interval limited");
        check(nav.update(0, 20000, 0, 480) == NavRight, "held stick second repeat");
        check(nav.update(0, 0, 0, 500) == 0, "stick release");
        check(nav.update(0, -25000, 0, 510) == NavLeft, "left stick left press");
        nav.update(0, 0, 0, 520);
        check(nav.update(0, 0, 25000, 530) == NavUp, "positive Switch stick Y moves up");
        check(nav.update(0, 0, -25000, 540) == NavDown, "negative Switch stick Y moves down");
        nav.update(0, 0, 0, 550);
        check(nav.update(NavRight, 25000, 0, 560) == NavRight, "D-pad and stick do not double navigate");
        nav.update(0, 0, 0, 570);
        check(nav.update(0, 22000, 18000, 580) == NavRight, "diagonal stick selects dominant axis");
        NavigationRepeat wrap;
        wrap.update(NavDown, 0, 0, UINT32_MAX - 100);
        check(wrap.update(NavDown, 0, 0, 300) == NavDown, "repeat handles timer wraparound");
        auto j = Json::parse("{\"name\":\"\\u4e2d\\u6587\\ud83c\\udf1e\",\"a\":[null,true,-1.5e2]}");
        check(j["name"].text() == "中文🌞", "unicode and surrogate pair");
        check(j["a"].array()[2].integer() == -150, "number parser");
        check(Json::parse(j.dump())["name"].text() == "中文🌞", "JSON round trip");
        for (const auto& bad : {"[01]", "[1,]", "{\"a\":1,\"a\":2}", "\"\\ud800\"", "[1e999]", "[true]false"}) rejects([&] { Json::parse(bad); }, "malformed JSON rejected");
        rejects([] { Json::parse(std::string(34, '[') + std::string(34, ']')); }, "JSON depth bound");
        rejects([] { Json::parse("[]", 1); }, "JSON byte bound");
        check(!validUtf8(std::string("\xc0\x80", 2)), "overlong UTF8 rejected");
        check(!validUtf8(std::string("\xed\xa0\x80", 3)), "UTF8 surrogate rejected");
        check(validateUrl("https://EXAMPLE.com/a%2Fb?q=a%2Bz&sig=X%2F").query == "q=a%2Bz&sig=X%2F", "signed query preserved");
        check(validateUrl("https://example.com/a").origin() == "https://example.com:443", "normalized origin");
        for (const auto& bad : {"file:///etc/passwd", "https://u:p@example.com/", "https://@example.com/", "https://example.com/#x", "https://example.com/a b", "https://example.com/%gg", "https://example.com/\\evil"}) rejects([&] { validateUrl(bad); }, "invalid URL rejected");
        check(isScoped("https://example.com/emby/Items/1", "https://example.com/emby/"), "same origin path scoped");
        for (const auto& url : {"https://cdn.example.com/emby/a", "http://example.com/emby/a", "https://example.com/emby-other/a", "https://example.com/emby/%252e%252e/secret", "https://example.com/emby/a/../secret", "https://example.com:444/emby/a"}) check(!isScoped(url, "https://example.com/emby/"), "credentials excluded outside scope");
        check(serverBase("https://nas.test", true) == "https://nas.test/emby/", "ugreen base");
        rejects([] { serverBase("http://nas.test", true); }, "ugreen HTTPS required");
        check(parseStrm("\xef\xbb\xbf# hi\r\nhttps://cdn.test/a?sig=x%2Fy\r\n") == "https://cdn.test/a?sig=x%2Fy", "STRM BOM comment signed URL");
        rejects([] { parseStrm("https://a.test\nhttps://b.test"); }, "STRM multiple addresses");
        rejects([] { parseStrm(std::string(65537, 'a')); }, "STRM byte bound");
        rejects([] { parseStrm("https://a.test\nAuthorization: x"); }, "STRM header injection");
        Mock nested; nested.reply("https://a.test/a.strm"); rejects([&] { resolveStrm(nested, "https://a.test/a.strm", {}); }, "STRM recursion loop");
        std::vector<std::string> hlsUrls;
        auto hls = rewriteHls("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"../key?st=abc%2F\"\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:6,\nhttps://cdn.test/a.ts?sig=x%2Fy\n", "https://media.test/hls/index.m3u8", [&](const std::string& url) { hlsUrls.push_back(url); return "http://127.0.0.1/secret/" + std::to_string(hlsUrls.size()); });
        check(hlsUrls.size() == 3 && hlsUrls[0] == "https://media.test/key?st=abc%2F", "HLS keys and relative references");
        check(hls.find("cdn.test") == std::string::npos && hls.find("st=abc") == std::string::npos, "HLS conceals remote URLs");
        rejects([] { rewriteHls("#EXTM3U\nfile:///etc/passwd\n", "https://x.test/a", [](const auto& u) { return u; }); }, "HLS local file rejected");
        rejects([] { rewriteHls("#EXTM3U\n#EXT-X-KEY:URI=unquoted", "https://x.test/a", [](const auto& u) { return u; }); }, "malformed HLS URI rejected");
        check(subtitlePriority("zh-Hans", "") == 0 && subtitlePriority("zho", "") == 1 && subtitlePriority("zh_TW", "") == 2 && subtitlePriority("en", "") == 3, "0.2.8 subtitle order");
        Mock http; http.reply(R"({"User":{"Id":"u1"},"AccessToken":"secret"})");
        auto account = Emby::login(http, "https://nas.test/emby", "name", "pw", "device-1", false);
        check(account.userId == "u1", "Emby login"); check(Json::parse(http.requests[0].body)["Pw"].text() == "pw", "login body");
        Emby source(http, account); Media movie; movie.id = "m/1"; movie.title = "Movie"; movie.positionMs = 12345;
        http.reply(R"({"ErrorCode":null,"PlaySessionId":"ps1","MediaSources":[{"Id":"s1","Path":"https://cdn.test/movie.mp4?sig=x%2Fy","SupportsDirectPlay":true,"MediaStreams":[{"Type":"Subtitle","IsExternal":true,"DeliveryUrl":"/emby/sub.vtt","Language":"zh-CN","DisplayTitle":"简体"}]}]})");
        auto play = source.playback(movie);
        check(play.url == "https://cdn.test/movie.mp4?sig=x%2Fy" && !play.fallbackUrl.empty(), "direct URL and server fallback");
        check(play.startMs == 12345 && play.subtitles.size() == 1, "resume and external subtitles");
        check(http.requests.back().url.find("m%2F1") != std::string::npos, "ID escaped as one path component");
        http.reply(R"({"MediaSources":[{"Id":"s1","Path":"https://provider.test/movie","SupportsDirectPlay":true,"RequiredHttpHeaders":{"Cookie":"provider-secret"}}]})");
        auto protectedPlay = source.playback(movie); check(isScoped(protectedPlay.url, account.base), "provider headers remain server-side");
        http.reply(R"({"MediaSources":[{"SupportsDirectPlay":false,"SupportsDirectStream":false}]})"); rejects([&] { source.playback(movie); }, "server direct-play denial respected");
        http.reply("", 204); source.report(play, "Playing", 1500, false);
        check(Json::parse(http.requests.back().body)["PositionTicks"].integer() == 15000000, "progress ticks");
        rejects([&] { source.report(play, "Delete", 0, false); }, "arbitrary session operation rejected");
        check(CloudVideo::supports("https://index.test/api/play/asset-token"), "MediaIndex original entry recognized");
        check(!CloudVideo::supports("https://cdn.test/final.mp4"), "CDN URL not treated as cloud session entry");
        std::cout << checks << " core checks passed\n";
    } catch (const std::exception& e) { std::cerr << "FAIL after " << checks << ": " << e.what() << '\n'; return 1; }
    curl_global_cleanup();
}
