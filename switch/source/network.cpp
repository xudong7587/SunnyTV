#include "sunny/network.hpp"
#include "sunny/json.hpp"
#include <curl/curl.h>
#include <algorithm>
#include <cctype>
#include <chrono>
#include <memory>
#include <set>
#include <sstream>
#include <stdexcept>

namespace sunny {
std::string lower(std::string s) { for (auto& c : s) c = char(std::tolower(static_cast<unsigned char>(c))); return s; }
std::string trim(std::string s) { auto white = [](unsigned char c) { return c == ' ' || c == '\t' || c == '\r' || c == '\n'; }; while (!s.empty() && white(s.back())) s.pop_back(); size_t p = 0; while (p < s.size() && white(s[p])) ++p; return s.substr(p); }
std::string encode(const std::string& s) { const char* hex = "0123456789ABCDEF"; std::string out; for (unsigned char c : s) { if ((std::isalnum(c) && c < 128) || c == '-' || c == '_' || c == '.' || c == '~') out += char(c); else { out += '%'; out += hex[c >> 4]; out += hex[c & 15]; } } return out; }
namespace {
struct CurlUrl { CURLU* p = curl_url(); ~CurlUrl() { curl_url_cleanup(p); } };
std::string part(CURLU* p, CURLUPart what, unsigned flags = 0) { char* out = nullptr; if (curl_url_get(p, what, &out, flags)) return ""; std::string s = out; curl_free(out); return s; }
bool secret(const std::string& key) { static const std::set<std::string> names = {"authorization", "proxy-authorization", "cookie", "cookie2", "x-emby-token", "x-emby-authorization", "x-mediabrowser-token"}; return names.count(lower(key)); }
bool redirect(long status) { return status == 301 || status == 302 || status == 303 || status == 307 || status == 308; }
bool badHeader(const std::string& s) { return s.find_first_of("\r\n") != std::string::npos || s.find('\0') != std::string::npos; }
struct Transfer {
    Response response;
    Sink sink;
    const std::atomic_bool* cancel;
    size_t bytes = 0, maxBytes, headerBytes = 0;
    bool failed = false;
    static size_t header(char* data, size_t size, size_t count, void* opaque) {
        auto& t = *static_cast<Transfer*>(opaque); size_t n = size * count;
        t.headerBytes += n;
        if (t.headerBytes > 65536) { t.failed = true; return 0; }
        std::string line(data, n);
        if (line.rfind("HTTP/", 0) == 0) { t.response.headers.clear(); std::istringstream in(line); std::string protocol; in >> protocol >> t.response.status; }
        else if (line == "\r\n") { if (t.response.status >= 200 && !redirect(t.response.status) && t.sink.start && !t.sink.start(t.response)) { t.failed = true; return 0; } }
        else { auto colon = line.find(':'); if (colon != std::string::npos) t.response.headers[lower(trim(line.substr(0, colon)))] = trim(line.substr(colon + 1)); }
        return n;
    }
    static size_t write(char* data, size_t size, size_t count, void* opaque) {
        auto& t = *static_cast<Transfer*>(opaque); size_t n = size * count;
        // Stop reading redirect bodies immediately. A video redirect must not keep an unlimited
        // body open before the next hop can apply credential scoping.
        if (redirect(t.response.status)) return 0;
        if (n > t.maxBytes - std::min(t.bytes, t.maxBytes) || (t.cancel && t.cancel->load())) { t.failed = true; return 0; }
        t.bytes += n;
        if (t.sink.data) { if (!t.sink.data(data, n)) { t.failed = true; return 0; } } else t.response.body.append(data, n);
        return n;
    }
    static int progress(void* opaque, curl_off_t, curl_off_t, curl_off_t, curl_off_t) { const auto& t = *static_cast<Transfer*>(opaque); return t.cancel && t.cancel->load(); }
};
}
std::string Url::origin() const { return scheme + "://" + lower(host) + ":" + port; }
Url validateUrl(const std::string& raw) {
    if (raw.empty() || raw.find('#') != std::string::npos || raw.find('\\') != std::string::npos) throw std::runtime_error("仅支持不带账号和片段的 HTTP(S) 地址");
    for (size_t i = 0; i < raw.size(); ++i) { unsigned char c = raw[i]; if (c <= 32 || c == 127) throw std::runtime_error("地址含空白或控制字符"); if (c == '%' && (i + 2 >= raw.size() || !std::isxdigit(static_cast<unsigned char>(raw[i + 1])) || !std::isxdigit(static_cast<unsigned char>(raw[i + 2])))) throw std::runtime_error("地址编码无效"); }
    CurlUrl u; if (!u.p || curl_url_set(u.p, CURLUPART_URL, raw.c_str(), CURLU_PATH_AS_IS)) throw std::runtime_error("地址格式无效");
    Url out{raw, lower(part(u.p, CURLUPART_SCHEME)), lower(part(u.p, CURLUPART_HOST)), part(u.p, CURLUPART_PORT, CURLU_DEFAULT_PORT), part(u.p, CURLUPART_PATH), part(u.p, CURLUPART_QUERY)};
    if ((out.scheme != "http" && out.scheme != "https") || out.host.empty() || !part(u.p, CURLUPART_USER).empty() || !part(u.p, CURLUPART_PASSWORD).empty() || raw.substr(raw.find("://") + 3, raw.find_first_of("/?", raw.find("://") + 3) - (raw.find("://") + 3)).find('@') != std::string::npos) throw std::runtime_error("请在账号栏填写凭据");
    return out;
}
bool ambiguousPath(std::string path) {
    for (int round = 0; round < 5; ++round) {
        std::string next; for (size_t i = 0; i < path.size(); ++i) { if (path[i] == '%' && i + 2 < path.size()) { auto code = lower(path.substr(i, 3)); if (code == "%25" || code == "%2e" || code == "%2f" || code == "%5c") { next += code == "%25" ? '%' : code == "%2e" ? '.' : code == "%2f" ? '/' : '\\'; i += 2; continue; } } next += path[i]; } path = next;
    }
    if (lower(path).find("%25") != std::string::npos || path.find('\\') != std::string::npos) return true;
    std::istringstream in(path); std::string segment; while (std::getline(in, segment, '/')) if (segment == "." || segment == "..") return true;
    return false;
}
std::string serverBase(const std::string& raw, bool ugreen) {
    auto s = trim(raw); auto u = validateUrl(s); if (s.find('?') != std::string::npos || ambiguousPath(u.path)) throw std::runtime_error("服务器根路径无效");
    if (ugreen && (u.scheme != "https" || (u.path != "/" && u.path != "/emby/" && u.path != "/emby"))) throw std::runtime_error("绿联请填写有有效证书的 HTTPS 媒体服务器地址");
    while (!s.empty() && s.back() == '/') s.pop_back();
    if (ugreen && u.path == "/") s += "/emby";
    return s + '/';
}
std::string resolveUrl(const std::string& base, const std::string& reference) {
    validateUrl(base); if (reference.empty()) throw std::runtime_error("服务返回空地址");
    for (unsigned char c : reference) if (c <= 32 || c == 127 || c == '\\') throw std::runtime_error("服务返回无效地址");
    CurlUrl u; if (curl_url_set(u.p, CURLUPART_URL, base.c_str(), CURLU_PATH_AS_IS) || curl_url_set(u.p, CURLUPART_URL, reference.c_str(), CURLU_PATH_AS_IS)) throw std::runtime_error("无法解析服务返回地址");
    auto out = part(u.p, CURLUPART_URL); validateUrl(out); return out;
}
bool isScoped(const std::string& target, const std::string& base) { try { auto t = validateUrl(target), b = validateUrl(base); std::string prefix = b.path; if (prefix.empty() || prefix.back() != '/') prefix += '/'; return !ambiguousPath(t.path) && !ambiguousPath(b.path) && t.origin() == b.origin() && (t.path == b.path || t.path.rfind(prefix, 0) == 0); } catch (...) { return false; } }
Response Http::fetch(const Request& r, const std::atomic_bool* cancel) { return stream(r, {}, cancel); }
Response Http::stream(const Request& original, const Sink& sink, const std::atomic_bool* cancel) {
    Request req = original; std::set<std::string> visited; auto start = std::chrono::steady_clock::now();
    for (int hop = 0; ; ++hop) {
        auto u = validateUrl(req.url); if (!visited.insert(req.url).second || hop > 8) throw std::runtime_error("重定向循环或次数超限");
        long remaining = 30000 - long(std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - start).count());
        if (!req.playback && remaining <= 0) throw std::runtime_error("服务请求超时");
        if (cancel && cancel->load()) throw std::runtime_error("操作已取消");
        std::unique_ptr<CURL, decltype(&curl_easy_cleanup)> curl(curl_easy_init(), curl_easy_cleanup); if (!curl) throw std::runtime_error("网络初始化失败");
        Transfer transfer{{}, sink, cancel, 0, req.maxBytes}; transfer.response.finalUrl = req.url;
        Headers headers; for (const auto& [k,v] : req.headers) if (!secret(k)) headers[k] = v;
        if (!req.scope.base.empty() && isScoped(req.url, req.scope.base)) for (const auto& [k,v] : req.scope.headers) headers[k] = v;
        curl_slist* list = nullptr;
        for (const auto& [k,v] : headers) { if (badHeader(k) || badHeader(v) || k.find(':') != std::string::npos) { curl_slist_free_all(list); throw std::runtime_error("请求头无效"); } list = curl_slist_append(list, (k + ": " + v).c_str()); }
        std::unique_ptr<curl_slist, decltype(&curl_slist_free_all)> listGuard(list, curl_slist_free_all);
        curl_easy_setopt(curl.get(), CURLOPT_URL, req.url.c_str()); curl_easy_setopt(curl.get(), CURLOPT_PATH_AS_IS, 1L);
        curl_easy_setopt(curl.get(), CURLOPT_HTTPHEADER, list); curl_easy_setopt(curl.get(), CURLOPT_USERAGENT, UserAgent);
        curl_easy_setopt(curl.get(), CURLOPT_FOLLOWLOCATION, 0L); curl_easy_setopt(curl.get(), CURLOPT_NOSIGNAL, 1L);
        curl_easy_setopt(curl.get(), CURLOPT_PROTOCOLS, long(CURLPROTO_HTTP | CURLPROTO_HTTPS));
        curl_easy_setopt(curl.get(), CURLOPT_SSL_VERIFYPEER, 1L); curl_easy_setopt(curl.get(), CURLOPT_SSL_VERIFYHOST, 2L);
        curl_easy_setopt(curl.get(), CURLOPT_CONNECTTIMEOUT, req.playback ? 15L : 8L);
        curl_easy_setopt(curl.get(), CURLOPT_LOW_SPEED_LIMIT, 1L); curl_easy_setopt(curl.get(), CURLOPT_LOW_SPEED_TIME, req.playback ? 60L : 25L);
        if (!req.playback) curl_easy_setopt(curl.get(), CURLOPT_TIMEOUT_MS, remaining);
        curl_easy_setopt(curl.get(), CURLOPT_HEADERFUNCTION, Transfer::header); curl_easy_setopt(curl.get(), CURLOPT_HEADERDATA, &transfer);
        curl_easy_setopt(curl.get(), CURLOPT_WRITEFUNCTION, Transfer::write); curl_easy_setopt(curl.get(), CURLOPT_WRITEDATA, &transfer);
        curl_easy_setopt(curl.get(), CURLOPT_XFERINFOFUNCTION, Transfer::progress); curl_easy_setopt(curl.get(), CURLOPT_XFERINFODATA, &transfer); curl_easy_setopt(curl.get(), CURLOPT_NOPROGRESS, 0L);
        if (req.method == "HEAD") curl_easy_setopt(curl.get(), CURLOPT_NOBODY, 1L);
        else if (req.method != "GET") { curl_easy_setopt(curl.get(), CURLOPT_CUSTOMREQUEST, req.method.c_str()); curl_easy_setopt(curl.get(), CURLOPT_POSTFIELDS, req.body.data()); curl_easy_setopt(curl.get(), CURLOPT_POSTFIELDSIZE_LARGE, curl_off_t(req.body.size())); }
        CURLcode result = curl_easy_perform(curl.get());
        if (result != CURLE_OK && !redirect(transfer.response.status)) { if (cancel && cancel->load()) throw std::runtime_error("操作已取消"); if (transfer.failed) throw std::runtime_error("响应超限或传输已停止"); throw std::runtime_error("连接 " + u.host + " 失败（网络/TLS，" + std::to_string(int(result)) + "）"); }
        auto& response = transfer.response;
        if (redirect(response.status)) { auto it = response.headers.find("location"); if (it == response.headers.end()) throw std::runtime_error("重定向缺少地址"); if (req.method != "GET") throw std::runtime_error("已阻止认证或写入请求重定向"); std::string next = resolveUrl(req.url, it->second); if (u.scheme == "https" && validateUrl(next).scheme != "https") throw std::runtime_error("已阻止 HTTPS 降级跳转"); req.url = next; continue; }
        return response;
    }
}
std::string parseStrm(const std::string& input) {
    if (input.size() > 65536 || !validUtf8(input)) throw std::runtime_error("STRM 超过 64 KiB 或不是有效 UTF-8");
    std::string text = input; if (text.rfind("\xef\xbb\xbf", 0) == 0) text.erase(0, 3);
    std::istringstream in(text); std::string line, url;
    while (std::getline(in, line)) { line = trim(line); if (line.empty() || line.front() == '#') continue; if (!url.empty()) throw std::runtime_error("STRM 必须只有一个媒体地址"); validateUrl(line); url = line; }
    if (url.empty()) throw std::runtime_error("STRM 没有媒体地址");
    return url;
}
bool looksLikeStrm(const std::string& url) { auto path = lower(validateUrl(url).path); return path.size() >= 5 && path.substr(path.size() - 5) == ".strm"; }
std::string resolveStrm(Transport& http, std::string url, const Scope& scope, const std::atomic_bool* cancel) { std::set<std::string> seen; while (looksLikeStrm(url)) { if (seen.size() >= 3 || !seen.insert(url).second) throw std::runtime_error("STRM 嵌套过深或存在循环"); Request req; req.url = url; req.scope = scope; req.maxBytes = 65536; auto r = http.fetch(req, cancel); if (r.status != 200) throw std::runtime_error("STRM 读取失败（HTTP " + std::to_string(r.status) + "）"); url = parseStrm(r.body); } return url; }
std::string rewriteHls(const std::string& body, const std::string& base, const std::function<std::string(const std::string&)>& add) {
    if (body.size() > 2 * 1024 * 1024 || !validUtf8(body) || body.rfind("#EXTM3U", 0) != 0) throw std::runtime_error("HLS 清单无效或过大");
    std::istringstream in(body); std::string line, out;
    while (std::getline(in, line)) {
        line = trim(line);
        if (!line.empty() && line.front() != '#') line = add(resolveUrl(base, line));
        else { size_t pos = 0; while ((pos = line.find("URI=", pos)) != std::string::npos) { if (pos && line[pos - 1] != ':' && line[pos - 1] != ',') { pos += 4; continue; } size_t begin = pos + 4; if (begin == line.size() || line[begin] != '"') throw std::runtime_error("HLS 地址属性无效"); size_t end = line.find('"', begin + 1); if (end == std::string::npos) throw std::runtime_error("HLS 地址属性无效"); auto url = add(resolveUrl(base, line.substr(begin + 1, end - begin - 1))); line.replace(begin + 1, end - begin - 1, url); pos = begin + url.size() + 2; } }
        out += line + '\n';
    } return out;
}
}
