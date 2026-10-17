    void searchMedia() {
        if (account_.token.empty()) { View v; v.screen=Screen::Connect; show(v); return; }
        auto query=keyboard("搜索媒体");
        if(!query.empty()) loadScreen(Screen::Grid,"",{},0,query);
    }
    void activateNavigation(int index) {
        navigationIndex_=index;
        if(index==2) { searchMedia(); return; }
        if(account_.token.empty() && index!=3) { View v; v.screen=Screen::Connect; show(v); return; }
        nav(index==0?Screen::Home:index==1?Screen::Libraries:Screen::Settings);
    }
    void drawHeader() {
        auto p=palette();
        canvas_->gradient({0,0,1280,112},dark_?SDL_Color{8,9,11,105}:SDL_Color{255,255,255,80},{0,0,0,0});
        canvas_->icon(Icon::Sun,48,24,44,{255,211,117,255});
        canvas_->text("SunnyTV",112,30,28,p.text);
        hit({40,20,255,64},[this](Point){activateNavigation(0);},true);
        int active=view_.screen==Screen::Home?0:view_.screen==Screen::Settings||view_.screen==Screen::Connect?3:view_.search.empty()?1:2;
        bool focused=view_.section==-1;
        float total=focused?316.f:264.f, left=670-total/2;
        auto shell=p.surface; shell.a=205;
        canvas_->round({left,12,total,68},34,shell);
        auto border=p.border; border.a=170;
        canvas_->outline({left,12,total,68},34,border,1);
        const Icon icons[]={Icon::Home,Icon::Library,Icon::Search,Icon::Settings};
        const char* names[]={"首页","媒体库","搜索","设置"};
        float x=left+5;
        for(int i=0;i<4;++i) {
            bool focus=focused&&navigationIndex_==i;
            float width=focus?110.f:62.f;
            auto fill=i==active||focus?p.ink:p.raised; fill.a=i==active||focus?240:170;
            canvas_->round({x,17,width,58},29,fill);
            if(focus) canvas_->outline({x,17,width,58},29,p.accent,2);
            canvas_->icon(icons[i],x+17,32,28,i==active||focus?p.accent:p.secondary);
            if(focus) canvas_->text(names[i],x+51,34,18,p.accent,width-54);
            hit({x,17,width,58},[this,i](Point){activateNavigation(i);},true);
            x+=width+2;
        }
        if(!history_.empty()){canvas_->round({1178,23,54,54},27,p.surface);canvas_->icon(Icon::Back,1193,38,24,p.text);hit({1178,23,54,54},[this](Point){input(HidNpadButton_B);},true);}
    }
    const Media* heroItem() const {
        if(view_.page.items.empty()) return nullptr;
        return &view_.page.items[size_t(std::clamp(view_.heroIndex,0,int(view_.page.items.size())-1))];
    }
    void drawBackdrop() {
        auto p=palette(); canvas_->box({0,0,1280,720},p.background);
        const Media* media=view_.screen==Screen::Detail?&detail_:heroItem();
        if(media && view_.screen!=Screen::Settings && view_.screen!=Screen::Connect) {
            canvas_->image(imageFor(*media,"Backdrop",1280),{0,0,1280,720},0,true);
            if(dark_) {
                canvas_->gradient({0,0,960,720},{8,9,11,150},{8,9,11,0},true);
                canvas_->gradient({0,0,1280,720},{8,9,11,18},{8,9,11,230});
            } else {
                canvas_->gradient({0,0,960,720},{245,247,243,190},{245,247,243,35},true);
                canvas_->gradient({0,0,1280,720},{245,247,243,35},{245,247,243,210});
            }
        } else canvas_->gradient({0,100,1280,620},p.background,p.surface);
        if(view_.scroll>420 && view_.screen!=Screen::Detail) {
            auto overlay=p.background; overlay.a=Uint8(std::min(210.f,(view_.scroll-420)*.45f)); canvas_->box({0,100,1280,620},overlay);
        }
    }
    void drawConnect() {
        auto p=palette();
        canvas_->text("连接你的媒体服务",64,128,32,p.text);
        canvas_->text("让自己的媒体库，回到大屏。",64,174,18,p.secondary);
        std::vector<std::string> rows={"服务器   "+(base_.empty()?"填写 HTTP(S) 地址":base_),"用户名   "+username_,"密码   "+std::string(password_.empty()?"点击填写":"••••••"),std::string("服务类型   ")+(ugreen_?"绿联影视中心":"Emby"),std::string(remember_?"已开启":"已关闭")+"   记住登录（SD 卡保存访问令牌）","连接媒体服务","HTTP / STRM 直接播放"};
        for(size_t i=0;i<rows.size();++i) {
            int y=222+int(i)*61; button(rows[i],int(i),view_.section==-1?-1:view_.selected,64,y,1100);
            hit({64,float(y),1100,52},[this,i](Point){view_.selected=int(i);input(HidNpadButton_A);});
        }
        contentHeight_=720;
    }
    void drawHero() {
        const Media* item=heroItem(); if(!item) return;
        auto p=palette(); float scroll=view_.scroll;
        bool home=view_.screen==Screen::Home;
        float titleY=(home?386.f:432.f)-scroll;
        if(!home) canvas_->text("媒体库 / "+view_.container.title,40,390-scroll,16,p.accent,470);
        auto* logo=imageFor(*item,"Logo",640);
        if(logo&&dark_) canvas_->image(logo,{40,titleY-12,450,92},0,false);
        else canvas_->text(item->title,40,titleY,48,p.text,468);
        std::string metadata=item->year?std::to_string(item->year):item->type;
        if(!item->subtitle.empty()) metadata+="  ·  "+item->subtitle;
        if(item->rating>0) {char score[24];std::snprintf(score,sizeof score,"    ★ %.1f",item->rating);metadata+=score;}
        canvas_->text(metadata,40,(home?470.f:515.f)-scroll,18,p.secondary,470);
        canvas_->wrap(item->overview.empty()?"来自你的媒体库":item->overview,40,(home?510.f:556.f)-scroll,465,18,home?2:1,p.text);
        if(home) {
            canvas_->text("向下查看更多媒体 ↓",40,570-scroll,16,p.secondary);
            hit({40,566-scroll,420,38},[this](Point){view_.section=2;view_.selected=0;scrollTo(640);});
            if(!view_.resume.items.empty()) canvas_->text("继续播放",40,598-scroll,16,p.secondary);
            for(size_t i=0;i<std::min<size_t>(2,view_.resume.items.size());++i) {
                const auto& recent=view_.resume.items[i]; float x=40+float(i)*238,y=626-scroll;
                canvas_->round({x,y,226,64},32,p.raised);
                if(view_.section==1&&view_.selected==int(i)) canvas_->outline({x,y,226,64},32,p.accent);
                canvas_->image(imageFor(recent),{x+10,y+10,44,44},22,true);
                canvas_->text(recent.title,x+65,y+11,17,p.text,149);
                auto remaining=std::max<int64_t>(0,recent.durationMs-recent.positionMs)/60000;
                canvas_->text(remaining?"剩余 "+std::to_string(remaining)+" 分钟":"继续播放",x+65,y+36,13,p.secondary,149);
                hit({x,y,226,64},[this,recent](Point){playItem(recent);});
            }
        } else {
            for(int i=0;i<2;++i) {
                Bounds bounds{40+float(i)*78,598-scroll,64,64}; bool focused=view_.section==1&&view_.selected==i;
                canvas_->round(bounds,32,focused?p.ink:p.raised);
                if(focused) canvas_->outline(bounds,32,p.accent);
                canvas_->icon(i?Icon::Info:Icon::Play,bounds.x+17,bounds.y+17,30,focused?p.accent:p.text);
                auto media=*item; hit(bounds,[this,i,media](Point){if(i)selectMedia(media);else playItem(media);});
            }
            canvas_->text("向下查看更多媒体 ↓",40,678-scroll,16,p.secondary);
            hit({40,671-scroll,430,36},[this](Point){view_.section=3;view_.selected=0;scrollTo(640);});
        }
        int count=int(view_.page.items.size()), first=std::max(0,view_.heroIndex-3);
        first=std::min(first,std::max(0,count-7)); float x=home?540.f:564.f, y=(home?336.f:350.f)-scroll;
        int visible=std::min(7,count-first); float big=home?346.f:346.f, gap=10;
        float narrow=visible>1?std::max(34.f,(1240-x-big-gap*(visible-1))/(visible-1)):48;
        for(int index=first;index<first+visible;++index) {
            const auto& candidate=view_.page.items[size_t(index)]; bool active=index==view_.heroIndex; float width=active?big:narrow;
            Bounds bounds{x,y,width,346};
            canvas_->round(bounds,22,p.surface); canvas_->image(imageFor(candidate,"Primary",480),bounds,22,true);
            if(active) {
                canvas_->gradient({x,y+246,width,100},{0,0,0,0},{0,0,0,170});
                canvas_->text(candidate.title,x+18,y+294,25,{245,245,242,255},width-36);
                if(view_.section==0) canvas_->outline(bounds,22,p.accent,2);
            }
            hit(bounds,[this,index,active](Point){if(active)selectMedia(view_.page.items[size_t(index)]);else{view_.heroIndex=index;view_.section=0;}});
            x+=width+gap;
        }
    }
    float postersTop() const { return view_.screen==Screen::Home?892.f+float(std::max<size_t>(1,(view_.libraries.items.size()+4)/5))*190.f:view_.hero?870.f:244.f; }
    void drawGrid() {
        auto p=palette(); bool home=view_.screen==Screen::Home, libraries=view_.screen==Screen::Libraries;
        if(hasHero()) drawHero();
        if(home) {
            float y=758-view_.scroll;
            canvas_->text("我的媒体库",40,y,27,p.text);
            for(size_t i=0;i<view_.libraries.items.size();++i) {
                const auto media=view_.libraries.items[i]; float x=40+float(i%5)*242, top=y+54+float(i/5)*190;
                if(top+176<100||top>720) continue;
                Bounds bounds{x,top,222,125}; artwork(media,bounds,16,false);
                if(view_.section==2&&view_.selected==int(i))canvas_->outline(bounds,16,p.accent);
                canvas_->text(media.title,x,top+136,18,p.text,222);
                hit({x,top,222,164},[this,media](Point){selectMedia(media);});
            }
        }
        float header=(home?postersTop()-56:view_.hero?770.f:142.f)-view_.scroll;
        canvas_->text(home?"最新入库":libraries?"我的媒体库":view_.title+" · 全部内容",40,header,27,p.text,800);
        canvas_->text(std::to_string(view_.page.total)+" 个条目",home?210:900,header+6,16,p.secondary,300);
        float top=postersTop(); int columns=libraries?3:6;
        float tileWidth=libraries?376.f:181.f, tileHeight=libraries?212.f:272.f, stride=libraries?406.f:206.f, rowHeight=libraries?275.f:346.f;
        if(libraries) top=220;
        for(size_t i=0;i<view_.page.items.size();++i) {
            const auto media=view_.page.items[i]; float y=top+float(i/columns)*rowHeight-view_.scroll,x=40+float(i%columns)*stride;
            if(y+rowHeight<100||y>720)continue;
            Bounds bounds{x,y,tileWidth,tileHeight}; artwork(media,bounds,16,libraries?false:true);
            if(view_.section==3&&view_.selected==int(i))canvas_->outline(bounds,16,p.accent,2);
            canvas_->text(media.title,x,y+tileHeight+12,20,p.text,tileWidth);
            std::string metadata=media.year?std::to_string(media.year):media.type;
            if(!media.subtitle.empty())metadata+=" · "+media.subtitle;
            canvas_->text(metadata,x,y+tileHeight+42,15,p.secondary,tileWidth);
            if(media.positionMs>0&&media.durationMs>0)canvas_->round({x+8,y+tileHeight-8,float((tileWidth-16)*std::clamp(double(media.positionMs)/media.durationMs,0.,1.)),3},1,p.accent);
            hit({x,y,tileWidth,tileHeight+68},[this,media](Point){selectMedia(media);});
        }
        if(view_.page.items.empty())canvas_->text("这里还没有可显示的媒体",40,header+80,22,p.secondary);
        contentHeight_=std::max(720.f,top+float((view_.page.items.size()+columns-1)/columns)*rowHeight+110);
        if(!home && view_.page.total>int64_t(view_.page.items.size())) {
            float y=contentHeight_-70-view_.scroll;
            for(int i=0;i<2;++i) { Bounds b{40+float(i)*190,y,174,46};canvas_->round(b,23,p.surface);canvas_->text(i?"下一页":"上一页",b.x+35,b.y+10,18,p.text);hit(b,[this,i](Point){input(i?HidNpadButton_ZR:HidNpadButton_ZL);}); }
        }
    }
    void drawDetail() {
        auto p=palette(); float y=250-view_.scroll;
        if(auto* logo=dark_?imageFor(detail_,"Logo",800):nullptr)canvas_->image(logo,{40,y-65,680,100},0,false);
        else canvas_->text(detail_.title,40,y,44,p.text,1000);
        std::string metadata=detail_.year?std::to_string(detail_.year):detail_.type;
        if(detail_.rating>0){char score[24];std::snprintf(score,sizeof score,"  ·  ★ %.1f",detail_.rating);metadata+=score;}
        if(detail_.durationMs>0)metadata+="  ·  "+std::to_string(detail_.durationMs/60000)+" 分钟";
        canvas_->text(metadata,40,y+64,19,p.text,1100);
        canvas_->text(detail_.subtitle,40,y+100,17,p.secondary,1100);
        for(int i=0;i<2;++i) {
            Bounds b{40+float(i)*226,y+155,210,60};bool focus=view_.section!=-1&&view_.selected==i;
            canvas_->round(b,30,focus?p.ink:p.raised);if(focus)canvas_->outline(b,30,p.accent);
            canvas_->icon(i?Icon::Refresh:Icon::Play,b.x+20,b.y+18,26,focus?p.accent:p.text);
            canvas_->text(i?"从头播放":detail_.positionMs>0?"继续播放":"播放",b.x+60,b.y+17,20,focus?p.accent:p.text,140);
            hit(b,[this,i](Point){startMedia(i==1);});
        }
        canvas_->text("剧情简介",40,y+260,25,p.text);
        canvas_->wrap(detail_.overview.empty()?"暂无简介":detail_.overview,40,y+310,1080,20,7,p.text);
        contentHeight_=std::max(720.f,y+view_.scroll+590);
    }
    std::vector<int> settingsRows() const { return settingsCategory_==0?std::vector<int>{0,1,4}:settingsCategory_==1?std::vector<int>{2,3}:std::vector<int>{5}; }
    void drawSettings() {
        auto p=palette();canvas_->text("设置",48,135,32,p.text);
        const char* categories[]={"连接与播放","外观","关于"};
        for(int i=0;i<3;++i){Bounds b{48,218.f+i*76,244,58};canvas_->round(b,29,i==settingsCategory_?p.ink:p.surface);if(settingsSidebar_&&i==settingsCategory_)canvas_->outline(b,29,p.accent);canvas_->text(categories[i],76,232.f+i*76,22,i==settingsCategory_?p.accent:p.secondary);hit(b,[this,i](Point){settingsCategory_=i;settingsSidebar_=false;view_.selected=settingsRows().front();});}
        std::vector<std::string> rows={"媒体服务    连接 / 切换账号","HTTP / STRM 直接播放",std::string("外观    ")+(dark_?"深色":"浅色"),"重点色    "+std::to_string(accent_+1)+" / 10","清除本机保存的登录信息","返回首页"};
        auto visible=settingsRows();
        for(size_t index=0;index<visible.size();++index){int i=visible[index],y=218+int(index)*66;button(rows[size_t(i)],i,view_.section==-1||settingsSidebar_?-1:view_.selected,332,y,874);hit({332,float(y),874,52},[this,i](Point){view_.selected=i;settingsSidebar_=false;input(HidNpadButton_A);});}
        if(settingsCategory_==2){canvas_->icon(Icon::Sun,340,352,54,{255,211,117,255});canvas_->text("SunnyTV",416,358,32,p.text);canvas_->text("让自己的媒体库，回到大屏。",340,438,22,p.text);canvas_->text("MediaIndex · STRM 与云端播放支持",340,490,18,p.secondary);}
        canvas_->text("SunnyTV Switch 0.2.9 · 基于 Android 0.2.8",336,644,16,p.secondary);contentHeight_=720;
    }
    void drawPlayer() {
        auto p=palette();auto& s=player_->state();player_->draw();
        bool osd=menu_||SDL_GetTicks()-lastInput_<4000||s.paused||!s.loaded;
        if(!s.loaded)canvas_->text("正在连接并准备播放…",40,110,22,{245,245,242,255});
        if(showInfo_){canvas_->round({38,90,540,92},12,{0,0,0,128});canvas_->text(std::to_string(s.width)+"×"+std::to_string(s.height)+" · "+s.videoCodec+" / "+s.audioCodec,54,104,16,{245,245,242,255},510);canvas_->text("解码："+(s.decoder.empty()?std::string("未知"):s.decoder),54,139,16,{245,245,242,255},510);}
        if(osd&&!menu_) {
            canvas_->gradient({0,0,1280,160},{0,0,0,170},{0,0,0,0});canvas_->gradient({0,470,1280,250},{0,0,0,0},{0,0,0,205});
            canvas_->round({30,25,52,52},26,{24,26,29,180});canvas_->icon(Icon::Back,44,39,24,{245,245,242,255});hit({30,25,52,52},[this](Point){stopPlayer();});
            canvas_->text(player_->playback().title,102,37,24,{245,245,242,255},1000);
            canvas_->round({600,306,80,80},40,{24,26,29,150});canvas_->icon(s.paused?Icon::Play:Icon::Pause,624,330,32,{245,245,242,255});hit({600,306,80,80},[this](Point){player_->pause();});
            canvas_->round({60,611,1160,4},2,{190,190,190,130});
            if(s.duration>0){float width=float(1160*std::clamp(s.position/s.duration,0.,1.));canvas_->round({60,611,width,4},2,p.accent);canvas_->round({54+width,607,12,12},6,p.accent);hit({50,590,1180,44},[this](Point point){auto state=player_->state();if(state.duration>0)player_->seek(std::clamp(double(point.x-60)/1160,0.,1.)*state.duration-state.position);});}
            canvas_->text(clockTime(s.position)+" / "+clockTime(s.duration),60,644,18,{245,245,242,255});
            const Icon icons[]={Icon::Previous,Icon::Audio,Icon::Subtitle,Icon::Settings,Icon::Next};
            for(int i=0;i<5;++i){Bounds b{490+float(i)*76,638,54,54};canvas_->round(b,27,{24,26,29,180});canvas_->icon(icons[i],b.x+14,b.y+14,26,{245,245,242,255});hit(b,[this,i](Point){if(i==0)player_->seek(-10);else if(i==4)player_->seek(10);else{menu_=true;menuIndex_=i==1?0:i==2?1:3;}});}
        }
        if(menu_) {
            canvas_->round({322,78,636,482},22,{0,0,0,128});canvas_->text(variants_.empty()?"播放菜单":"云端清晰度",354,100,28,{245,245,242,255});
            if(!variants_.empty()){int first=menuIndex_/6*6;for(int i=first;i<std::min(first+6,int(variants_.size()));++i){int y=152+(i-first)*60;button(variants_[i].label(),i,menuIndex_,354,y,570);hit({354,float(y),570,52},[this,i](Point){chooseCloud(variants_[size_t(i)]);});}}
            else {
                const std::string labels[]={"音轨","字幕 / 关闭字幕","字幕调整","115 云端清晰度",std::string("播放信息    ")+(showInfo_?"开":"关")};
                for(int i=0;i<5;++i){int y=154+i*66;button(labels[i],i,menuIndex_,354,y,570);hit({354,float(y),570,52},[this,i](Point){menuIndex_=i;input(HidNpadButton_A);});}
                if(menuIndex_==2){const double offsets[]={-10,-3,-.5,0,.5,3,10};for(int i=0;i<7;++i){Bounds b{354+float(i)*81,508,73,42};canvas_->round(b,21,i==subtitleStep_?p.ink:p.raised);canvas_->text(i==3?"归零":std::to_string(offsets[i]),b.x+8,b.y+10,14,p.accent,60);hit(b,[this,i](Point){subtitleStep_=i;if(i==3)player_->setSubtitleDelay(0);else player_->subtitleDelay(std::array<double,7>{-10,-3,-.5,0,.5,3,10}[size_t(i)]);});}}
            }
        }
    }
