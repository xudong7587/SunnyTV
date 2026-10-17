#pragma once
#include "touch.hpp"
#include <SDL2/SDL.h>
#include <SDL2/SDL_ttf.h>
#include <map>
#include <string>

namespace sunny {
struct Palette {
    SDL_Color background, surface, raised, accent, ink, text, secondary, border;
};
Palette sunnyPalette(bool dark, int accent);
enum class Icon { Home, Library, Search, Settings, Play, Pause, Info, Back, Next, Previous, Audio, Subtitle, Sun, Refresh, Grid, Sort, Folder };
class Canvas {
    SDL_Renderer* renderer_;
    const void* fontData_;
    size_t fontSize_;
    std::map<int, TTF_Font*> fonts_;
    std::map<std::string, SDL_Texture*> texts_;
public:
    Canvas(SDL_Renderer*, const void* font, size_t length);
    ~Canvas();
    TTF_Font* font(int size);
    void box(Bounds, SDL_Color);
    void round(Bounds, float radius, SDL_Color);
    void outline(Bounds, float radius, SDL_Color, int thickness = 2);
    void gradient(Bounds, SDL_Color a, SDL_Color b, bool horizontal = false);
    void image(SDL_Texture*, Bounds, float radius = 0, bool cover = true, SDL_Color tint = {255,255,255,255});
    void text(const std::string&, float x, float y, int size, SDL_Color, float maxWidth = 1240);
    void wrap(const std::string&, float x, float y, float width, int size, int lines, SDL_Color);
    void icon(Icon, float x, float y, float size, SDL_Color);
};
}
