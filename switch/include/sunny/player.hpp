#pragma once
#include "gateway.hpp"
#include "media.hpp"
#include <SDL2/SDL.h>
#include <mpv/client.h>
#include <mpv/render_gl.h>
#include <memory>

namespace sunny {
struct PlayerState {
    bool loaded = false, started = false, paused = false, ended = false, failed = false;
    double position = 0, duration = 0;
    std::string decoder, videoCodec, audioCodec;
    int width = 0, height = 0;
};
class Player {
    SDL_Renderer* renderer_;
    SDL_Texture* video_ = nullptr;
    mpv_handle* mpv_ = nullptr;
    mpv_render_context* context_ = nullptr;
    unsigned fbo_ = 0;
    std::unique_ptr<Gateway> gateway_;
    Playback playback_;
    PlayerState state_;
    bool triedFallback_ = false;
    double subDelay_ = 0;
    void option(const char*, const char*);
public:
    explicit Player(SDL_Renderer* renderer);
    ~Player();
    void open(const Playback& playback, const std::string& randomSecret);
    void close();
    void poll();
    void draw();
    void pause();
    void seek(double seconds);
    void cycleAudio();
    void cycleSubtitle();
    void subtitleDelay(double seconds);
    void setSubtitleDelay(double seconds);
    double subtitleDelay() const { return subDelay_; }
    void switchUrl(const std::string& url);
    const PlayerState& state() const { return state_; }
    const Playback& playback() const { return playback_; }
};
}
