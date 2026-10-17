#pragma once
#include "json.hpp"
#include "network.hpp"
#include <optional>

namespace sunny {
struct Account {
    std::string base, userId, token, deviceId;
    bool ugreen = false;
};
struct Media {
    std::string id, title, type, overview, seriesId, imageTag, parentImageId;
    int year = 0;
    int64_t positionMs = 0, durationMs = 0;
    bool folder = false, favorite = false;
};
struct Page { std::vector<Media> items; int64_t total = 0; };
struct Subtitle { std::string url, language, title; bool isDefault = false; };
struct Playback {
    std::string url, fallbackUrl, sourceMediaUrl, itemId, sourceId, sessionId, method, title;
    Scope scope;
    int64_t startMs = 0, durationMs = 0;
    std::vector<Subtitle> subtitles;
};
int subtitlePriority(const std::string& language, const std::string& title);
class Emby {
    Transport& http_;
    Account account_;
    std::atomic_bool* cancel_;
    Json api(const std::string& path, const Headers& query = {}, const Json* body = nullptr);
    Media parseItem(const Json& j) const;
    Page parsePage(const Json& j) const;
public:
    Emby(Transport& http, Account account, std::atomic_bool* cancel = nullptr);
    static Account login(Transport&, const std::string& base, const std::string& username,
        const std::string& password, const std::string& deviceId, bool ugreen, std::atomic_bool* cancel = nullptr);
    const Account& account() const { return account_; }
    Scope scope() const;
    std::string endpoint(const std::string& path, const Headers& query = {}) const;
    Page views();
    Page resume();
    Page latest();
    Page list(const std::string& parent, int start = 0, const std::string& search = "");
    Page children(const Media& item, int start = 0);
    Media item(const std::string& id);
    Playback playback(const Media& item, bool fromStart = false);
    std::string imageUrl(const Media&, int width = 240) const;
    void report(const Playback&, const std::string& event, int64_t positionMs, bool paused);
};
struct CloudVariant { std::string id; int width = 0, height = 0; int64_t bandwidth = 0; std::string label() const; };
struct CloudSession { std::string id, token, url; CloudVariant variant; };
class CloudVideo {
    Transport& http_;
    std::string origin_, assetToken_;
    std::atomic_bool* cancel_;
    Json post(const std::string& path, const Json& body);
public:
    CloudVideo(Transport&, std::string sourceUrl, std::atomic_bool* cancel = nullptr);
    static bool supports(const std::string& sourceUrl);
    std::vector<CloudVariant> discover();
    CloudSession create(const CloudVariant&);
    void stop(const CloudSession&);
};
}
