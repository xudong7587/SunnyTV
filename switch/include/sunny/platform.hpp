#pragma once
#ifdef __SWITCH__
#include <switch.h>
#else
// Desktop adapter for exercising the same SDL screens and touch handlers in CI. This branch is
// excluded from the NRO; it never emulates Switch playback or device performance.
#include <SDL2/SDL.h>
#include <cstdint>
#include <fstream>
#include <random>
#include <string>
#include <vector>
using u64=uint64_t;
using Result=int;
#define R_FAILED(result) ((result)<0)
#define R_SUCCEEDED(result) ((result)>=0)
enum : u64 { HidNpadButton_A=1ULL<<0,HidNpadButton_B=1ULL<<1,HidNpadButton_X=1ULL<<2,HidNpadButton_Y=1ULL<<3,HidNpadButton_L=1ULL<<4,HidNpadButton_R=1ULL<<5,HidNpadButton_ZL=1ULL<<6,HidNpadButton_ZR=1ULL<<7,HidNpadButton_Plus=1ULL<<8,HidNpadButton_Minus=1ULL<<9,HidNpadButton_Left=1ULL<<10,HidNpadButton_Right=1ULL<<11,HidNpadButton_Up=1ULL<<12,HidNpadButton_Down=1ULL<<13 };
constexpr int HidNpadStyleSet_NpadStandard=0,PlSharedFontType_ChineseSimplified=0,PlServiceType_User=0;
struct PadState { u64 held=0,previous=0; };
struct HidAnalogStickState { int x=0,y=0; };
struct PlFontData { void* address=nullptr; uint32_t size=0; };
struct SwkbdConfig {};
inline bool desktopConsole=false;
inline void randomGet(void* output,size_t count) { std::random_device random;auto* p=static_cast<unsigned char*>(output);for(size_t i=0;i<count;++i)p[i]=static_cast<unsigned char>(random()); }
inline int socketInitializeDefault(){return 0;} inline void socketExit(){} inline int plInitialize(int){return 0;} inline void plExit(){}
inline int plGetSharedFontByType(PlFontData* out,int) {
    static std::vector<char> data;
    if(data.empty()){std::ifstream in("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",std::ios::binary);data.assign(std::istreambuf_iterator<char>(in),{});}
    if(data.empty())return -1;out->address=data.data();out->size=uint32_t(data.size());return 0;
}
inline void padConfigureInput(int,int){} inline void padInitializeDefault(PadState*){}
inline void padUpdate(PadState* pad){pad->previous=pad->held;pad->held=0;auto* keys=SDL_GetKeyboardState(nullptr);const std::pair<SDL_Scancode,u64> map[]={{SDL_SCANCODE_RETURN,HidNpadButton_A},{SDL_SCANCODE_BACKSPACE,HidNpadButton_B},{SDL_SCANCODE_X,HidNpadButton_X},{SDL_SCANCODE_Y,HidNpadButton_Y},{SDL_SCANCODE_Q,HidNpadButton_L},{SDL_SCANCODE_E,HidNpadButton_R},{SDL_SCANCODE_PAGEUP,HidNpadButton_ZL},{SDL_SCANCODE_PAGEDOWN,HidNpadButton_ZR},{SDL_SCANCODE_SPACE,HidNpadButton_Plus},{SDL_SCANCODE_ESCAPE,HidNpadButton_Minus},{SDL_SCANCODE_LEFT,HidNpadButton_Left},{SDL_SCANCODE_RIGHT,HidNpadButton_Right},{SDL_SCANCODE_UP,HidNpadButton_Up},{SDL_SCANCODE_DOWN,HidNpadButton_Down}};for(auto [key,button]:map)if(keys[key])pad->held|=button;if(desktopConsole)pad->held|=HidNpadButton_Plus;}
inline u64 padGetButtonsDown(PadState* p){return p->held&~p->previous;} inline u64 padGetButtons(PadState* p){return p->held;}
inline HidAnalogStickState padGetStickPos(PadState*,int){return {};}
inline bool appletMainLoop(){return true;}
inline int swkbdCreate(SwkbdConfig*,int){return 0;} inline void swkbdClose(SwkbdConfig*){} inline void swkbdConfigMakePresetPassword(SwkbdConfig*){} inline void swkbdConfigMakePresetDefault(SwkbdConfig*){} inline void swkbdConfigSetHeaderText(SwkbdConfig*,const char*){} inline void swkbdConfigSetInitialText(SwkbdConfig*,const char*){} inline void swkbdConfigSetStringLenMax(SwkbdConfig*,int){} inline int swkbdShow(SwkbdConfig*,char*,size_t){return -1;}
inline void consoleInit(void*){desktopConsole=true;} inline void consoleUpdate(void*){} inline void consoleExit(void*){}
#endif
