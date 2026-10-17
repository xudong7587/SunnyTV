#include "sunny/player.hpp"
#include <SDL2/SDL_opengl.h>
#include <algorithm>
#include <cstdio>
#include <stdexcept>

namespace sunny {
namespace {
template <typename T> T gl(const char* name) { auto p = SDL_GL_GetProcAddress(name);
        if (!p) throw std::runtime_error("OpenGL 功能不可用");
    return reinterpret_cast<T>(p); }
using GenFramebuffers = void (*)(GLsizei, GLuint*);
using DeleteFramebuffers = void (*)(GLsizei, const GLuint*);
using BindFramebuffer = void (*)(GLenum, GLuint);
using FramebufferTexture2D = void (*)(GLenum, GLenum, GLenum, GLuint, GLint);
using CheckFramebufferStatus = GLenum (*)(GLenum);
using GetIntegerv = void (*)(GLenum, GLint*);
void* getProc(void*, const char* name) { return SDL_GL_GetProcAddress(name); }
void command(mpv_handle* h, const char* a, const char* b = nullptr, const char* c = nullptr, const char* d = nullptr) { const char* args[] = {a, b, c, d, nullptr};
        if (mpv_command(h, args) < 0) throw std::runtime_error("播放器操作失败"); }
double number(mpv_handle* h, const char* name) { double v = 0; mpv_get_property(h, name, MPV_FORMAT_DOUBLE, &v); return v; }
std::string text(mpv_handle* h, const char* name) { char* p = mpv_get_property_string(h, name);
        if (!p) return "";
    std::string s = p; mpv_free(p); return s; }
}
void Player::option(const char* key, const char* value) { if (mpv_set_option_string(mpv_, key, value) < 0) throw std::runtime_error(std::string("播放器参数不可用：") + key); }
Player::Player(SDL_Renderer* renderer) : renderer_(renderer) {
    mpv_ = mpv_create();
        if (!mpv_) throw std::runtime_error("播放器创建失败");
    try {
        option("config", "no"); option("terminal", "no"); option("msg-level", "all=no");
        option("vo", "libmpv"); option("ao", "hos,sdl"); option("hwdec", "auto-copy");
        option("idle", "yes"); option("keep-open", "no"); option("input-default-bindings", "no");
        option("input-vo-keyboard", "no"); option("load-scripts", "no");
        option("audio-channels", "stereo"); option("cache", "yes"); option("cache-secs", "20");
        option("demuxer-max-bytes", "67108864"); option("demuxer-max-back-bytes", "8388608");
        option("user-agent", UserAgent); option("sub-auto", "no");
        option("sub-fonts-dir", "sdmc:/switch/SunnyTV/fonts");
        option("sub-font", "sans-serif"); option("sub-font-size", "42");
        option("network-timeout", "60");
        if (mpv_initialize(mpv_) < 0) throw std::runtime_error("播放器初始化失败");
        mpv_opengl_init_params init{getProc, nullptr};
        mpv_render_param params[] = {{MPV_RENDER_PARAM_API_TYPE, const_cast<char*>(MPV_RENDER_API_TYPE_OPENGL)}, {MPV_RENDER_PARAM_OPENGL_INIT_PARAMS, &init}, {MPV_RENDER_PARAM_INVALID, nullptr}};
        if (mpv_render_context_create(&context_, mpv_, params) < 0) throw std::runtime_error("播放器图形初始化失败");
        SDL_RendererInfo info{}; SDL_GetRendererInfo(renderer_, &info);
        if (!info.name || std::string(info.name) != "opengl") throw std::runtime_error("需要 SDL OpenGL 渲染器");
        video_ = SDL_CreateTexture(renderer_, SDL_PIXELFORMAT_RGBA8888, SDL_TEXTUREACCESS_TARGET, 1280, 720);
        if (!video_) throw std::runtime_error("无法创建视频画面");
        SDL_RenderFlush(renderer_);
        if (SDL_GL_BindTexture(video_, nullptr, nullptr) != 0) throw std::runtime_error("无法绑定视频画面");
        GLint texture = 0, old = 0; gl<GetIntegerv>("glGetIntegerv")(GL_TEXTURE_BINDING_2D, &texture); gl<GetIntegerv>("glGetIntegerv")(GL_FRAMEBUFFER_BINDING, &old);
        gl<GenFramebuffers>("glGenFramebuffers")(1, &fbo_); gl<BindFramebuffer>("glBindFramebuffer")(GL_FRAMEBUFFER, fbo_);
        gl<FramebufferTexture2D>("glFramebufferTexture2D")(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, GLuint(texture), 0);
        bool complete = gl<CheckFramebufferStatus>("glCheckFramebufferStatus")(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
        gl<BindFramebuffer>("glBindFramebuffer")(GL_FRAMEBUFFER, GLuint(old)); SDL_GL_UnbindTexture(video_);
        if (!complete) throw std::runtime_error("视频画面缓冲初始化失败");
    } catch (...) {
        if (context_) mpv_render_context_free(context_);
        context_ = nullptr;
        if (mpv_) mpv_terminate_destroy(mpv_);
        mpv_ = nullptr;
        if (video_) SDL_DestroyTexture(video_);
        video_ = nullptr;
        throw;
    }
}
Player::~Player() {
    close();
        if (context_) mpv_render_context_free(context_);
        if (mpv_) mpv_terminate_destroy(mpv_);
    if (fbo_) gl<DeleteFramebuffers>("glDeleteFramebuffers")(1, &fbo_);
    if (video_) SDL_DestroyTexture(video_);
}
void Player::open(const Playback& playback, const std::string& secret) {
    close(); playback_ = playback; state_ = {}; subDelay_ = 0; triedFallback_ = false;
    gateway_ = std::make_unique<Gateway>(secret);
    mpv_set_property_string(mpv_, "start", std::to_string(double(playback.startMs) / 1000.0).c_str());
    int paused = 0; mpv_set_property(mpv_, "pause", MPV_FORMAT_FLAG, &paused);
    command(mpv_, "loadfile", gateway_->add(playback.url, playback.scope).c_str(), "replace");
}
void Player::close() {
    if (mpv_) { const char* args[] = {"stop", nullptr}; mpv_command(mpv_, args); }
    gateway_.reset(); state_ = {};
}
void Player::poll() {
    while (auto* event = mpv_wait_event(mpv_, 0)) {
        if (event->event_id == MPV_EVENT_NONE) break;
        if (event->event_id == MPV_EVENT_PLAYBACK_RESTART) state_.started = true;
        if (event->event_id == MPV_EVENT_FILE_LOADED) {
            state_.loaded = true; state_.ended = false; state_.failed = false;
            for (const auto& sub : playback_.subtitles) { try { command(mpv_, "sub-add", gateway_->add(sub.url, playback_.scope).c_str(), sub.isDefault ? "select" : "auto", sub.title.c_str()); } catch (...) {} }
            mpv_set_property_string(mpv_, "sub-delay", std::to_string(subDelay_).c_str());
        }
        if (event->event_id == MPV_EVENT_END_FILE) {
            auto* end = static_cast<mpv_event_end_file*>(event->data);
            if (end && end->reason == MPV_END_FILE_REASON_ERROR) {
                if (!state_.loaded && !triedFallback_ && !playback_.fallbackUrl.empty()) {
                    triedFallback_ = true; playback_.method = "DirectStream";
                    command(mpv_, "loadfile", gateway_->add(playback_.fallbackUrl, playback_.scope).c_str(), "replace");
                } else { state_.failed = true; state_.ended = true; }
            } else if (end && end->reason == MPV_END_FILE_REASON_EOF) state_.ended = true;
        }
    }
    if (state_.loaded) {
        state_.position = number(mpv_, "time-pos"); state_.duration = number(mpv_, "duration");
        int paused = 0; mpv_get_property(mpv_, "pause", MPV_FORMAT_FLAG, &paused); state_.paused = paused;
        state_.width = int(number(mpv_, "width")); state_.height = int(number(mpv_, "height"));
        state_.decoder = text(mpv_, "hwdec-current"); state_.videoCodec = text(mpv_, "video-format"); state_.audioCodec = text(mpv_, "audio-codec-name");
    }
}
void Player::draw() {
    if (!state_.loaded) return;
    SDL_RenderFlush(renderer_);
    GLint old = 0; gl<GetIntegerv>("glGetIntegerv")(GL_FRAMEBUFFER_BINDING, &old);
    mpv_opengl_fbo fbo{int(fbo_), 1280, 720, 0}; int flip = 0, block = 0;
    mpv_render_param params[] = {{MPV_RENDER_PARAM_OPENGL_FBO, &fbo}, {MPV_RENDER_PARAM_FLIP_Y, &flip}, {MPV_RENDER_PARAM_BLOCK_FOR_TARGET_TIME, &block}, {MPV_RENDER_PARAM_INVALID, nullptr}};
    mpv_render_context_render(context_, params);
    gl<BindFramebuffer>("glBindFramebuffer")(GL_FRAMEBUFFER, GLuint(old));
    SDL_RenderCopy(renderer_, video_, nullptr, nullptr);
}
void Player::pause() { command(mpv_, "cycle", "pause"); }
void Player::seek(double seconds) { command(mpv_, "seek", std::to_string(seconds).c_str(), "relative+exact"); }
void Player::cycleAudio() { command(mpv_, "cycle", "audio"); }
void Player::cycleSubtitle() { command(mpv_, "cycle", "sub"); }
void Player::subtitleDelay(double seconds) { setSubtitleDelay(subDelay_ + seconds); }
void Player::setSubtitleDelay(double seconds) { subDelay_ = std::clamp(seconds, -3600.0, 3600.0); mpv_set_property_string(mpv_, "sub-delay", std::to_string(subDelay_).c_str()); }
void Player::switchUrl(const std::string& url) { auto position = state_.position; int paused = state_.paused; mpv_set_property_string(mpv_, "start", std::to_string(position).c_str()); mpv_set_property(mpv_, "pause", MPV_FORMAT_FLAG, &paused); command(mpv_, "loadfile", gateway_->add(url, {}).c_str(), "replace"); }
}
