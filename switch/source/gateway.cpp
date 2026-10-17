#include "sunny/gateway.hpp"
#include <arpa/inet.h>
#include <sys/socket.h>
#include <unistd.h>
#include <algorithm>
#include <cerrno>
#include <limits>
#include <sstream>
#include <stdexcept>

#ifndef MSG_NOSIGNAL
#define MSG_NOSIGNAL 0
#endif
namespace sunny {
namespace {
bool sendAll(int socket, const char* data, size_t n) { while (n) { auto sent = send(socket, data, n, MSG_NOSIGNAL); if (sent <= 0) return false; data += sent; n -= size_t(sent); } return true; }
bool sendAll(int socket, const std::string& s) { return sendAll(socket, s.data(), s.size()); }
void simple(int client, int code, const std::string& reason, const std::string& body = "") { sendAll(client, "HTTP/1.1 " + std::to_string(code) + " " + reason + "\r\nContent-Length: " + std::to_string(body.size()) + "\r\nConnection: close\r\n\r\n" + body); }
bool rangeValid(const std::string& range) { if (range.empty()) return true; if (range.rfind("bytes=", 0) != 0 || range.size() > 64 || range.size() < 7) return false; auto s = range.substr(6); auto dash = s.find('-'); return dash != std::string::npos && s.find('-', dash + 1) == std::string::npos && s != "-" && s.find_first_not_of("0123456789-") == std::string::npos; }
}
Gateway::Gateway(std::string randomSecret) : secret_(std::move(randomSecret)) {
    if (secret_.size() < 32 || secret_.find_first_not_of("0123456789abcdef") != std::string::npos) throw std::runtime_error("播放网关随机标识无效");
    listener_ = socket(AF_INET, SOCK_STREAM, 0); if (listener_ < 0) throw std::runtime_error("无法创建播放网关");
    sockaddr_in address{}; address.sin_family = AF_INET; address.sin_addr.s_addr = htonl(INADDR_LOOPBACK); address.sin_port = 0;
    if (bind(listener_, reinterpret_cast<sockaddr*>(&address), sizeof address) < 0 || listen(listener_, 8) < 0) { close(listener_); listener_ = -1; throw std::runtime_error("无法启动播放网关"); }
    socklen_t length = sizeof address; if (getsockname(listener_, reinterpret_cast<sockaddr*>(&address), &length) != 0) { close(listener_); listener_ = -1; throw std::runtime_error("无法读取播放网关端口"); } port_ = ntohs(address.sin_port);
    try { for (int i = 0; i < 4; ++i) workers_.emplace_back([this] { serve(); }); }
    catch (...) { stopping_ = true; shutdown(listener_, SHUT_RDWR); close(listener_); for (auto& t : workers_) t.join(); throw; }
}
Gateway::~Gateway() { stopping_ = true; if (listener_ >= 0) { shutdown(listener_, SHUT_RDWR); close(listener_); } for (auto& t : workers_) if (t.joinable()) t.join(); }
std::string Gateway::add(const std::string& url, const Scope& scope) {
    validateUrl(url); std::lock_guard<std::mutex> lock(mutex_);
    // Do not coalesce resources from different credential scopes.
    std::string key = scope.base + '\n' + url;
    auto found = ids_.find(key); std::string id;
    if (found != ids_.end()) id = found->second;
    else { if (resources_.size() >= 16384) throw std::runtime_error("播放分片数量超限"); id = std::to_string(resources_.size() + 1); resources_[id] = {url, scope}; ids_[key] = id; }
    return "http://127.0.0.1:" + std::to_string(port_) + '/' + secret_ + '/' + id;
}
void Gateway::serve() {
    while (!stopping_) {
        int client = accept(listener_, nullptr, nullptr); if (client < 0) { if (stopping_) return; continue; }
        timeval timeout{5, 0}; setsockopt(client, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof timeout); setsockopt(client, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof timeout);
        try { handle(client); } catch (...) { simple(client, 502, "Upstream Error"); }
        shutdown(client, SHUT_RDWR); close(client);
    }
}
void Gateway::handle(int client) {
    std::string input; char buffer[2048];
    while (input.find("\r\n\r\n") == std::string::npos) { auto n = recv(client, buffer, sizeof buffer, 0); if (n <= 0 || input.size() + size_t(n) > 8192 || stopping_) return; input.append(buffer, size_t(n)); }
    std::istringstream lines(input); std::string method, target, protocol; lines >> method >> target >> protocol;
    if (method != "GET" && method != "HEAD") { simple(client, 405, "Method Not Allowed"); return; }
    std::string prefix = '/' + secret_ + '/'; if (target.rfind(prefix, 0) != 0) { simple(client, 404, "Not Found"); return; }
    Resource resource; { std::lock_guard<std::mutex> lock(mutex_); auto it = resources_.find(target.substr(prefix.size())); if (it == resources_.end()) { simple(client, 404, "Not Found"); return; } resource = it->second; }
    Headers clientHeaders; std::string line; std::getline(lines, line);
    while (std::getline(lines, line) && line != "\r") { auto colon = line.find(':'); if (colon != std::string::npos) clientHeaders[lower(trim(line.substr(0, colon)))] = trim(line.substr(colon + 1)); }
    auto range = clientHeaders["range"]; if (!rangeValid(range)) { simple(client, 416, "Range Not Satisfiable"); return; }
    Request req; req.url = resource.url; req.scope = resource.scope; req.playback = true; req.maxBytes = std::numeric_limits<size_t>::max();
    bool hlsHint = lower(validateUrl(req.url).path).find(".m3u8") != std::string::npos;
    if (!range.empty() && !hlsHint) req.headers["Range"] = range;
    Response upstream; std::string pending; bool decided = false, hls = false, sent = false, rejected = false;
    auto startBody = [&]() {
        if (sent) return true;
        std::string header = "HTTP/1.1 " + std::to_string(upstream.status) + (upstream.status == 206 ? " Partial Content\r\n" : " OK\r\n");
        for (const auto& key : {"content-type", "content-length", "content-range", "accept-ranges"}) { auto it = upstream.headers.find(key); if (it != upstream.headers.end() && it->second.find_first_of("\r\n") == std::string::npos) header += std::string(key) + ": " + it->second + "\r\n"; }
        sent = true; return sendAll(client, header + "Connection: close\r\n\r\n");
    };
    Sink sink;
    sink.start = [&](const Response& r) { upstream = r; if (r.status != 200 && r.status != 206) { rejected = true; return false; } auto type = lower(upstream.headers["content-type"]); hls = hlsHint || type.find("mpegurl") != std::string::npos; return true; };
    sink.data = [&](const char* bytes, size_t n) {
        if (stopping_) return false;
        if (!decided) { pending.append(bytes, n); if (!hls && pending.size() < 10) return true; hls = hls || pending.rfind("#EXTM3U", 0) == 0; decided = true; if (!hls) { if (!startBody()) return false; if (method != "HEAD" && !sendAll(client, pending)) return false; pending.clear(); } }
        else if (hls) pending.append(bytes, n);
        else if (method != "HEAD" && !sendAll(client, bytes, n)) return false;
        return (!hls || pending.size() <= 2 * 1024 * 1024) && (hls || method != "HEAD");
    };
    try {
        http_.stream(req, sink, &stopping_);
        if (!decided) { hls = hls || pending.rfind("#EXTM3U", 0) == 0; if (!hls) { startBody(); if (method != "HEAD") sendAll(client, pending); } }
        if (hls) { auto body = rewriteHls(pending, upstream.finalUrl, [&](const std::string& url) { return add(url, resource.scope); }); sendAll(client, "HTTP/1.1 200 OK\r\nContent-Type: application/vnd.apple.mpegurl\r\nContent-Length: " + std::to_string(body.size()) + "\r\nConnection: close\r\n\r\n"); if (method != "HEAD") sendAll(client, body); }
    } catch (...) { if (!sent && !stopping_) simple(client, rejected && upstream.status == 416 ? 416 : 502, "Upstream Error"); }
}
}
