#include "sunny/player.hpp"
#include <curl/curl.h>
#include <iostream>
#include <cstdlib>
#include <cstring>

using namespace sunny;
// Reproduce the real Horizon build's compiled-out scripting option while still exercising the
// complete renderer, gateway and decoder. Link wrapping affects only this regression harness.
extern "C" int __real_mpv_set_option_string(mpv_handle*, const char*, const char*);
static int missingScriptOptions = 0;
extern "C" int __wrap_mpv_set_option_string(mpv_handle* handle, const char* name, const char* value) {
    if (std::getenv("SUNNYTV_TEST_NO_SCRIPTS") && std::strcmp(name, "load-scripts") == 0) {
        ++missingScriptOptions;
        return MPV_ERROR_OPTION_NOT_FOUND;
    }
    return __real_mpv_set_option_string(handle, name, value);
}
int main(int argc, char** argv) {
    if (argc != 3) return 2;
    curl_global_init(CURL_GLOBAL_DEFAULT);
    const char* driver=std::getenv("SUNNYTV_TEST_RENDER_DRIVER");
    if(!driver)driver="opengl";
    SDL_SetHint(SDL_HINT_RENDER_DRIVER,driver);
    if (SDL_Init(SDL_INIT_VIDEO|SDL_INIT_AUDIO) != 0) return 3;
    auto* window=SDL_CreateWindow("SunnyTV playback test",0,0,1280,720,SDL_WINDOW_OPENGL|SDL_WINDOW_HIDDEN);
    auto* renderer=window?SDL_CreateRenderer(window,-1,SDL_RENDERER_ACCELERATED):nullptr;
    int result=1;
    try {
        if (!renderer) throw std::runtime_error("SDL OpenGL renderer unavailable");
        SDL_RendererInfo info{};SDL_GetRendererInfo(renderer,&info);
        if(!info.name||std::strcmp(driver,info.name)!=0)throw std::runtime_error("Requested GL backend was not exercised");
        SDL_RenderSetLogicalSize(renderer,1280,720);
        const auto uiContext=SDL_GL_GetCurrentContext();
        // Prime SDL's cached shader, blend, clip and texture state before mpv is created.
        SDL_SetRenderDrawBlendMode(renderer,SDL_BLENDMODE_BLEND);
        SDL_SetRenderDrawColor(renderer,12,34,56,255);SDL_Rect prime{10,10,100,100};
        SDL_RenderFillRect(renderer,&prime);SDL_RenderFlush(renderer);
        Player player(renderer); Playback p; p.url=argv[1]; p.title="Synthetic media";
        if(SDL_GL_GetCurrentContext()!=uiContext)throw std::runtime_error("Player constructor did not restore UI context");
        if (std::getenv("SUNNYTV_TEST_NO_SCRIPTS") && missingScriptOptions != 1)
            throw std::runtime_error("Compiled-out scripting regression was not exercised");
        player.open(p,std::string(48,'b'));
        auto start=SDL_GetTicks(); bool played=false;
        while (SDL_GetTicks()-start<15000) {
            SDL_Event event; while(SDL_PollEvent(&event)) {}
            player.poll(); SDL_SetRenderDrawColor(renderer,0,0,0,255); SDL_RenderClear(renderer); player.draw();
            if(SDL_GL_GetCurrentContext()!=uiContext)throw std::runtime_error("Video draw did not restore UI context");
            SDL_Rect overlay{20,20,90,40};SDL_SetRenderDrawColor(renderer,0,255,0,255);SDL_RenderFillRect(renderer,&overlay);
            auto s=player.state();
            if (s.failed) throw std::runtime_error("Synthetic playback failed");
            if (s.started&&s.position>0.5&&s.width==320&&s.height==180) {
                std::vector<unsigned char> pixels(1280*720*4);
                if (SDL_RenderReadPixels(renderer,nullptr,SDL_PIXELFORMAT_RGBA32,pixels.data(),1280*4)!=0) throw std::runtime_error("Cannot read video pixels");
                int colored=0;
                for(size_t i=0;i<pixels.size();i+=400) if (pixels[i]>20||pixels[i+1]>20||pixels[i+2]>20) ++colored;
                if (colored<100) throw std::runtime_error("Playback advanced but video frame was black");
                auto pixel=[&](int x,int y,int channel){return pixels[size_t((y*1280+x)*4+channel)];};
                if(pixel(40,40,1)<230||pixel(40,40,0)>20)throw std::runtime_error("SDL overlay corrupted after mpv draw");
                if(pixel(640,80,0)<180||pixel(640,80,2)>60||pixel(640,640,2)<180||pixel(640,640,0)>60)
                    throw std::runtime_error("Video orientation or red/blue channels are incorrect");
                played=true; break;
            }
            SDL_RenderPresent(renderer);
            player.presented();
            SDL_Delay(10);
        }
        if (!played) throw std::runtime_error("Video never produced playback progress");
        player.setSubtitleDelay(0.5); if (player.subtitleDelay()!=0.5) throw std::runtime_error("Subtitle delay did not apply");
        player.pause(); player.seek(1); player.close();
        p.url=argv[2]; player.open(p,std::string(48,'c')); start=SDL_GetTicks(); played=false;
        while(SDL_GetTicks()-start<15000) {
            SDL_Event event; while(SDL_PollEvent(&event)) {}
            player.poll(); player.draw(); SDL_RenderPresent(renderer);player.presented();
            if(player.state().failed) throw std::runtime_error("HLS playback failed");
            if(player.state().started&&player.state().position>0.5) { played=true; break; }
            SDL_Delay(10);
        }
        if(!played) throw std::runtime_error("HLS playback never advanced");
        player.close();
        std::cout<<driver<<": MP4/HLS progress, frame orientation/colors, SDL overlay/context, subtitle offset, pause, seek and close verified\n";
        result=0;
    } catch(const std::exception& e) { std::cerr<<e.what()<<'\n'; }
    if (renderer) SDL_DestroyRenderer(renderer);
    if (window) SDL_DestroyWindow(window);
    SDL_Quit(); curl_global_cleanup(); return result;
}
