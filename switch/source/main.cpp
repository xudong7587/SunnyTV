#include "sunny/media.hpp"
#include "sunny/player.hpp"
#include "sunny/worker.hpp"
#include "sunny/navigation.hpp"
#include "sunny/ui.hpp"
#include "sunny/platform.hpp"
#include <SDL2/SDL.h>
#include <SDL2/SDL_image.h>
#include <SDL2/SDL_ttf.h>
#include <curl/curl.h>
#include <algorithm>
#include <array>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <fstream>
#include <functional>
#include <optional>
#include <set>
#include <sys/stat.h>

using namespace sunny;
namespace {
constexpr const char* Root = "sdmc:/switch/SunnyTV";
std::string randomHex() { unsigned char bytes[24]; randomGet(bytes, sizeof bytes); std::string s; for (auto c : bytes) { s += "0123456789abcdef"[c >> 4]; s += "0123456789abcdef"[c & 15]; } return s; }
std::string keyboard(const std::string& title, const std::string& initial = "", bool password = false) {
    SwkbdConfig config;
        if (R_FAILED(swkbdCreate(&config, 0))) throw std::runtime_error("系统键盘不可用，请使用完整应用模式运行");
    if (password) swkbdConfigMakePresetPassword(&config); else swkbdConfigMakePresetDefault(&config);
    swkbdConfigSetHeaderText(&config, title.c_str()); swkbdConfigSetInitialText(&config, initial.c_str()); swkbdConfigSetStringLenMax(&config, 2048);
    std::array<char, 8193> text{}; Result rc = swkbdShow(&config, text.data(), text.size()); swkbdClose(&config);
    return R_SUCCEEDED(rc) ? std::string(text.data()) : initial;
}
Json readConfig() { std::ifstream in(std::string(Root) + "/config.json", std::ios::binary);
        if (!in) return Json::Object{}; std::string text; char data[4096]; while (in.read(data, sizeof data) || in.gcount()) { if (text.size() + size_t(in.gcount()) > 65536) throw std::runtime_error("本地配置过大"); text.append(data, size_t(in.gcount())); } return Json::parse(text, 65536); }
void writeConfig(const Json& config) { auto path = std::string(Root) + "/config.json"; { std::ofstream out(path + ".tmp", std::ios::binary | std::ios::trunc);
        if (!out) throw std::runtime_error("无法保存设置"); out << config.dump(); out.flush();
        if (!out) throw std::runtime_error("保存设置失败"); } if (std::rename((path + ".tmp").c_str(), path.c_str()) != 0) throw std::runtime_error("无法替换设置文件"); }
bool safeImage(const std::string& s) {
    auto byte = [&](size_t i) { return static_cast<unsigned char>(s[i]); };
    uint64_t w = 0, h = 0;
    if (s.size() >= 24 && s.compare(0, 8, "\x89PNG\r\n\x1a\n", 8) == 0) { for (int i = 0; i < 4; ++i) { w = (w << 8) | byte(16 + i); h = (h << 8) | byte(20 + i); } }
    else if (s.size() > 4 && byte(0) == 255 && byte(1) == 216) {
        size_t p = 2; while (p + 4 < s.size()) { if (byte(p++) != 255) break; while (p < s.size() && byte(p) == 255) ++p;
        if (p >= s.size()) break; unsigned marker = byte(p++);
        if (marker == 217 || marker == 218) break;
        if (marker == 1 || (marker >= 208 && marker <= 215)) continue;
        if (p + 2 > s.size()) break; size_t length = size_t(byte(p)) * 256 + byte(p + 1);
        if (length < 2 || length > s.size() - p) break;
        if ((marker >= 192 && marker <= 195) || (marker >= 197 && marker <= 199) || (marker >= 201 && marker <= 203) || (marker >= 205 && marker <= 207)) { if (length < 7) break; h = byte(p + 3) * 256 + byte(p + 4); w = byte(p + 5) * 256 + byte(p + 6); break; } p += length; }
    }
    return w && h && w <= 2048 && h <= 2048 && w * h <= 4194304;
}
std::string clockTime(double value) { int t = int(std::max(0.0, value)); char s[32]; std::snprintf(s, sizeof s, "%02d:%02d:%02d", t / 3600, t / 60 % 60, t % 60); return s; }
enum class Screen { Connect, Home, Libraries, Grid, Detail, Settings };
struct View {
    Screen screen = Screen::Home;
    std::string title, parent, search;
    Media container;
    Page page, libraries, resume;
    int selected = 0, offset = 0, heroIndex = 0, section = 0;
    float scroll = 0, targetScroll = 0;
    bool hero = false;
};
class App {
    SDL_Renderer* renderer_;
    TTF_Font* normal_ = nullptr;
    TTF_Font* small_ = nullptr;
    TTF_Font* large_ = nullptr;
    PadState pad_{};
    Http http_;
    Worker api_, images_, reports_;
    std::future<std::function<void()>> pending_;
    struct Image { SDL_Texture* texture = nullptr; uint32_t used = 0; std::future<std::shared_ptr<SDL_Surface>> future; };
    std::map<std::string, Image> imageCache_;
    std::unique_ptr<Player> player_;
    Account account_;
    Json config_ = Json::Object{};
    View view_;
    std::vector<View> history_;
    Media detail_;
    std::string base_, username_, password_, status_, device_;
    bool ugreen_ = false, remember_ = false, busy_ = false, playing_ = false, reported_ = false, showInfo_ = false;
    bool menu_ = false, run_ = true;
    int menuIndex_ = 0, subtitleStep_ = 0;
    uint32_t lastInput_ = 0, reportAt_ = 0;
    NavigationRepeat navigation_;
    TouchTracker touch_;
    std::unique_ptr<Canvas> canvas_;
    struct Hit { Bounds bounds; std::function<void(Point)> action; };
    std::vector<Hit> hits_;
    bool dark_ = true;
    int accent_ = 2, navigationIndex_ = 0;
    int heroLeftTravel_ = 0;
    int settingsCategory_ = 0;
    bool settingsSidebar_ = false;
    float contentHeight_ = 720;
    std::vector<CloudVariant> variants_;
    std::optional<CloudSession> cloud_;
    std::string cloudSource_;
    PlFontData fontData_{};
    std::map<std::string, SDL_Texture*> textCache_;
    void color(SDL_Color c) { SDL_SetRenderDrawColor(renderer_, c.r, c.g, c.b, c.a); }
    void rect(int x, int y, int w, int h, SDL_Color c) { color(c); SDL_Rect r{x,y,w,h}; SDL_RenderFillRect(renderer_, &r); }
    Palette palette() const { return sunnyPalette(dark_, accent_); }
    void text(const std::string& value, int x, int y, int size = 24, SDL_Color c = {243,245,239,255}, int maxWidth = 1200) {
        if (c.r == 243 && c.g == 245 && c.b == 239) c = palette().text;
        canvas_->text(value, float(x), float(y), size, c, float(maxWidth));
    }
    void hit(Bounds bounds, std::function<void(Point)> action, bool overlay = false) {
        float top=overlay||playing_?0.f:100.f;
        float bottom=std::min(720.f,bounds.y+bounds.h);
        bounds.y=std::max(top,bounds.y);bounds.h=bottom-bounds.y;
        if(bounds.h>0)hits_.push_back({bounds,std::move(action)});
    }
    bool hasHero() const { return view_.screen == Screen::Home || view_.hero; }
    void scrollTo(float value) { view_.targetScroll = std::clamp(value, 0.f, std::max(0.f, contentHeight_ - 720)); }
    void playItem(Media item) {
        if (item.folder) { selectMedia(item); return; }
        auto a = account_;
        task([=, this] { Emby source(http_, a, &api_.cancel); auto p = source.playback(item); return [this, p] { startPlayer(p); }; });
    }
    template <typename F> void task(F f) { if (busy_) return; api_.cancel = false; busy_ = true; pending_ = api_.submit([f=std::move(f)]() mutable -> std::function<void()> { return f(); }); }
    void save() {
        config_["dark"] = dark_; config_["accent"] = accent_;
        config_["base"] = base_; config_["username"] = username_; config_["device"] = device_; config_["ugreen"] = ugreen_; config_["remember"] = remember_;
        config_["userId"] = remember_ ? account_.userId : ""; config_["token"] = remember_ ? account_.token : ""; writeConfig(config_);
    }
    void clearImages() { for (auto& [key, image] : imageCache_) if (image.texture) SDL_DestroyTexture(image.texture); imageCache_.clear(); }
    void show(View next, bool push = true) { if (push) history_.push_back(view_); view_ = std::move(next); heroLeftTravel_=0; }
    int heroCount() const { return std::min(view_.screen==Screen::Home?7:8,int(view_.page.items.size())); }
    void rotateHero(int direction) {
        int count=heroCount();if(!count)return;
        view_.heroIndex=(view_.heroIndex+direction+count)%count;
        heroLeftTravel_=direction<0?heroLeftTravel_+1:0;
    }
    void loadScreen(Screen screen, std::string parent = "", Media container = {}, int offset = 0, std::string search = "", bool push = true) {
        auto a = account_; task([=, this] {
            Emby source(http_, a, &api_.cancel); Page page, libraries, resume; std::string title;
            if (screen == Screen::Home) {
                libraries = source.views(); resume = source.resume(); page = source.latest();
                if (page.items.empty()) page = resume;
                title = "首页";
            } else if (screen == Screen::Libraries) { page = source.views(); title = "我的媒体库"; }
            else { page = container.id.empty() ? source.list(parent, offset, search) : source.children(container, offset); title = search.empty() ? container.title : "搜索：" + search; }
            return [=, this]() mutable {
                View next; next.screen = screen; next.title = title; next.parent = parent; next.search = search;
                next.container = container; next.offset = offset; next.page = std::move(page);
                next.libraries = std::move(libraries); next.resume = std::move(resume);
                next.hero = screen == Screen::Grid && container.type == "CollectionFolder" && offset == 0 && !next.page.items.empty();
                next.section = (screen == Screen::Home || next.hero) ? 0 : 3;
                show(std::move(next), push);
            };
        });
    }
    void login() {
        auto base = base_, name = username_, password = password_; bool ugreen = ugreen_;
        task([=, this] { auto account = Emby::login(http_, base, name, password, device_, ugreen, &api_.cancel); return [this, account]() { clearImages(); account_ = account; base_ = account.base; password_.assign(password_.size(), '\0'); password_.clear(); save(); history_.clear(); loadScreen(Screen::Home, "", {}, 0, "", false); }; });
    }
    void selectMedia(const Media& m) {
        if (m.folder) { loadScreen(Screen::Grid, m.id, m); return; }
        auto a = account_; task([=, this] { Emby source(http_, a, &api_.cancel); auto item = source.item(m.id); return [this, item] { detail_ = item; View next; next.screen = Screen::Detail; next.title = item.title; show(next); }; });
    }
    void startMedia(bool fromStart) {
        auto a = account_; auto item = detail_;
        task([=, this] { Emby source(http_, a, &api_.cancel); auto playback = source.playback(item, fromStart); return [this, playback] { startPlayer(playback); }; });
    }
    void startPlayer(const Playback& p) {
        if (!player_) player_ = std::make_unique<Player>(renderer_);
        player_->open(p, randomHex()); playing_ = true; reported_ = false; menu_ = false; variants_.clear(); cloudSource_ = p.sourceMediaUrl; lastInput_ = SDL_GetTicks(); status_.clear();
    }
    void report(const std::string& event) {
        if (!reported_ && event != "Playing") return;
        auto p = player_->playback();
        if (p.itemId.empty() || account_.token.empty()) return;
        auto state = player_->state(); auto account = account_;
        reports_.submit([this, account, p, state, event] { try { Emby source(http_, account, &reports_.cancel); source.report(p, event, int64_t(state.position * 1000), state.paused); } catch (...) { /* A progress failure must not stop playback. */ } });
    }
    void stopCloud() {
        if (!cloud_) return; auto s = *cloud_; auto url = cloudSource_; cloud_.reset();
        reports_.submit([this, s, url] { try { CloudVideo(http_, url, &reports_.cancel).stop(s); } catch (...) {} });
    }
    void stopPlayer() { if (!playing_) return; report("Playing/Stopped"); stopCloud(); player_->close(); playing_ = false; menu_ = false; reported_ = false; }
    void direct() {
        auto url = keyboard("输入 HTTP(S) 视频或 STRM 地址");
        if (url.empty()) return;
        task([this, url] { Playback p; p.url = resolveStrm(http_, url, {}, &api_.cancel); p.title = "直接播放"; p.sourceMediaUrl = url; return [this, p] { startPlayer(p); }; });
    }
    void cloudMenu() {
        if (!CloudVideo::supports(cloudSource_)) { status_ = "需要 MediaIndex 的原始播放入口才能查询清晰度"; return; }
        auto url = cloudSource_; task([this, url] { auto list = CloudVideo(http_, url, &api_.cancel).discover(); return [this, list] { variants_ = list; menuIndex_ = 0; }; });
    }
    void chooseCloud(const CloudVariant& v) {
        auto url = cloudSource_; task([this, url, v] { auto session = CloudVideo(http_, url, &api_.cancel).create(v); return [this, session] { stopCloud(); player_->switchUrl(session.url); cloud_ = session; variants_.clear(); menu_ = false; }; });
    }
    void pollImages() {
        for (auto& [key, image] : imageCache_) {
            if (!image.future.valid() || image.future.wait_for(std::chrono::seconds(0)) != std::future_status::ready) continue;
            try { auto surface = image.future.get(); if (surface) image.texture = SDL_CreateTextureFromSurface(renderer_, surface.get()); } catch (...) {}
        }
    }
    SDL_Texture* imageFor(const Media& media, const std::string& type = "Primary", int width = 360) {
        if (media.id.empty() || (type == "Backdrop" && media.backdropTag.empty()) || (type == "Logo" && media.logoTag.empty())) return nullptr;
        std::string key = account_.base + ':' + media.id + ':' + type + ':' + std::to_string(width) + ':' + media.imageTag + ':' + media.backdropTag + ':' + media.logoTag;
        auto found = imageCache_.find(key);
        if (found != imageCache_.end()) { found->second.used = SDL_GetTicks(); return found->second.texture; }
        if (imageCache_.size() >= 80) {
            auto oldest = std::min_element(imageCache_.begin(), imageCache_.end(), [](const auto& a, const auto& b) { return a.second.used < b.second.used; });
            if (oldest->second.texture) SDL_DestroyTexture(oldest->second.texture);
            imageCache_.erase(oldest);
        }
        if (!account_.token.empty()) {
            Emby source(http_, account_); Request req; req.url = source.imageUrl(media, width, type); req.scope = source.scope(); req.maxBytes = 4 * 1024 * 1024;
            Image image; image.used = SDL_GetTicks();
            try {
                image.future = images_.submit([this, req] {
                    auto response = http_.fetch(req, &images_.cancel);
                    if (response.status != 200 || !safeImage(response.body)) return std::shared_ptr<SDL_Surface>{};
                    auto* rw = SDL_RWFromConstMem(response.body.data(), int(response.body.size()));
                    return std::shared_ptr<SDL_Surface>(rw ? IMG_Load_RW(rw, 1) : nullptr, SDL_FreeSurface);
                });
                imageCache_.emplace(key, std::move(image));
            } catch (...) {}
        }
        return nullptr;
    }
    void artwork(const Media& media, Bounds bounds, float radius = 14, bool cover = false, const std::string& type = "Primary") {
        canvas_->round(bounds, radius, palette().surface);
        canvas_->image(imageFor(media, type, type == "Backdrop" ? 1280 : 480), bounds, radius, cover);
    }
    void button(const std::string& label, int index, int selected, int x, int y, int width = 340) {
        auto p = palette(); bool focus = index == selected;
        canvas_->round({float(x),float(y),float(width),52},26,focus ? p.ink : p.surface);
        if (focus) canvas_->outline({float(x),float(y),float(width),52},26,p.accent);
        text(label,x+20,y+10,22,focus ? p.accent : p.text,width-40);
    }
#include "screens.inc"
    void nav(Screen screen) { history_.clear();
        if (screen == Screen::Settings) { settingsCategory_=0;settingsSidebar_=false;View v; v.screen=screen; show(v,false); } else loadScreen(screen,"",{},0,"",false); }
#include "input.inc"

#include "preview.inc"
public:
    explicit App(SDL_Renderer* renderer) : renderer_(renderer) {
        mkdir(Root,0777); mkdir((std::string(Root)+"/fonts").c_str(),0777);
        if(R_FAILED(plGetSharedFontByType(&fontData_,PlSharedFontType_ChineseSimplified))) throw std::runtime_error("无法读取系统中文字体");
        auto openFont=[&](int size) { return TTF_OpenFontRW(SDL_RWFromConstMem(fontData_.address,int(fontData_.size)),1,size); };
        canvas_=std::make_unique<Canvas>(renderer_,fontData_.address,fontData_.size);
        normal_=openFont(24); small_=openFont(20); large_=openFont(36);
        if(!normal_||!small_||!large_) throw std::runtime_error("字体初始化失败");
        // A device-local copy lets libass discover the system font. It is never part of the NRO.
        auto fontPath=std::string(Root)+"/fonts/system-cjk.ttf"; std::ifstream existing(fontPath,std::ios::binary);
        if(!existing) { std::ofstream out(fontPath,std::ios::binary); out.write(static_cast<const char*>(fontData_.address),fontData_.size); }
        try { config_=readConfig(); } catch(const std::exception& e) { status_=e.what(); }
        dark_=config_["dark"].boolean(true); accent_=int(config_["accent"].integer(2));
        base_=config_["base"].text(); username_=config_["username"].text(); ugreen_=config_["ugreen"].boolean(); remember_=config_["remember"].boolean(); device_=config_["device"].text();
        if(device_.empty()) device_=randomHex();
        if(remember_) account_={base_,config_["userId"].text(),config_["token"].text(),device_,ugreen_};
        padConfigureInput(1,HidNpadStyleSet_NpadStandard); padInitializeDefault(&pad_);
        if(account_.token.empty()) view_.screen=Screen::Connect; else loadScreen(Screen::Home,"",{},0,"",false);
    }
    ~App() {
        stopPlayer(); api_.shutdown(); images_.shutdown(); reports_.shutdown();
        player_.reset(); canvas_.reset(); clearImages(); for(auto& [k,v]:textCache_) SDL_DestroyTexture(v);
        if(normal_) TTF_CloseFont(normal_);
        if(small_) TTF_CloseFont(small_);
        if(large_) TTF_CloseFont(large_);
    }
    void run() {
        while(run_ && appletMainLoop()) {
            SDL_Event nextEvent; while(SDL_PollEvent(&nextEvent)) { try { event(nextEvent); } catch(const std::exception& error) { status_=error.what(); } }
            padUpdate(&pad_);
            auto down = padGetButtonsDown(&pad_);
            const auto held = padGetButtons(&pad_);
            const auto stick = padGetStickPos(&pad_, 0);
            const uint32_t now = SDL_GetTicks();
            uint8_t digital = 0;
            if (held & HidNpadButton_Left) digital |= NavLeft;
            if (held & HidNpadButton_Right) digital |= NavRight;
            if (held & HidNpadButton_Up) digital |= NavUp;
            if (held & HidNpadButton_Down) digital |= NavDown;
            const auto navigation = navigation_.update(digital, stick.x, stick.y, now);
            down &= ~(HidNpadButton_Left | HidNpadButton_Right | HidNpadButton_Up | HidNpadButton_Down);
            if (navigation & NavLeft) down |= HidNpadButton_Left;
            if (navigation & NavRight) down |= HidNpadButton_Right;
            if (navigation & NavUp) down |= HidNpadButton_Up;
            if (navigation & NavDown) down |= HidNpadButton_Down;
            try {
                if(pending_.valid() && pending_.wait_for(std::chrono::seconds(0))==std::future_status::ready) { busy_=false; auto apply=pending_.get(); apply(); }
                pollImages();
        if(down) input(down);
                if(playing_) { player_->poll(); auto s=player_->state();
        if(s.started&&!reported_) { report("Playing"); reported_=true; reportAt_=now; } if(reported_&&now-reportAt_>10000) { report("Playing/Progress"); reportAt_=now; } if(s.ended) { bool failed=s.failed; stopPlayer();
        if(failed) status_="播放失败：请检查格式、网络或选择较低的云端清晰度"; } }
            } catch(const std::exception& e) { busy_=false; status_=e.what(); }
            view_.scroll += (view_.targetScroll-view_.scroll)*.24f;
            if(std::abs(view_.targetScroll-view_.scroll)<.4f)view_.scroll=view_.targetScroll;
            hits_.clear();
            if(playing_) { rect(0,0,1280,720,{0,0,0,255}); drawPlayer(); }
            else {
                drawBackdrop();
                SDL_Rect clip{0,100,1280,620}; SDL_RenderSetClipRect(renderer_,&clip);
                switch(view_.screen) { case Screen::Connect:drawConnect();break;case Screen::Detail:drawDetail();break;case Screen::Settings:drawSettings();break;default:drawGrid();break; }
                SDL_RenderSetClipRect(renderer_,nullptr); drawHeader();
            }
            if(busy_) { rect(850,650,380,50,{23,32,28,230}); text("正在加载… B 取消",870,662,20); }
            if(!status_.empty()) { rect(40,642,1200,40,{23,32,28,240}); text(status_,56,650,20,{214,230,165,255},1160); }
#ifndef __SWITCH__
            previewFrame();
#endif
            SDL_RenderPresent(renderer_);
            if(playing_)player_->presented();
        if(playing_) SDL_Delay(1); else SDL_Delay(16);
        }
        stopPlayer();
    }
};
}
int main(int, char**) {
    if(R_FAILED(socketInitializeDefault())) return 1;
    if(R_FAILED(plInitialize(PlServiceType_User))) { socketExit(); return 1; }
    curl_global_init(CURL_GLOBAL_DEFAULT);
    SDL_SetHint(SDL_HINT_TOUCH_MOUSE_EVENTS,"0");
#ifdef __SWITCH__
    SDL_SetHint(SDL_HINT_RENDER_DRIVER,"opengles2");
#else
    SDL_SetHint(SDL_HINT_RENDER_DRIVER,"opengl");
#endif
    SDL_SetHint(SDL_HINT_RENDER_SCALE_QUALITY,"1");
    if(SDL_Init(SDL_INIT_VIDEO|SDL_INIT_AUDIO|SDL_INIT_GAMECONTROLLER)<0) return 1;
    TTF_Init(); IMG_Init(IMG_INIT_JPG|IMG_INIT_PNG);
    SDL_Window* window=SDL_CreateWindow("SunnyTV",0,0,1280,720,SDL_WINDOW_OPENGL|SDL_WINDOW_FULLSCREEN);
    SDL_Renderer* renderer=window?SDL_CreateRenderer(window,-1,SDL_RENDERER_ACCELERATED|SDL_RENDERER_PRESENTVSYNC):nullptr;
    int result=0;
    try { if(!renderer) throw std::runtime_error("无法启动图形界面"); SDL_RenderSetLogicalSize(renderer,1280,720); SDL_SetRenderDrawBlendMode(renderer,SDL_BLENDMODE_BLEND); { App app(renderer); app.run(); } }
    catch(const std::exception& e) { consoleInit(nullptr); std::printf("SunnyTV Switch\n%s\n\nPress + to exit.\n",e.what()); PadState pad; padConfigureInput(1,HidNpadStyleSet_NpadStandard); padInitializeDefault(&pad); while(appletMainLoop()) { padUpdate(&pad);
        if(padGetButtonsDown(&pad)&HidNpadButton_Plus) break; consoleUpdate(nullptr); } consoleExit(nullptr); result=1; }
    if(renderer) SDL_DestroyRenderer(renderer);
        if(window) SDL_DestroyWindow(window); IMG_Quit(); TTF_Quit(); SDL_Quit(); curl_global_cleanup(); plExit(); socketExit(); return result;
}
