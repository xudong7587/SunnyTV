#ifndef __SWITCH__
    void previewFrame() {
        const char* directory=std::getenv("SUNNYTV_UI_PREVIEW");if(!directory)return;
        static int stage=0;static uint32_t changedAt=SDL_GetTicks();
        if(busy_||SDL_GetTicks()-changedAt<950)return;
        auto shot=[&](const char* name){
            auto* surface=SDL_CreateRGBSurfaceWithFormat(0,1280,720,32,SDL_PIXELFORMAT_RGBA32);
            SDL_Rect area{0,0,1280,720};
            if(!surface||SDL_RenderReadPixels(renderer_,&area,SDL_PIXELFORMAT_RGBA32,surface->pixels,surface->pitch)!=0)throw std::runtime_error("UI screenshot failed");
            std::string path=std::string(directory)+"/"+name+".png";
            if(IMG_SavePNG(surface,path.c_str())!=0){SDL_FreeSurface(surface);throw std::runtime_error("UI screenshot save failed");}
            SDL_FreeSurface(surface);
            std::printf("UI stage %d: %s\n",stage,name);
        };
        auto finger=[&](Uint32 type,float x,float y){SDL_Event e{};e.type=type;e.tfinger.type=type;e.tfinger.fingerId=1;e.tfinger.x=x/1280;e.tfinger.y=y/720;SDL_PushEvent(&e);};
        auto tap=[&](float x,float y){finger(SDL_FINGERDOWN,x,y);finger(SDL_FINGERUP,x,y);};
        if(stage==0&&view_.screen==Screen::Home){shot("home");finger(SDL_FINGERDOWN,1000,500);finger(SDL_FINGERUP,850,500);}
        else if(stage==1&&view_.screen==Screen::Home&&view_.heroIndex==1){shot("home-rotated");tap(638,46);}
        else if(stage==2&&view_.screen==Screen::Libraries){shot("libraries");tap(160,300);}
        else if(stage==3&&view_.screen==Screen::Grid&&view_.hero){shot("library");finger(SDL_FINGERDOWN,1100,680);finger(SDL_FINGERMOTION,1100,40);finger(SDL_FINGERUP,1100,40);}
        else if(stage==4&&view_.screen==Screen::Grid&&view_.scroll>620){shot("grid");tap(130,350);}
        else if(stage==5&&view_.screen==Screen::Detail){shot("detail");tap(766,46);}
        else if(stage==6&&view_.screen==Screen::Settings){shot("settings");tap(160,322);}
        else if(stage==7&&view_.screen==Screen::Settings&&settingsCategory_==1){shot("appearance");tap(600,244);}
        else if(stage==8&&view_.screen==Screen::Settings&&!dark_){tap(574,46);}
        else if(stage==9&&view_.screen==Screen::Home&&!dark_){shot("light");std::puts("Touch navigation, library entry, scrolling, detail entry and theme toggle passed");run_=false;}
        else return;
        ++stage;changedAt=SDL_GetTicks();
    }
#endif
