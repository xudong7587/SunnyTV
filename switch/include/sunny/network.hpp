#pragma once
#include <atomic>
#include <functional>
#include <map>
#include <string>
#include <vector>

namespace sunny {
using Headers = std::map<std::string, std::string>;
constexpr const char* UserAgent = "SunnyTV/0.2.9 (Nintendo Switch; libmpv)";
std::string lower(std::string s);
std::string trim(std::string s);
std::string encode(const std::string& s);
struct Url { std::string raw, scheme, host, port, path, query; std::string origin() const; };
Url validateUrl(const std::string& raw);
std::string serverBase(const std::string& raw, bool ugreen = false);
std::string resolveUrl(const std::string& base, const std::string& reference);
bool isScoped(const std::string& target, const std::string& base);
bool ambiguousPath(std::string path);
struct Scope { std::string base; Headers headers; };
struct Request {
    std::string url, method = "GET", body;
    Scope scope;
    Headers headers;
    size_t maxBytes = 8 * 1024 * 1024;
    bool playback = false;
};
struct Response { long status = 0; std::string body, finalUrl; Headers headers; };
struct Sink {
    std::function<bool(const Response&)> start;
    std::function<bool(const char*, size_t)> data;
};
class Transport {
public:
    virtual ~Transport() = default;
    virtual Response fetch(const Request&, const std::atomic_bool* cancel = nullptr) = 0;
};
class Http final : public Transport {
public:
    Response fetch(const Request&, const std::atomic_bool* cancel = nullptr) override;
    Response stream(const Request&, const Sink&, const std::atomic_bool* cancel = nullptr);
};
std::string parseStrm(const std::string& text);
bool looksLikeStrm(const std::string& url);
std::string resolveStrm(Transport&, std::string url, const Scope&, const std::atomic_bool* cancel = nullptr);
std::string rewriteHls(const std::string& body, const std::string& base,
    const std::function<std::string(const std::string&)>& registerUrl);
}
