#include "sunny/ui.hpp"
#include <algorithm>
#include <cmath>
#include <vector>

namespace sunny {
namespace {
SDL_Color rgb(unsigned value) { return {Uint8(value >> 16), Uint8(value >> 8), Uint8(value), 255}; }
SDL_Color mix(SDL_Color a, SDL_Color b, float t) { return {Uint8(a.r+(b.r-a.r)*t),Uint8(a.g+(b.g-a.g)*t),Uint8(a.b+(b.b-a.b)*t),Uint8(a.a+(b.a-a.a)*t)}; }
const unsigned swatches[10][2] = {{0x493D2E,0xF1EBDD},{0x403B2D,0xD8C89B},{0x29483D,0xBFD8C9},{0x284744,0xADCBC5},{0x30495F,0xC6D5E3},{0x4C3C5B,0xD7CAE0},{0x603B40,0xE0BEC1},{0x4F4033,0xC9B59F},{0x435A70,0xF2EEE4},{0x61776A,0xF3EFE2}};
void mesh(SDL_Renderer* renderer, SDL_Texture* texture, Bounds b, float radius, SDL_Color color, SDL_FRect uv = {0,0,1,1}) {
    radius = std::clamp(radius,0.f,std::min(b.w,b.h)/2);
    if (b.w <= 0 || b.h <= 0) return;
    std::vector<SDL_Vertex> vertices;
    vertices.push_back({{b.x+b.w/2,b.y+b.h/2},color,{uv.x+uv.w/2,uv.y+uv.h/2}});
    const float centers[4][2] = {{b.x+b.w-radius,b.y+radius},{b.x+b.w-radius,b.y+b.h-radius},{b.x+radius,b.y+b.h-radius},{b.x+radius,b.y+radius}};
    for (int corner=0;corner<4;++corner) for (int step=0;step<=8;++step) {
        float angle=(-90.f+corner*90.f+step*90.f/8)*3.14159265f/180.f;
        float x=centers[corner][0]+std::cos(angle)*radius, y=centers[corner][1]+std::sin(angle)*radius;
        vertices.push_back({{x,y},color,{uv.x+(x-b.x)/b.w*uv.w,uv.y+(y-b.y)/b.h*uv.h}});
    }
    std::vector<int> indices;
    for (int i=1;i<int(vertices.size());++i) { indices.push_back(0); indices.push_back(i); indices.push_back(i+1<int(vertices.size())?i+1:1); }
    SDL_RenderGeometry(renderer,texture,vertices.data(),int(vertices.size()),indices.data(),int(indices.size()));
}
}
Palette sunnyPalette(bool dark, int accent) {
    auto ink=rgb(swatches[std::clamp(accent,0,9)][0]), light=rgb(swatches[std::clamp(accent,0,9)][1]);
    if (dark) return {rgb(0x08090B),rgb(0x181A1D),rgb(0x2A2D32),light,ink,rgb(0xF5F5F2),rgb(0xB9BDBE),rgb(0x53585C)};
    return {mix(light,rgb(0xFFFFFF),.90f),mix(light,rgb(0xFFFFFF),.82f),mix(light,rgb(0xFFFFFF),.70f),ink,light,rgb(0x17191C),rgb(0x535A5D),rgb(0x9A9F9C)};
}
Canvas::Canvas(SDL_Renderer* r,const void* data,size_t length):renderer_(r),fontData_(data),fontSize_(length) {}
Canvas::~Canvas() { for(auto& [k,t]:texts_) SDL_DestroyTexture(t); for(auto& [k,f]:fonts_) TTF_CloseFont(f); }
TTF_Font* Canvas::font(int size) { auto it=fonts_.find(size); if(it!=fonts_.end()) return it->second; auto* f=TTF_OpenFontRW(SDL_RWFromConstMem(fontData_,int(fontSize_)),1,size); if(f) fonts_[size]=f; return f; }
void Canvas::box(Bounds b,SDL_Color c) { SDL_SetRenderDrawColor(renderer_,c.r,c.g,c.b,c.a); SDL_FRect r{b.x,b.y,b.w,b.h}; SDL_RenderFillRectF(renderer_,&r); }
void Canvas::round(Bounds b,float radius,SDL_Color c) { mesh(renderer_,nullptr,b,radius,c); }
void Canvas::outline(Bounds b,float radius,SDL_Color c,int thickness) {
    for(int t=0;t<thickness;++t) {
        SDL_SetRenderDrawColor(renderer_,c.r,c.g,c.b,c.a);
        float x=b.x+t,y=b.y+t,w=b.w-2*t,h=b.h-2*t,r=std::max(0.f,radius-t);
        SDL_FPoint points[37]; int count=0;
        const float centers[4][2]={{x+w-r,y+r},{x+w-r,y+h-r},{x+r,y+h-r},{x+r,y+r}};
        for(int corner=0;corner<4;++corner) for(int i=0;i<=8;++i) { float a=(-90+corner*90+i*90.f/8)*3.14159265f/180; points[count++]={centers[corner][0]+std::cos(a)*r,centers[corner][1]+std::sin(a)*r}; }
        points[count++]=points[0]; SDL_RenderDrawLinesF(renderer_,points,count);
    }
}
void Canvas::gradient(Bounds b,SDL_Color a,SDL_Color z,bool horizontal) {
    SDL_Vertex v[4]={{{b.x,b.y},a,{0,0}},{{b.x+b.w,b.y},horizontal?z:a,{0,0}},{{b.x+b.w,b.y+b.h},z,{0,0}},{{b.x,b.y+b.h},horizontal?a:z,{0,0}}};
    int indices[]={0,1,2,0,2,3}; SDL_RenderGeometry(renderer_,nullptr,v,4,indices,6);
}
void Canvas::image(SDL_Texture* texture,Bounds b,float radius,bool cover,SDL_Color tint) {
    if(!texture) return;
    int w=0,h=0; SDL_QueryTexture(texture,nullptr,nullptr,&w,&h); if(!w||!h) return;
    SDL_FRect uv{0,0,1,1};
    float scale=cover?std::max(b.w/w,b.h/h):std::min(b.w/w,b.h/h);
    if(cover) { uv.w=b.w/(w*scale); uv.h=b.h/(h*scale); uv.x=(1-uv.w)/2; uv.y=(1-uv.h)/2; }
    else { float actualW=w*scale,actualH=h*scale; b.x+=(b.w-actualW)/2; b.y+=(b.h-actualH)/2; b.w=actualW; b.h=actualH; }
    mesh(renderer_,texture,b,radius,tint,uv);
}
void Canvas::text(const std::string& s,float x,float y,int size,SDL_Color c,float maxWidth) {
    if(s.empty()||maxWidth<=0) return;
    std::string key=std::to_string(size)+":"+std::to_string(c.r)+":"+std::to_string(c.g)+":"+std::to_string(c.b)+":"+s;
    auto found=texts_.find(key); SDL_Texture* texture=found==texts_.end()?nullptr:found->second;
    if(!texture) { if(texts_.size()>320) { for(auto& [k,t]:texts_) SDL_DestroyTexture(t); texts_.clear(); } auto* f=font(size); if(!f) return; SDL_Surface* surface=TTF_RenderUTF8_Blended(f,s.c_str(),c); if(!surface) return; texture=SDL_CreateTextureFromSurface(renderer_,surface); SDL_FreeSurface(surface); if(!texture) return; texts_[key]=texture; }
    int w,h; SDL_QueryTexture(texture,nullptr,nullptr,&w,&h); SDL_Rect src{0,0,std::min(w,int(maxWidth)),h}; SDL_FRect dst{x,y,float(src.w),float(h)}; SDL_SetTextureAlphaMod(texture,c.a); SDL_RenderCopyF(renderer_,texture,&src,&dst);
}
void Canvas::wrap(const std::string& s,float x,float y,float width,int size,int maxLines,SDL_Color c) {
    std::string line; int lines=0; auto* f=font(size); if(!f) return;
    for(size_t p=0;p<s.size()&&lines<maxLines;) { auto byte=static_cast<unsigned char>(s[p]); size_t n=byte>=0xf0?4:byte>=0xe0?3:byte>=0xc0?2:1; auto next=s.substr(p,n); p+=n; int w=0; TTF_SizeUTF8(f,(line+next).c_str(),&w,nullptr); if(next=="\n"||w>width) { text(line,x,y+lines*(size+8),size,c,width); ++lines; line=next=="\n"?"":next; } else line+=next; }
    if(lines<maxLines) text(line,x,y+lines*(size+8),size,c,width);
}
void Canvas::icon(Icon icon,float x,float y,float size,SDL_Color c) {
    auto line=[&](float a,float b,float d,float e){SDL_SetRenderDrawColor(renderer_,c.r,c.g,c.b,c.a);for(int i=0;i<2;++i)SDL_RenderDrawLineF(renderer_,x+a*size+i,y+b*size,x+d*size+i,y+e*size);};
    auto circle=[&](float cx,float cy,float r){SDL_SetRenderDrawColor(renderer_,c.r,c.g,c.b,c.a);SDL_FPoint pts[33];for(int i=0;i<=32;++i){float a=i*6.2831853f/32;pts[i]={x+(cx+std::cos(a)*r)*size,y+(cy+std::sin(a)*r)*size};}SDL_RenderDrawLinesF(renderer_,pts,33);};
    switch(icon) {
    case Icon::Home: line(.12,.45,.5,.12);line(.5,.12,.88,.45);line(.23,.38,.23,.88);line(.23,.88,.42,.88);line(.42,.88,.42,.6);line(.42,.6,.62,.6);line(.62,.6,.62,.88);line(.62,.88,.78,.88);line(.78,.88,.78,.38);break;
    case Icon::Library: case Icon::Folder: for(int i=0;i<2;++i){float a=.12f+i*.27f;line(a,.18,a,.85);line(a,.18,a+.16,.18);line(a+.16,.18,a+.16,.85);line(a,.85,a+.16,.85);}line(.68,.18,.92,.85);line(.79,.16,.99,.81);break;
    case Icon::Search: circle(.43,.43,.29);line(.65,.65,.92,.92);break;
    case Icon::Settings: circle(.5,.5,.19);for(int i=0;i<8;++i){float a=i*6.2831853f/8;line(.5+std::cos(a)*.31,.5+std::sin(a)*.31,.5+std::cos(a)*.44,.5+std::sin(a)*.44);}circle(.5,.5,.34);break;
    case Icon::Play: line(.27,.14,.27,.86);line(.27,.14,.84,.5);line(.84,.5,.27,.86);break;
    case Icon::Pause: line(.32,.17,.32,.83);line(.68,.17,.68,.83);break;
    case Icon::Info: circle(.5,.5,.38);line(.5,.43,.5,.73);line(.5,.25,.5,.29);break;
    case Icon::Back: line(.7,.18,.3,.5);line(.3,.5,.7,.82);break;
    case Icon::Next: case Icon::Previous: {float sign=icon==Icon::Next?1:-1;line(.5-sign*.3,.2,.5+sign*.1,.5);line(.5+sign*.1,.5,.5-sign*.3,.8);line(.5,.2,.5+sign*.4,.5);line(.5+sign*.4,.5,.5,.8);break;}
    case Icon::Audio: line(.15,.38,.35,.38);line(.35,.38,.58,.18);line(.58,.18,.58,.82);line(.58,.82,.35,.62);line(.35,.62,.15,.62);line(.15,.62,.15,.38);line(.75,.3,.88,.5);line(.88,.5,.75,.7);break;
    case Icon::Subtitle: outline({x+.08f*size,y+.2f*size,.84f*size,.62f*size},size*.08,c);text("CC",x+.19f*size,y+.25f*size,int(size*.43f),c,size);break;
    case Icon::Sun: circle(.5,.5,.24);for(int i=0;i<8;++i){float a=i*6.2831853f/8;line(.5+std::cos(a)*.36,.5+std::sin(a)*.36,.5+std::cos(a)*.47,.5+std::sin(a)*.47);}line(.44,.36,.44,.64);line(.44,.36,.66,.5);line(.66,.5,.44,.64);break;
    case Icon::Refresh: circle(.5,.5,.32);line(.75,.15,.82,.42);line(.82,.42,.55,.4);break;
    case Icon::Grid: for(int row=0;row<2;++row)for(int col=0;col<2;++col)outline({x+(col*.42f+.12f)*size,y+(row*.42f+.12f)*size,size*.3f,size*.3f},size*.03,c);break;
    case Icon::Sort: line(.25,.15,.25,.85);line(.1,.3,.25,.15);line(.4,.3,.25,.15);line(.75,.15,.75,.85);line(.6,.7,.75,.85);line(.9,.7,.75,.85);break;
    }
}
}
