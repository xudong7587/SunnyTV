#include "sunny/player.hpp"
#include <SDL2/SDL_opengl.h>
#include <algorithm>
#include <cstdio>
#include <cstdlib>
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
using Finish = void (*)();
// mpv resets GL state to defaults, whereas SDL caches its own shaders, buffers, blend and
// clip state. Keep the decoder renderer in a shared context so SDL's state stays intact.
class CurrentContext {
    SDL_Window* window_ = SDL_GL_GetCurrentWindow();
    SDL_GLContext context_ = SDL_GL_GetCurrentContext();
public:
    CurrentContext(SDL_Window* window, SDL_GLContext context) {
        if (SDL_GL_MakeCurrent(window, context) != 0)
            throw std::runtime_error("无法切换播放器图形上下文");
    }
    ~CurrentContext() { SDL_GL_MakeCurrent(window_, context_); }
};
void* getProc(void*, const char* name) { return SDL_GL_GetProcAddress(name); }
void command(mpv_handle* h, const char* a, const char* b = nullptr, const char* c = nullptr, const char* d = nullptr) { const char* args[] = {a, b, c, d, nullptr};
        if (mpv_command(h, args) < 0) throw std::runtime_error("播放器操作失败"); }
double number(mpv_handle* h, const char* name) { double v = 0; mpv_get_property(h, name, MPV_FORMAT_DOUBLE, &v); return v; }
std::string text(mpv_handle* h, const char* name) { char* p = mpv_get_property_string(h, name);
        if (!p) return "";
    std::string s = p; mpv_free(p); return s; }
}
void Player::option(const char* key, const char* value, bool allowMissing) {
    const int result = mpv_set_option_string(mpv_, key, value);
    // Horizon's libmpv has no scripting backends. In that build load-scripts does not exist;
    // there is consequently no script loader to disable. Invalid values and required options
    // must still fail visibly, rather than hiding an unrelated initialization failure.
    if (allowMissing && result == MPV_ERROR_OPTION_NOT_FOUND) return;
    if (result < 0) throw std::runtime_error(std::string("播放器参数不可用：") + key + "（" + mpv_error_string(result) + "）");
}
Player::Player(SDL_Renderer* renderer) : renderer_(renderer) {
    SDL_RendererInfo info{};
    if (SDL_GetRendererInfo(renderer_, &info) != 0 || !info.name ||
        (std::string(info.name) != "opengl" && std::string(info.name) != "opengles2") ||
        !(info.flags & SDL_RENDERER_TARGETTEXTURE) || !SDL_GL_GetCurrentContext())
        throw std::runtime_error("当前图形后端不支持视频输出");
    window_ = SDL_GL_GetCurrentWindow();
    mpv_ = mpv_create();
        if (!mpv_) throw std::runtime_error("播放器创建失败");
    try {
        option("config", "no"); option("terminal", "no"); option("msg-level", "all=no");
#ifndef __SWITCH__
        if(std::getenv("SUNNYTV_TEST_GL_TRACE")){option("terminal","yes");option("msg-level","all=warn,libmpv_render=debug");}
#endif
        option("vo", "libmpv"); option("ao", "hos,sdl"); option("hwdec", "auto-copy");
        option("idle", "yes"); option("keep-open", "no"); option("input-default-bindings", "no");
        option("input-vo-keyboard", "no"); option("load-scripts", "no", true);
        option("audio-channels", "stereo"); option("cache", "yes"); option("cache-secs", "20");
        option("demuxer-max-bytes", "67108864"); option("demuxer-max-back-bytes", "8388608");
        option("user-agent", UserAgent); option("sub-auto", "no");
        option("sub-fonts-dir", "sdmc:/switch/SunnyTV/fonts");
        option("sub-font", "sans-serif"); option("sub-font-size", "42");
        option("network-timeout", "60");
#ifdef __SWITCH__
        // Switchfin also synchronizes Mesa GL before handing frames back to its UI.
        option("vd-lavc-threads", "3"); option("opengl-glfinish", "yes");
#endif
        if (mpv_initialize(mpv_) < 0) throw std::runtime_error("播放器初始化失败");
        // RGBA bytes on little endian machines; GLES2 supports this format natively.
        video_ = SDL_CreateTexture(renderer_, SDL_PIXELFORMAT_ABGR8888, SDL_TEXTUREACCESS_TARGET, 1280, 720);
        if (!video_) throw std::runtime_error("无法创建视频画面");
        SDL_RenderFlush(renderer_);
        if (SDL_GL_BindTexture(video_, nullptr, nullptr) != 0) throw std::runtime_error("无法绑定视频画面");
        GLint texture = 0;
        gl<GetIntegerv>("glGetIntegerv")(GL_TEXTURE_BINDING_2D, &texture);
        SDL_GL_UnbindTexture(video_);
        gl<Finish>("glFinish")();
        auto uiContext = SDL_GL_GetCurrentContext();
        int share = 0; SDL_GL_GetAttribute(SDL_GL_SHARE_WITH_CURRENT_CONTEXT, &share);
        SDL_GL_SetAttribute(SDL_GL_SHARE_WITH_CURRENT_CONTEXT, 1);
        videoContext_ = SDL_GL_CreateContext(window_);
        SDL_GL_SetAttribute(SDL_GL_SHARE_WITH_CURRENT_CONTEXT, share);
        SDL_GL_MakeCurrent(window_, uiContext);
        if (!videoContext_) throw std::runtime_error("无法创建共享视频图形上下文");
        {
            CurrentContext current(window_, videoContext_);
            mpv_opengl_init_params init{getProc, nullptr};
            mpv_render_param params[] = {{MPV_RENDER_PARAM_API_TYPE, const_cast<char*>(MPV_RENDER_API_TYPE_OPENGL)}, {MPV_RENDER_PARAM_OPENGL_INIT_PARAMS, &init}, {MPV_RENDER_PARAM_INVALID, nullptr}};
            if (mpv_render_context_create(&context_, mpv_, params) < 0) throw std::runtime_error("播放器图形初始化失败");
            gl<GenFramebuffers>("glGenFramebuffers")(1, &fbo_);
            gl<BindFramebuffer>("glBindFramebuffer")(GL_FRAMEBUFFER, fbo_);
            gl<FramebufferTexture2D>("glFramebufferTexture2D")(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, GLuint(texture), 0);
            if (gl<CheckFramebufferStatus>("glCheckFramebufferStatus")(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw std::runtime_error("视频画面缓冲初始化失败");
        }
    } catch (...) {
        if (videoContext_) {
            { CurrentContext current(window_, videoContext_);
              if (context_) mpv_render_context_free(context_);
              if (fbo_) gl<DeleteFramebuffers>("glDeleteFramebuffers")(1, &fbo_); }
            SDL_GL_DeleteContext(videoContext_);
        }
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
    if (videoContext_) {
        { CurrentContext current(window_, videoContext_);
          if (context_) mpv_render_context_free(context_);
          if (fbo_) gl<DeleteFramebuffers>("glDeleteFramebuffers")(1, &fbo_);
          gl<Finish>("glFinish")(); }
        SDL_GL_DeleteContext(videoContext_);
    }
    if (mpv_) mpv_terminate_destroy(mpv_);
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
    gl<Finish>("glFinish")();
    {
        CurrentContext current(window_, videoContext_);
        mpv_render_context_update(context_);
        // SDL target textures already use their bottom row as the UI's top row.
        mpv_opengl_fbo fbo{int(fbo_), 1280, 720, 0}; int flip = 0, block = 0;
        mpv_render_param params[] = {{MPV_RENDER_PARAM_OPENGL_FBO, &fbo}, {MPV_RENDER_PARAM_FLIP_Y, &flip}, {MPV_RENDER_PARAM_BLOCK_FOR_TARGET_TIME, &block}, {MPV_RENDER_PARAM_INVALID, nullptr}};
        const int result=mpv_render_context_render(context_, params);
        if(result<0)throw std::runtime_error(std::string("视频画面输出失败：")+mpv_error_string(result));
#ifndef __SWITCH__
        if(std::getenv("SUNNYTV_TEST_GL_TRACE")) {
            static int samples=0;
            if(state_.position>.5&&samples++<2) {
                using ReadPixels=void (*)(GLint,GLint,GLsizei,GLsizei,GLenum,GLenum,void*);
                gl<BindFramebuffer>("glBindFramebuffer")(GL_FRAMEBUFFER,fbo_);
                unsigned char pixel[4]{};gl<ReadPixels>("glReadPixels")(640,80,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
                std::fprintf(stderr,"Video FBO pixel: %u %u %u %u; frame %.3f\n",pixel[0],pixel[1],pixel[2],pixel[3],state_.position);
                gl<BindFramebuffer>("glBindFramebuffer")(GL_FRAMEBUFFER,0);
            }
        }
#endif
        // Shared texture writes must finish before the UI context samples the frame.
        gl<Finish>("glFinish")();
    }
    SDL_RenderCopy(renderer_, video_, nullptr, nullptr);
}
void Player::presented() {
    if (context_) { CurrentContext current(window_, videoContext_); mpv_render_context_report_swap(context_); }
}
void Player::pause() { command(mpv_, "cycle", "pause"); }
void Player::seek(double seconds) { command(mpv_, "seek", std::to_string(seconds).c_str(), "relative+exact"); }
void Player::cycleAudio() { command(mpv_, "cycle", "audio"); }
void Player::cycleSubtitle() { command(mpv_, "cycle", "sub"); }
void Player::subtitleDelay(double seconds) { setSubtitleDelay(subDelay_ + seconds); }
void Player::setSubtitleDelay(double seconds) { subDelay_ = std::clamp(seconds, -3600.0, 3600.0); mpv_set_property_string(mpv_, "sub-delay", std::to_string(subDelay_).c_str()); }
void Player::switchUrl(const std::string& url) { auto position = state_.position; int paused = state_.paused; mpv_set_property_string(mpv_, "start", std::to_string(position).c_str()); mpv_set_property(mpv_, "pause", MPV_FORMAT_FLAG, &paused); command(mpv_, "loadfile", gateway_->add(url, {}).c_str(), "replace"); }
}
