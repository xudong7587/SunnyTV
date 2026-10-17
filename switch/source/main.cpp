#include "sunny/media.hpp"
#include "sunny/player.hpp"
#include "sunny/worker.hpp"
#include <switch.h>
#include <SDL2/SDL.h>
#include <SDL2/SDL_image.h>
#include <SDL2/SDL_ttf.h>
#include <curl/curl.h>
#include <algorithm>
#include <array>
#include <chrono>
#include <cstdio>
#include <cstring>
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
struct View { Screen screen = Screen::Home; std::string title, parent, search; Media container; Page page; int selected = 0, offset = 0; };
class App {
    SDL_Renderer* renderer_;
    TTF_Font* normal_ = nullptr;
    TTF_Font* small_ = nullptr;
    TTF_Font* large_ = nullptr;
    PadState pad_{};
    Http http_;
    Worker api_, images_, reports_;
    std::future<std::function<void()>> pending_;
    struct Image { SDL_Texture* texture = nullptr; bool requested = false; std::future<std::string> future; };
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
    uint32_t lastInput_ = 0, lastRepeat_ = 0, heldSince_ = 0, reportAt_ = 0;
    u64 lastHeld_ = 0;
    std::vector<CloudVariant> variants_;
    std::optional<CloudSession> cloud_;
    std::string cloudSource_;
    PlFontData fontData_{};
    std::map<std::string, SDL_Texture*> textCache_;
    void color(SDL_Color c) { SDL_SetRenderDrawColor(renderer_, c.r, c.g, c.b, c.a); }
    void rect(int x, int y, int w, int h, SDL_Color c) { color(c); SDL_Rect r{x,y,w,h}; SDL_RenderFillRect(renderer_, &r); }
    void text(const std::string& s, int x, int y, int size = 24, SDL_Color c = {243,245,239,255}, int maxWidth = 1200) {
        if (s.empty()) return;
        auto key = std::to_string(size) + ':' + std::to_string(c.r) + ':' + std::to_string(c.g) + ':' + std::to_string(c.b) + ':' + s;
        auto it = textCache_.find(key); SDL_Texture* t = it == textCache_.end() ? nullptr : it->second;
        if (!t) {
            if (textCache_.size() > 240) { for (auto& [k,v] : textCache_) SDL_DestroyTexture(v); textCache_.clear(); }
            auto font = size >= 32 ? large_ : size <= 20 ? small_ : normal_;
            SDL_Surface* surface = TTF_RenderUTF8_Blended(font, s.c_str(), c);
        if (!surface) return;
            t = SDL_CreateTextureFromSurface(renderer_, surface); SDL_FreeSurface(surface);
        if (!t) return; textCache_[key] = t;
        }
        int w, h; SDL_QueryTexture(t, nullptr, nullptr, &w, &h); SDL_Rect src{0,0,std::min(w, maxWidth),h}; SDL_Rect dst{x,y,src.w,h}; SDL_RenderCopy(renderer_, t, &src, &dst);
    }
    void wrapped(const std::string& s, int x, int y, int width, int maxLines) {
        // Wrap on UTF-8 codepoint boundaries, preserving CJK text and never splitting a byte sequence.
        std::string line; int lines = 0;
        for (size_t p = 0; p < s.size() && lines < maxLines;) { size_t n = 1; auto c = static_cast<unsigned char>(s[p]);
        if (c >= 0xf0) n = 4; else if (c >= 0xe0) n = 3; else if (c >= 0xc0) n = 2; auto next = s.substr(p, n); p += n; int w = 0; TTF_SizeUTF8(normal_, (line + next).c_str(), &w, nullptr);
        if (next == "\n" || w > width) { text(line, x, y + lines * 34, 24, {171,183,173,255}, width); ++lines; line = next == "\n" ? "" : next; } else line += next; }
        if (lines < maxLines) text(line, x, y + lines * 34, 24, {171,183,173,255}, width);
    }
    template <typename F> void task(F f) { if (busy_) return; api_.cancel = false; busy_ = true; pending_ = api_.submit([f=std::move(f)]() mutable -> std::function<void()> { return f(); }); }
    void save() {
        config_["base"] = base_; config_["username"] = username_; config_["device"] = device_; config_["ugreen"] = ugreen_; config_["remember"] = remember_;
        config_["userId"] = remember_ ? account_.userId : ""; config_["token"] = remember_ ? account_.token : ""; writeConfig(config_);
    }
    void clearImages() { for (auto& [key, image] : imageCache_) if (image.texture) SDL_DestroyTexture(image.texture); imageCache_.clear(); }
    void show(View next, bool push = true) { if (push) history_.push_back(view_); view_ = std::move(next); }
    void loadScreen(Screen screen, std::string parent = "", Media container = {}, int offset = 0, std::string search = "", bool push = true) {
        auto a = account_; task([=, this] {
            Emby source(http_, a, &api_.cancel); Page page; std::string title;
            if (screen == Screen::Home) { auto resume = source.resume(); auto latest = source.latest(); page = resume; std::set<std::string> seen; for (const auto& m : resume.items) seen.insert(m.id); for (auto& m : latest.items) if (seen.insert(m.id).second) page.items.push_back(std::move(m)); page.total = int64_t(page.items.size()); title = "继续观看 · 最新入库"; }
            else if (screen == Screen::Libraries) { page = source.views(); title = "我的媒体库"; }
            else { page = container.id.empty() ? source.list(parent, offset, search) : source.children(container, offset); title = search.empty() ? container.title : "搜索：" + search; }
            return [=, this]() mutable { View next; next.screen = screen; next.title = title; next.parent = parent; next.search = search; next.container = container; next.offset = offset; next.page = std::move(page); show(std::move(next), push); };
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
        for (auto& [key, image] : imageCache_) if (image.future.valid() && image.future.wait_for(std::chrono::seconds(0)) == std::future_status::ready) { try { auto bytes = image.future.get();
        if (!safeImage(bytes)) continue; SDL_RWops* rw = SDL_RWFromConstMem(bytes.data(), int(bytes.size())); SDL_Surface* surface = IMG_Load_RW(rw, 1);
        if (surface) { image.texture = SDL_CreateTextureFromSurface(renderer_, surface); SDL_FreeSurface(surface); } } catch (...) {} }
    }
    void artwork(const Media& m, int x, int y, int w, int h) {
        auto key = m.id + ':' + m.imageTag;
        if (!imageCache_.count(key) && imageCache_.size() < 96 && !account_.token.empty()) {
            Emby source(http_, account_); Request req; req.url = source.imageUrl(m, 360); req.scope = source.scope(); req.maxBytes = 4 * 1024 * 1024;
            Image image; image.requested = true; image.future = images_.submit([this, req] { auto r = http_.fetch(req, &images_.cancel); return r.status == 200 ? r.body : ""; }); imageCache_.emplace(key, std::move(image));
        }
        rect(x,y,w,h,{34,45,38,255}); auto it = imageCache_.find(key);
        if (it != imageCache_.end() && it->second.texture) { int iw, ih; SDL_QueryTexture(it->second.texture, nullptr, nullptr, &iw, &ih); float scale = std::min(float(w) / iw, float(h) / ih); SDL_Rect dst{x + (w - int(iw * scale)) / 2,y + (h - int(ih * scale)) / 2,int(iw * scale),int(ih * scale)}; SDL_RenderCopy(renderer_, it->second.texture, nullptr, &dst); }
    }
    void button(const std::string& label, int index, int selected, int x, int y, int width = 340) {
        bool focus = index == selected; rect(x,y,width,52,focus ? SDL_Color{214,230,165,255} : SDL_Color{23,32,28,255}); text(label,x + 16,y + 10,24,focus ? SDL_Color{9,13,13,255} : SDL_Color{243,245,239,255},width - 32);
    }
    void drawConnect() {
        text("连接你的媒体服务",60,116,36); text("SunnyTV Switch · 基于 Android 0.2.8",60,166,20,{171,183,173,255});
        std::vector<std::string> rows = {"服务器：" + (base_.empty() ? "填写 HTTP(S) 地址" : base_), "用户名：" + username_, "密码：" + std::string(password_.empty() ? "未填写" : "••••••"), std::string("类型：") + (ugreen_ ? "绿联影视中心" : "Emby"), std::string(remember_ ? "☑" : "□") + " 记住登录（SD 卡保存访问令牌）", "连接", "HTTP / STRM 直接播放"};
        for (size_t i = 0; i < rows.size(); ++i) button(rows[i], int(i), view_.selected, 60,220 + int(i) * 60,1100);
    }
    void drawGrid() {
        text(view_.title,60,104,32); int startRow = std::max(0, view_.selected / 6 - 1);
        for (int row = startRow; row < startRow + 2; ++row) for (int col = 0; col < 6; ++col) {
            int i = row * 6 + col;
        if (i >= int(view_.page.items.size())) continue; auto& m = view_.page.items[i]; int x = 60 + col * 194, y = 158 + (row - startRow) * 248; bool focused = i == view_.selected;
            if (focused) rect(x-4,y-4,176,214,{214,230,165,255}); artwork(m,x,y,168,206); text(m.title,x,y+212,20,{243,245,239,255},176);
            if (m.positionMs > 0 && m.durationMs > 0) rect(x,y+202,int(168 * std::clamp(double(m.positionMs) / m.durationMs, 0.0, 1.0)),4,{214,230,165,255});
        }
        if (view_.page.items.empty()) text("这里还没有可显示的媒体",60,210);
        text(std::to_string(view_.offset + (view_.page.items.empty() ? 0 : view_.selected + 1)) + " / " + std::to_string(view_.page.total),1100,112,20);
    }
    void drawDetail() {
        artwork(detail_,60,150,300,430); text(detail_.title,400,130,36,{243,245,239,255},810); text(detail_.year ? std::to_string(detail_.year) : detail_.type,400,184,20,{171,183,173,255}); wrapped(detail_.overview,400,230,780,7);
        button(detail_.positionMs > 0 ? "继续播放 · " + clockTime(double(detail_.positionMs) / 1000) : "播放",0,view_.selected,400,518,390); button("从头播放",1,view_.selected,400,582,390);
    }
    void drawSettings() {
        text("设置",60,116,36);
        std::vector<std::string> rows = {"重新连接 / 切换服务", "HTTP / STRM 直接播放", "清除本机保存的登录信息", "返回首页"};
        for (size_t i = 0; i < rows.size(); ++i) button(rows[i],int(i),view_.selected,60,210+int(i)*70,900);
        text("SunnyTV Switch 0.2.8 · 初始移植版",60,550,24); text("A 确认 · B 返回 · L/R 切换栏目 · − 退出应用",60,600,20,{171,183,173,255});
    }
    void drawPlayer() {
        auto& s = player_->state(); player_->draw();
        bool osd = menu_ || SDL_GetTicks() - lastInput_ < 4000 || s.paused || !s.loaded;
        if (!s.loaded) text("正在连接并准备播放…",60,60);
        if (showInfo_) { rect(40,30,560,105,{0,0,0,140}); text(std::to_string(s.width) + "×" + std::to_string(s.height) + " · " + s.videoCodec + " / " + s.audioCodec,56,42,20); text("解码：" + (s.decoder.empty() ? std::string("未知") : s.decoder),56,76,20); }
        if (osd) { rect(40,582,1200,108,{0,0,0,140}); text(player_->playback().title,60,594,24,{243,245,239,255},1140); text(clockTime(s.position) + " / " + clockTime(s.duration),60,630,20); text("X 暂停 · L/R ±10秒 · ＋ 菜单 · B 返回",550,630,20);
        if (s.duration > 0) rect(60,676,int(1140 * std::clamp(s.position / s.duration,0.0,1.0)),4,{214,230,165,255}); }
        if (menu_) {
            rect(340,80,620,480,{0,0,0,128});
            if (!variants_.empty()) { text("云端清晰度",370,98,32); for (size_t i=size_t(menuIndex_/6)*6;i<variants_.size() && i<size_t(menuIndex_/6)*6+6;++i) button(variants_[i].label(),int(i),menuIndex_,370,150+int(i%6)*60,560); }
            else { std::vector<std::string> labels = {"切换音轨", "切换字幕 / 关闭字幕", "字幕调整：−10  −3  −0.5  归零  +0.5  +3  +10", "115 云端清晰度", std::string("播放信息：") + (showInfo_ ? "开" : "关")}; text("播放菜单",370,98,32); for (size_t i=0;i<labels.size();++i) button(labels[i],int(i),menuIndex_,370,150+int(i)*68,560);
        if(menuIndex_==2) text("左右选择，A 应用：" + std::to_string(std::array<double,7>{-10,-3,-0.5,0,0.5,3,10}[subtitleStep_]) + " 秒 · 当前 " + std::to_string(player_->subtitleDelay()) + " 秒",370,504,20,{214,230,165,255},560); }
        }
    }
    void nav(Screen screen) { history_.clear();
        if (screen == Screen::Settings) { View v; v.screen=screen; show(v,false); } else loadScreen(screen,"",{},0,"",false); }
    void input(u64 down) {
        lastInput_=SDL_GetTicks(); status_.clear();
        if (busy_) { if (down & HidNpadButton_B) api_.cancel=true; return; }
        if (playing_) {
            if (menu_) {
                if (down & HidNpadButton_B) { if (!variants_.empty()) variants_.clear(); else menu_=false; return; }
                int count=variants_.empty()?5:int(variants_.size());
                if (down & HidNpadButton_Up) menuIndex_=std::max(0,menuIndex_-1);
        if(down & HidNpadButton_Down) menuIndex_=std::min(count-1,menuIndex_+1);
                if (variants_.empty() && menuIndex_==2) { if(down & HidNpadButton_Left) subtitleStep_=std::max(0,subtitleStep_-1);
        if(down & HidNpadButton_Right) subtitleStep_=std::min(6,subtitleStep_+1); }
                if(down & HidNpadButton_A) { if(!variants_.empty()) chooseCloud(variants_[menuIndex_]); else switch(menuIndex_) { case 0: player_->cycleAudio(); break; case 1: player_->cycleSubtitle(); break; case 2: if(subtitleStep_==3) player_->setSubtitleDelay(0); else player_->subtitleDelay(std::array<double,7>{-10,-3,-0.5,0,0.5,3,10}[subtitleStep_]); break; case 3: cloudMenu(); break; case 4: showInfo_=!showInfo_; break; } }
            } else { if(down & HidNpadButton_B) stopPlayer(); else if(down & HidNpadButton_Plus) { menu_=true; menuIndex_=0; } else if(down & (HidNpadButton_X|HidNpadButton_A)) player_->pause(); else if(down & (HidNpadButton_L|HidNpadButton_Left)) player_->seek(-10); else if(down & (HidNpadButton_R|HidNpadButton_Right)) player_->seek(10); }
            return;
        }
        if (down & HidNpadButton_Minus) { run_=false; return; }
        if (down & HidNpadButton_B) { if(!history_.empty()) { view_=std::move(history_.back()); history_.pop_back(); } else if(!account_.token.empty()) nav(Screen::Home); return; }
        if (view_.screen==Screen::Connect) {
            if(down & HidNpadButton_Up) view_.selected=std::max(0,view_.selected-1);
        if(down & HidNpadButton_Down) view_.selected=std::min(6,view_.selected+1);
            if(down & HidNpadButton_A) switch(view_.selected) { case 0: base_=keyboard("媒体服务器 HTTP(S) 地址",base_); break; case 1: username_=keyboard("用户名",username_); break; case 2: password_=keyboard("密码",password_,true); break; case 3: ugreen_=!ugreen_; break; case 4: remember_=!remember_; break; case 5: login(); break; case 6: direct(); break; }
            return;
        }
        if(down & HidNpadButton_Y) { auto query=keyboard("搜索媒体");
        if(!query.empty()) loadScreen(Screen::Grid,"",{},0,query); return; }
        if(down & (HidNpadButton_L|HidNpadButton_R)) { std::array<Screen,3> tabs{Screen::Home,Screen::Libraries,Screen::Settings}; int index=view_.screen==Screen::Home?0:view_.screen==Screen::Settings?2:1; index=(index+((down & HidNpadButton_R)?1:2))%3; nav(tabs[index]); return; }
        if(view_.screen==Screen::Detail) { if(down & (HidNpadButton_Up|HidNpadButton_Down)) view_.selected=1-view_.selected;
        if(down & HidNpadButton_A) startMedia(view_.selected==1); return; }
        if(view_.screen==Screen::Settings) {
            if(down & HidNpadButton_Up) view_.selected=std::max(0,view_.selected-1);
        if(down & HidNpadButton_Down) view_.selected=std::min(3,view_.selected+1);
            if(down & HidNpadButton_A) switch(view_.selected) { case 0: { View v; v.screen=Screen::Connect; show(v); break; } case 1: direct(); break; case 2: remember_=false; account_.token.clear(); account_.userId.clear(); save(); clearImages(); history_.clear(); view_={}; view_.screen=Screen::Connect; break; case 3: nav(Screen::Home); break; }
            return;
        }
        int count=int(view_.page.items.size());
        if(!count) return;
        if(down & HidNpadButton_Left) view_.selected=std::max(0,view_.selected-1);
        if(down & HidNpadButton_Right) view_.selected=std::min(count-1,view_.selected+1);
        if(down & HidNpadButton_Up) view_.selected=std::max(0,view_.selected-6);
        if(down & HidNpadButton_Down) view_.selected=std::min(count-1,view_.selected+6);
        if(down & HidNpadButton_A) selectMedia(view_.page.items[view_.selected]);
        if(down & HidNpadButton_ZR) { if(view_.screen==Screen::Grid && view_.offset+count<view_.page.total) loadScreen(Screen::Grid,view_.parent,view_.container,view_.offset+count,view_.search,false); }
        if(down & HidNpadButton_ZL) { if(view_.screen==Screen::Grid && view_.offset>0) loadScreen(Screen::Grid,view_.parent,view_.container,std::max(0,view_.offset-60),view_.search,false); }
    }
public:
    explicit App(SDL_Renderer* renderer) : renderer_(renderer) {
        mkdir(Root,0777); mkdir((std::string(Root)+"/fonts").c_str(),0777);
        if(R_FAILED(plGetSharedFontByType(&fontData_,PlSharedFontType_ChineseSimplified))) throw std::runtime_error("无法读取系统中文字体");
        auto openFont=[&](int size) { return TTF_OpenFontRW(SDL_RWFromConstMem(fontData_.address,int(fontData_.size)),1,size); };
        normal_=openFont(24); small_=openFont(20); large_=openFont(36);
        if(!normal_||!small_||!large_) throw std::runtime_error("字体初始化失败");
        // A device-local copy lets libass discover the system font. It is never part of the NRO.
        auto fontPath=std::string(Root)+"/fonts/system-cjk.ttf"; std::ifstream existing(fontPath,std::ios::binary);
        if(!existing) { std::ofstream out(fontPath,std::ios::binary); out.write(static_cast<const char*>(fontData_.address),fontData_.size); }
        try { config_=readConfig(); } catch(const std::exception& e) { status_=e.what(); }
        base_=config_["base"].text(); username_=config_["username"].text(); ugreen_=config_["ugreen"].boolean(); remember_=config_["remember"].boolean(); device_=config_["device"].text();
        if(device_.empty()) device_=randomHex();
        if(remember_) account_={base_,config_["userId"].text(),config_["token"].text(),device_,ugreen_};
        padConfigureInput(1,HidNpadStyleSet_NpadStandard); padInitializeDefault(&pad_);
        if(account_.token.empty()) view_.screen=Screen::Connect; else loadScreen(Screen::Home,"",{},0,"",false);
    }
    ~App() {
        stopPlayer(); api_.shutdown(); images_.shutdown(); reports_.shutdown();
        player_.reset(); clearImages(); for(auto& [k,v]:textCache_) SDL_DestroyTexture(v);
        if(normal_) TTF_CloseFont(normal_);
        if(small_) TTF_CloseFont(small_);
        if(large_) TTF_CloseFont(large_);
    }
    void run() {
        while(run_ && appletMainLoop()) {
            SDL_Event event; while(SDL_PollEvent(&event)) if(event.type==SDL_QUIT) run_=false;
            padUpdate(&pad_); auto down=padGetButtonsDown(&pad_), held=padGetButtons(&pad_); u64 directions=held & (HidNpadButton_Up|HidNpadButton_Down|HidNpadButton_Left|HidNpadButton_Right); uint32_t now=SDL_GetTicks();
            if(directions!=lastHeld_) { heldSince_=now; lastRepeat_=now; lastHeld_=directions; } else if(directions && now-heldSince_>350 && now-lastRepeat_>120) { down|=directions; lastRepeat_=now; }
            try {
                if(pending_.valid() && pending_.wait_for(std::chrono::seconds(0))==std::future_status::ready) { busy_=false; auto apply=pending_.get(); apply(); }
                pollImages();
        if(down) input(down);
                if(playing_) { player_->poll(); auto s=player_->state();
        if(s.started&&!reported_) { report("Playing"); reported_=true; reportAt_=now; } if(reported_&&now-reportAt_>10000) { report("Playing/Progress"); reportAt_=now; } if(s.ended) { bool failed=s.failed; stopPlayer();
        if(failed) status_="播放失败：请检查格式、网络或选择较低的云端清晰度"; } }
            } catch(const std::exception& e) { busy_=false; status_=e.what(); }
            rect(0,0,1280,720,{9,13,13,255});
            if(playing_) drawPlayer(); else { text("SunnyTV",60,28,36,{214,230,165,255}); text("L/R 首页 · 媒体库 · 设置       Y 搜索",650,42,20,{171,183,173,255}); switch(view_.screen) { case Screen::Connect: drawConnect(); break; case Screen::Detail: drawDetail(); break; case Screen::Settings: drawSettings(); break; default: drawGrid(); break; } text("A 确认 · B 返回 · ZL/ZR 翻页 · − 退出",60,680,20,{171,183,173,255}); }
            if(busy_) { rect(850,650,380,50,{23,32,28,230}); text("正在加载… B 取消",870,662,20); }
            if(!status_.empty()) { rect(40,642,1200,40,{23,32,28,240}); text(status_,56,650,20,{214,230,165,255},1160); }
            SDL_RenderPresent(renderer_);
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
    SDL_SetHint(SDL_HINT_RENDER_DRIVER,"opengl"); SDL_SetHint(SDL_HINT_RENDER_SCALE_QUALITY,"1");
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
