#pragma once
#include "network.hpp"
#include <mutex>
#include <thread>

namespace sunny {
// Loopback-only streaming proxy. Every request, including HLS keys and segments, goes through
// Http's redirect/TLS/header policy. mpv never receives the Emby token or remote signed URL.
class Gateway {
    struct Resource { std::string url; Scope scope; };
    Http http_;
    int listener_ = -1;
    unsigned port_ = 0;
    std::string secret_;
    std::atomic_bool stopping_{false};
    std::mutex mutex_;
    std::map<std::string, Resource> resources_;
    std::map<std::string, std::string> ids_;
    std::vector<std::thread> workers_;
    void serve();
    void handle(int client);
public:
    explicit Gateway(std::string randomSecret);
    ~Gateway();
    Gateway(const Gateway&) = delete;
    Gateway& operator=(const Gateway&) = delete;
    std::string add(const std::string& url, const Scope& scope = {});
};
}
