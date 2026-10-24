#include "sunny/media.hpp"
#include <algorithm>
#include <climits>
#include <set>
#include <stdexcept>

namespace sunny {
namespace {
Headers auth(const std::string& token, const std::string& device) {
    if (device.empty() || device.find_first_not_of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_") != std::string::npos) throw std::runtime_error("设备标识无效");
    Headers h{{"X-Emby-Authorization", "MediaBrowser Client=\"SunnyTV\", Device=\"Nintendo Switch\", DeviceId=\"" + device + "\", Version=\"0.2.8\""}};
    if (!token.empty()) h["X-Emby-Token"] = token;
    return h;
}
void success(const Response& r) { if (r.status < 200 || r.status >= 300) throw std::runtime_error("媒体服务请求失败（HTTP " + std::to_string(r.status) + "）"); }
CloudVariant variant(const Json& j) {
    CloudVariant v{j["id"].text(), int(j["width"].integer()), int(j["height"].integer()), j["bandwidth"].integer()};
    if (v.id.size() != 32 || v.id.find_first_not_of("0123456789abcdef") != std::string::npos || v.width < 1 || v.width > 16384 || v.height < 1 || v.height > 16384 || v.bandwidth < 1 || v.bandwidth > 1000000000) throw std::runtime_error("云端清晰度数据无效");
    return v;
}
}
int subtitlePriority(const std::string& language, const std::string& title) {
    auto code = lower(language), name = lower(title); std::replace(code.begin(), code.end(), '_', '-');
    if (code == "zh-hans" || code == "zh-cn" || code == "zh-sg" || code == "chs" || name.find("简体") != std::string::npos || name.find("簡體") != std::string::npos || name.find("simplified") != std::string::npos) return 0;
    if (code == "zh-hant" || code == "zh-tw" || code == "zh-hk" || code == "zh-mo" || code == "cht" || name.find("繁体") != std::string::npos || name.find("繁體") != std::string::npos || name.find("traditional") != std::string::npos) return 2;
    if (code == "zh" || code == "zho" || code == "chi" || code == "cmn" || code == "yue" || code.rfind("zh-", 0) == 0 || name.find("中文") != std::string::npos || name.find("chinese") != std::string::npos) return 1;
    return 3;
}
Emby::Emby(Transport& http, Account account, std::atomic_bool* cancel) : http_(http), account_(std::move(account)), cancel_(cancel) { account_.base = serverBase(account_.base, account_.ugreen); }
Scope Emby::scope() const { return {account_.base, auth(account_.token, account_.deviceId)}; }
std::string Emby::endpoint(const std::string& path, const Headers& query) const {
    // Paths are built exclusively by this adapter, identifiers must be escaped at insertion sites.
    std::string url = account_.base + path; bool first = true;
    for (const auto& [k,v] : query) { url += first ? '?' : '&'; first = false; url += encode(k) + '=' + encode(v); }
    validateUrl(url); return url;
}
Json Emby::api(const std::string& path, const Headers& query, const Json* body) {
    Request req; req.url = endpoint(path, query); req.scope = scope();
    if (body) { req.method = "POST"; req.body = body->dump(); req.headers["Content-Type"] = "application/json"; }
    auto r = http_.fetch(req, cancel_); success(r); if (r.body.empty()) return Json::Object{}; return Json::parse(r.body);
}
Account Emby::login(Transport& http, const std::string& base, const std::string& username, const std::string& password, const std::string& device, bool ugreen, std::atomic_bool* cancel) {
    Account a{serverBase(base, ugreen), "", "", device, ugreen}; Emby source(http, a, cancel);
    Json body = Json::Object{{"Username", username}, {"Pw", password}};
    auto j = source.api("Users/AuthenticateByName", {}, &body);
    a.userId = j["User"]["Id"].text(); a.token = j["AccessToken"].text();
    if (a.userId.empty() || a.token.empty()) throw std::runtime_error("登录响应缺少用户或令牌");
    return a;
}
Media Emby::parseItem(const Json& j) const {
    Media m; m.id = j["Id"].text(); m.title = j["Name"].text("未命名"); m.type = j["Type"].text(); m.overview = j["Overview"].text(); m.seriesId = j["SeriesId"].text(); m.year = int(j["ProductionYear"].integer());
    if (m.type == "Episode") { auto series = j["SeriesName"].text(); if (!series.empty()) m.title = series + " · S" + std::to_string(j["ParentIndexNumber"].integer()) + "E" + std::to_string(j["IndexNumber"].integer()) + " · " + m.title; }
    m.positionMs = std::max<int64_t>(0, j["UserData"]["PlaybackPositionTicks"].integer() / 10000);
    m.durationMs = std::max<int64_t>(0, j["RunTimeTicks"].integer() / 10000);
    m.favorite = j["UserData"]["IsFavorite"].boolean();
    m.folder = j["IsFolder"].boolean() || m.type == "Series" || m.type == "Season" || m.type == "CollectionFolder" || m.type == "BoxSet";
    m.imageTag = j["ImageTags"]["Primary"].text(); m.parentImageId = j["SeriesId"].text();
    const auto& backdrops = j["BackdropImageTags"].array();
    const auto& parentBackdrops = j["ParentBackdropImageTags"].array();
    if (!backdrops.empty()) { m.backdropTag = backdrops.front().text(); m.backdropId = m.id; }
    else if (!parentBackdrops.empty()) { m.backdropTag = parentBackdrops.front().text(); m.backdropId = j["ParentBackdropItemId"].text(m.seriesId); }
    m.logoTag = j["ImageTags"]["Logo"].text(); m.rating = j["CommunityRating"].number();
    for (const auto& genre : j["Genres"].array()) { if (!m.subtitle.empty()) m.subtitle += " · "; m.subtitle += genre.text(); if (m.subtitle.size() > 96) break; }
    return m;
}
Page Emby::parsePage(const Json& j) const {
    Page page; const auto& array = j.has("Items") ? j["Items"].array() : j.array();
    if (array.size() > 10000) throw std::runtime_error("媒体列表过大");
    for (const auto& row : array) { auto m = parseItem(row); if (!m.id.empty()) page.items.push_back(std::move(m)); }
    page.total = j["TotalRecordCount"].integer(int64_t(page.items.size()));
    return page;
}
Page Emby::views() { return parsePage(api("Users/" + encode(account_.userId) + "/Views")); }
Page Emby::resume() { return parsePage(api("Users/" + encode(account_.userId) + "/Items/Resume", {{"Limit", "30"}, {"Fields", "Overview,MediaSources"}, {"ImageTypeLimit", "1"}})); }
Page Emby::latest() { return parsePage(api("Users/" + encode(account_.userId) + "/Items/Latest", {{"Limit", "30"}, {"Fields", "Overview"}, {"ImageTypeLimit", "1"}})); }
Page Emby::list(const std::string& parent, int start, const std::string& search) {
    Headers q{{"StartIndex", std::to_string(std::max(0, start))}, {"Limit", "60"}, {"Fields", "Overview,MediaSources,MediaStreams"}, {"SortBy", "SortName"}, {"SortOrder", "Ascending"}, {"ImageTypeLimit", "1"}};
    if (!parent.empty()) q["ParentId"] = parent;
    if (!search.empty()) { q["SearchTerm"] = search; q["Recursive"] = "true"; q["IncludeItemTypes"] = "Movie,Series,Episode"; }
    return parsePage(api("Users/" + encode(account_.userId) + "/Items", q));
}
Page Emby::children(const Media& item, int start) {
    if (item.type == "Series") return parsePage(api("Shows/" + encode(item.id) + "/Seasons", {{"UserId", account_.userId}, {"Fields", "Overview"}}));
    if (item.type == "Season") return parsePage(api("Shows/" + encode(item.seriesId) + "/Episodes", {{"UserId", account_.userId}, {"SeasonId", item.id}, {"Fields", "Overview,MediaSources,MediaStreams"}, {"StartIndex", std::to_string(start)}, {"Limit", "60"}}));
    return list(item.id, start);
}
Media Emby::item(const std::string& id) { return parseItem(api("Users/" + encode(account_.userId) + "/Items/" + encode(id), {{"Fields", "Overview,MediaSources,MediaStreams"}})); }
std::string Emby::imageUrl(const Media& m, int width, const std::string& type) const {
    if (type != "Primary" && type != "Backdrop" && type != "Logo") throw std::runtime_error("图片类型无效");
    auto id = type == "Backdrop" && !m.backdropId.empty() ? m.backdropId : type == "Primary" && m.imageTag.empty() && !m.parentImageId.empty() ? m.parentImageId : m.id;
    auto tag = type == "Backdrop" ? m.backdropTag : type == "Logo" ? m.logoTag : m.imageTag;
    Headers q{{"MaxWidth", std::to_string(std::clamp(width, 64, 1920))}, {"Quality", "85"}};
    if (!tag.empty()) q["tag"] = tag;
    return endpoint("Items/" + encode(id) + "/Images/" + type + (type == "Backdrop" ? "/0" : ""), q);
}
Playback Emby::playback(const Media& item, bool fromStart) {
    Json body = Json::Object{{"UserId", account_.userId}, {"StartTimeTicks", fromStart ? int64_t(0) : item.positionMs * 10000}, {"IsPlayback", true}, {"AutoOpenLiveStream", false}, {"EnableDirectPlay", true}, {"EnableDirectStream", true}, {"EnableTranscoding", false}};
    auto info = api("Items/" + encode(item.id) + "/PlaybackInfo", {}, &body);
    if (!info["ErrorCode"].null() && !info["ErrorCode"].text().empty()) throw std::runtime_error("服务端拒绝直接播放");
    const Json* source = nullptr;
    for (const auto& s : info["MediaSources"].array()) if (s["SupportsDirectPlay"].boolean() || s["SupportsDirectStream"].boolean()) { source = &s; break; }
    if (!source) for (const auto& s : info["MediaSources"].array()) if (!s.has("SupportsDirectPlay") && !s.has("SupportsDirectStream")) { source = &s; break; }
    if (!source || (*source)["RequiresOpening"].boolean()) throw std::runtime_error("没有可直接播放的媒体源，或需要专用打开会话");
    const auto& s = *source; Playback p; p.itemId = item.id; p.sourceId = s["Id"].text(); p.sessionId = info["PlaySessionId"].text(); p.title = item.title; p.scope = scope(); p.startMs = fromStart ? 0 : item.positionMs; p.durationMs = item.durationMs;
    auto direct = s["Path"].text(), supplied = s["DirectStreamUrl"].text(); std::string server;
    if (!supplied.empty()) { server = resolveUrl(account_.base, supplied); if (account_.ugreen && !isScoped(server, account_.base)) server.clear(); }
    if (server.empty() && lower(s["Protocol"].text()) == "http" && direct.rfind("//", 0) != 0 && direct.find(':') == std::string::npos && direct.find('\\') == std::string::npos && !direct.empty()) { auto resolved = resolveUrl(account_.base, direct); if (isScoped(resolved, account_.base)) server = resolved; }
    if (server.empty()) server = account_.ugreen ? endpoint("videos/" + encode(item.id) + "/stream.mp4") : endpoint("Videos/" + encode(item.id) + "/stream", {{"Static", "true"}, {"MediaSourceId", p.sourceId}, {"PlaySessionId", p.sessionId}});
    bool remote = lower(direct).rfind("https://", 0) == 0 || lower(direct).rfind("http://", 0) == 0;
    bool directPlay = remote && s["SupportsDirectPlay"].boolean() && s["RequiredHttpHeaders"].fields().empty();
    p.url = resolveStrm(http_, directPlay ? direct : server, p.scope, cancel_); p.sourceMediaUrl = remote ? direct : ""; p.method = directPlay ? "DirectPlay" : "DirectStream";
    if (directPlay && server != p.url) p.fallbackUrl = server;
    for (const auto& track : s["MediaStreams"].array()) {
        if (track["Type"].text() != "Subtitle" || !track["IsExternal"].boolean()) continue;
        auto url = track["DeliveryUrl"].text(); if (url.empty()) continue;
        p.subtitles.push_back({resolveUrl(account_.base, url), track["Language"].text(), track["DisplayTitle"].text(track["Language"].text("字幕")), track["IsDefault"].boolean()});
    }
    std::stable_sort(p.subtitles.begin(), p.subtitles.end(), [](const auto& a, const auto& b) { return subtitlePriority(a.language, a.title) < subtitlePriority(b.language, b.title); });
    return p;
}
void Emby::report(const Playback& p, const std::string& event, int64_t position, bool paused) {
    if (event != "Playing" && event != "Playing/Progress" && event != "Playing/Stopped") throw std::runtime_error("播放事件无效");
    Json body = Json::Object{{"ItemId", p.itemId}, {"MediaSourceId", p.sourceId}, {"PlaySessionId", p.sessionId}, {"PositionTicks", std::clamp<int64_t>(position, 0, 900719925474LL) * 10000}, {"IsPaused", paused}, {"CanSeek", true}, {"PlayMethod", p.method}, {"EventName", paused ? "Pause" : "TimeUpdate"}};
    api("Sessions/" + event, {}, &body);
}
bool CloudVideo::supports(const std::string& url) { try { auto u = validateUrl(url); auto marker = u.path.find("/api/play/"); return marker != std::string::npos && marker + 10 < u.path.size() && u.path.find('/', marker + 10) == std::string::npos; } catch (...) { return false; } }
CloudVideo::CloudVideo(Transport& http, std::string url, std::atomic_bool* cancel) : http_(http), cancel_(cancel) {
    if (!supports(url)) throw std::runtime_error("此媒体没有可识别的 MediaIndex 原始入口");
    auto u = validateUrl(url); origin_ = u.scheme + "://" + u.host + (u.port == (u.scheme == "https" ? "443" : "80") ? "" : ":" + u.port); assetToken_ = u.path.substr(u.path.find("/api/play/") + 10);
}
Json CloudVideo::post(const std::string& path, const Json& body) {
    Request req; req.url = origin_ + "/api/cloud-video/" + path; req.method = "POST"; req.body = body.dump(); req.maxBytes = 65536; req.headers["Content-Type"] = "application/json";
    auto r = http_.fetch(req, cancel_); success(r); auto j = Json::parse(r.body, 65536); if (j["protocol"].text() != "mediaindex-cloud-video-v1") throw std::runtime_error("云端协议不匹配"); return j;
}
std::vector<CloudVariant> CloudVideo::discover() {
    auto j = post("discover", Json::Object{{"assetToken", assetToken_}}); std::vector<CloudVariant> result; std::set<std::string> seen;
    for (const auto& row : j["variants"].array()) { auto v = variant(row); if (!seen.insert(v.id).second) throw std::runtime_error("云端档位重复"); result.push_back(v); }
    if (result.empty() || result.size() > 32) throw std::runtime_error("云端没有可用清晰度");
    return result;
}
CloudSession CloudVideo::create(const CloudVariant& v) {
    auto j = post("sessions", Json::Object{{"assetToken", assetToken_}, {"variantId", v.id}, {"delivery", "direct"}});
    CloudSession s{j["sessionId"].text(), j["sessionToken"].text(), resolveUrl(origin_ + '/', j["playlistUrl"].text()), variant(j["variant"])};
    if (s.id.size() != 32 || s.id.find_first_not_of("0123456789abcdef") != std::string::npos || s.token.size() < 32 || s.token.size() > 128 || s.token.find_first_not_of("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-") != std::string::npos) throw std::runtime_error("云端会话标识无效");
    auto u = validateUrl(s.url); if (u.origin() != validateUrl(origin_).origin() || u.path != "/api/cloud-video/sessions/" + s.id + "/index.m3u8" || u.query != "st=" + s.token || j["seekMode"].text() != "hls-vod" || j["delivery"].text() != "direct" || s.variant.id != v.id) throw std::runtime_error("云端播放地址或会话不匹配");
    return s;
}
void CloudVideo::stop(const CloudSession& s) { Request req; req.url = origin_ + "/api/cloud-video/sessions/" + encode(s.id) + "?st=" + encode(s.token); req.method = "DELETE"; auto r = http_.fetch(req, cancel_); success(r); }
std::string CloudVariant::label() const { int size = std::max(width, height); return (size >= 3840 ? "4K" : size >= 2560 ? "2K" : size >= 1920 ? "1080P" : size >= 1280 ? "720P" : std::to_string(width) + "×" + std::to_string(height)) + " · " + std::to_string(bandwidth / 1000000) + " Mbps"; }
}
