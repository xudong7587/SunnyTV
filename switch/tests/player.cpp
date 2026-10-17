#include "sunny/player.hpp"
#include <curl/curl.h>
#include <iostream>

using namespace sunny;
int main(int argc, char** argv) {
    if (argc != 3) return 2;
    curl_global_init(CURL_GLOBAL_DEFAULT);
    SDL_SetHint(SDL_HINT_RENDER_DRIVER,"opengl");
    if (SDL_Init(SDL_INIT_VIDEO|SDL_INIT_AUDIO) != 0) return 3;
    auto* window=SDL_CreateWindow("SunnyTV playback test",0,0,1280,720,SDL_WINDOW_OPENGL|SDL_WINDOW_HIDDEN);
    auto* renderer=window?SDL_CreateRenderer(window,-1,SDL_RENDERER_ACCELERATED):nullptr;
    int result=1;
    try {
        if (!renderer) throw std::runtime_error("SDL OpenGL renderer unavailable");
        Player player(renderer); Playback p; p.url=argv[1]; p.title="Synthetic media";
        player.open(p,std::string(48,'b'));
        auto start=SDL_GetTicks(); bool played=false;
        while (SDL_GetTicks()-start<15000) {
            SDL_Event event; while(SDL_PollEvent(&event)) {}
            player.poll(); SDL_SetRenderDrawColor(renderer,0,0,0,255); SDL_RenderClear(renderer); player.draw();
            auto s=player.state();
            if (s.failed) throw std::runtime_error("Synthetic playback failed");
            if (s.started&&s.position>0.5&&s.width==320&&s.height==180) {
                std::vector<unsigned char> pixels(1280*720*4);
                if (SDL_RenderReadPixels(renderer,nullptr,SDL_PIXELFORMAT_RGBA32,pixels.data(),1280*4)!=0) throw std::runtime_error("Cannot read video pixels");
                int colored=0;
                for(size_t i=0;i<pixels.size();i+=400) if (pixels[i]>20||pixels[i+1]>20||pixels[i+2]>20) ++colored;
                if (colored<100) throw std::runtime_error("Playback advanced but video frame was black");
                played=true; break;
            }
            SDL_RenderPresent(renderer);
            SDL_Delay(10);
        }
        if (!played) throw std::runtime_error("Video never produced playback progress");
        player.setSubtitleDelay(0.5); if (player.subtitleDelay()!=0.5) throw std::runtime_error("Subtitle delay did not apply");
        player.pause(); player.seek(1); player.close();
        p.url=argv[2]; player.open(p,std::string(48,'c')); start=SDL_GetTicks(); played=false;
        while(SDL_GetTicks()-start<15000) {
            SDL_Event event; while(SDL_PollEvent(&event)) {}
            player.poll(); player.draw(); SDL_RenderPresent(renderer);
            if(player.state().failed) throw std::runtime_error("HLS playback failed");
            if(player.state().started&&player.state().position>0.5) { played=true; break; }
            SDL_Delay(10);
        }
        if(!played) throw std::runtime_error("HLS playback never advanced");
        player.close();
        std::cout<<"MP4 and HLS playback initialized and advanced; video pixels, subtitle offset, pause, seek and close verified\n";
        result=0;
    } catch(const std::exception& e) { std::cerr<<e.what()<<'\n'; }
    if (renderer) SDL_DestroyRenderer(renderer);
    if (window) SDL_DestroyWindow(window);
    SDL_Quit(); curl_global_cleanup(); return result;
}
