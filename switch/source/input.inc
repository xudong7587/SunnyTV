    void movePosterFocus(u64 down) {
        int count=int(view_.page.items.size());
        int columns=view_.screen==Screen::Libraries?3:6;
        if(!count) return;
        if(down&HidNpadButton_Up) {
            if(view_.selected<columns) {
                if(view_.screen==Screen::Home&&!view_.libraries.items.empty()){view_.section=2;view_.selected=0;scrollTo(640);return;}
                if(view_.hero){view_.section=0;scrollTo(0);return;}
                view_.section=-1;return;
            }
            view_.selected=std::max(0,view_.selected-columns);
        }
        if(down&HidNpadButton_Down)view_.selected=std::min(count-1,view_.selected+columns);
        if(down&HidNpadButton_Left)view_.selected=std::max(0,view_.selected-1);
        if(down&HidNpadButton_Right)view_.selected=std::min(count-1,view_.selected+1);
        float height=columns==3?275.f:346.f;
        float y=(columns==3?220.f:postersTop())+float(view_.selected/columns)*height;
        if(y-view_.targetScroll<140)scrollTo(y-140);
        if(y+height-view_.targetScroll>700)scrollTo(y+height-700);
        if(down&HidNpadButton_A)selectMedia(view_.page.items[size_t(view_.selected)]);
    }
    void input(u64 down) {
        lastInput_=SDL_GetTicks();status_.clear();
        if(busy_){if(down&HidNpadButton_B)api_.cancel=true;return;}
        if(playing_) {
            if(menu_) {
                if(down&HidNpadButton_B){if(!variants_.empty())variants_.clear();else menu_=false;return;}
                int count=variants_.empty()?5:int(variants_.size());
                if(down&HidNpadButton_Up)menuIndex_=std::max(0,menuIndex_-1);
                if(down&HidNpadButton_Down)menuIndex_=std::min(count-1,menuIndex_+1);
                if(variants_.empty()&&menuIndex_==2){if(down&HidNpadButton_Left)subtitleStep_=std::max(0,subtitleStep_-1);if(down&HidNpadButton_Right)subtitleStep_=std::min(6,subtitleStep_+1);}
                if(down&HidNpadButton_A) {
                    if(!variants_.empty())chooseCloud(variants_[size_t(menuIndex_)]);
                    else switch(menuIndex_){case 0:player_->cycleAudio();break;case 1:player_->cycleSubtitle();break;case 2:if(subtitleStep_==3)player_->setSubtitleDelay(0);else player_->subtitleDelay(std::array<double,7>{-10,-3,-.5,0,.5,3,10}[size_t(subtitleStep_)]);break;case 3:cloudMenu();break;case 4:showInfo_=!showInfo_;break;}
                }
            } else if(down&HidNpadButton_B)stopPlayer();
            else if(down&HidNpadButton_Plus){menu_=true;menuIndex_=0;}
            else if(down&(HidNpadButton_X|HidNpadButton_A))player_->pause();
            else if(down&(HidNpadButton_L|HidNpadButton_Left))player_->seek(-10);
            else if(down&(HidNpadButton_R|HidNpadButton_Right))player_->seek(10);
            return;
        }
        if(down&HidNpadButton_Minus){run_=false;return;}
        if(down&HidNpadButton_B){if(!history_.empty()){view_=std::move(history_.back());history_.pop_back();}else if(hasHero()&&view_.targetScroll>100){scrollTo(0);view_.section=0;}else if(!account_.token.empty())nav(Screen::Home);return;}
        if(down&HidNpadButton_Y){searchMedia();return;}
        if(down&(HidNpadButton_L|HidNpadButton_R)) {
            int index=view_.screen==Screen::Home?0:view_.screen==Screen::Settings?2:1;
            index=(index+((down&HidNpadButton_R)?1:2))%3;
            activateNavigation(index==2?3:index);return;
        }
        if(view_.section==-1) {
            if(down&HidNpadButton_Left)navigationIndex_=std::max(0,navigationIndex_-1);
            if(down&HidNpadButton_Right)navigationIndex_=std::min(3,navigationIndex_+1);
            if(down&HidNpadButton_A)activateNavigation(navigationIndex_);
            if(down&HidNpadButton_Down){view_.section=hasHero()?(view_.screen==Screen::Home&&!view_.resume.items.empty()?1:0):view_.screen==Screen::Home?0:3;view_.selected=0;}
            return;
        }
        if(view_.screen==Screen::Connect) {
            if(down&HidNpadButton_Up){if(view_.selected==0){view_.section=-1;navigationIndex_=3;return;}--view_.selected;}
            if(down&HidNpadButton_Down)view_.selected=std::min(6,view_.selected+1);
            if(down&HidNpadButton_A)switch(view_.selected){case 0:base_=keyboard("媒体服务器 HTTP(S) 地址",base_);break;case 1:username_=keyboard("用户名",username_);break;case 2:password_=keyboard("密码",password_,true);break;case 3:ugreen_=!ugreen_;break;case 4:remember_=!remember_;break;case 5:login();break;case 6:direct();break;}
            return;
        }
        if(view_.screen==Screen::Settings) {
            if(settingsSidebar_){if(down&HidNpadButton_Up){if(settingsCategory_==0){view_.section=-1;navigationIndex_=3;return;}--settingsCategory_;}if(down&HidNpadButton_Down)settingsCategory_=std::min(2,settingsCategory_+1);if(down&(HidNpadButton_A|HidNpadButton_Right))settingsSidebar_=false;view_.selected=settingsRows().front();return;}
            if(down&HidNpadButton_Left){settingsSidebar_=true;return;}
            auto rows=settingsRows();auto it=std::find(rows.begin(),rows.end(),view_.selected);int index=it==rows.end()?0:int(it-rows.begin());
            if(down&HidNpadButton_Up){if(index==0){view_.section=-1;navigationIndex_=3;return;}--index;}
            if(down&HidNpadButton_Down)index=std::min(int(rows.size())-1,index+1);
            view_.selected=rows[size_t(index)];
            if(down&HidNpadButton_A)switch(view_.selected){case 0:{View v;v.screen=Screen::Connect;show(v);break;}case 1:direct();break;case 2:dark_=!dark_;save();break;case 3:accent_=(accent_+1)%10;save();break;case 4:remember_=false;account_.token.clear();account_.userId.clear();save();clearImages();history_.clear();view_={};view_.screen=Screen::Connect;break;case 5:nav(Screen::Home);break;}
            return;
        }
        if(view_.screen==Screen::Detail) {
            if(down&HidNpadButton_Up){view_.section=-1;navigationIndex_=1;scrollTo(0);}
            if(down&HidNpadButton_Left)view_.selected=0;
            if(down&HidNpadButton_Right)view_.selected=1;
            if(down&HidNpadButton_Down)scrollTo(view_.targetScroll+180);
            if(down&HidNpadButton_A)startMedia(view_.selected==1);
            return;
        }
        if(hasHero()&&view_.section==0) {
            if(down&HidNpadButton_Left){if(heroLeftTravel_>=heroCount()&&(view_.hero||!view_.resume.items.empty())){view_.section=1;view_.selected=0;heroLeftTravel_=0;}else rotateHero(-1);}
            if(down&HidNpadButton_Right)rotateHero(1);
            if(down&HidNpadButton_Up){view_.section=-1;navigationIndex_=view_.screen==Screen::Home?0:1;}
            if(down&HidNpadButton_Down){view_.selected=0;view_.section=view_.screen==Screen::Home&&!view_.libraries.items.empty()?2:3;scrollTo(640);heroLeftTravel_=0;}
            if((down&HidNpadButton_A)&&heroItem())selectMedia(*heroItem());
            return;
        }
        if(hasHero()&&view_.section==1) {
            int count=view_.screen==Screen::Home?int(std::min<size_t>(2,view_.resume.items.size())):2;
            if(down&HidNpadButton_Left)view_.selected=std::max(0,view_.selected-1);
            if(down&HidNpadButton_Right){if(view_.selected==count-1)view_.section=0;else view_.selected=std::min(count-1,view_.selected+1);}
            if(down&HidNpadButton_Up){view_.section=0;view_.selected=0;}
            if(down&HidNpadButton_Down){view_.section=view_.screen==Screen::Home&&!view_.libraries.items.empty()?2:3;view_.selected=0;scrollTo(640);}
            if(down&HidNpadButton_A){if(view_.screen==Screen::Home&&count)playItem(view_.resume.items[size_t(view_.selected)]);else if(heroItem()){if(view_.selected)selectMedia(*heroItem());else playItem(*heroItem());}}
            return;
        }
        if(view_.screen==Screen::Home&&view_.section==2) {
            int count=int(view_.libraries.items.size());
            if(down&HidNpadButton_Left)view_.selected=std::max(0,view_.selected-1);
            if(down&HidNpadButton_Right)view_.selected=std::min(std::max(0,count-1),view_.selected+1);
            if(down&HidNpadButton_Up){view_.section=view_.resume.items.empty()?0:1;view_.selected=0;scrollTo(0);}
            if(down&HidNpadButton_Down){view_.section=3;view_.selected=0;scrollTo(postersTop()-140);}
            if((down&HidNpadButton_A)&&count)selectMedia(view_.libraries.items[size_t(view_.selected)]);
            return;
        }
        movePosterFocus(down);
        int count=int(view_.page.items.size());
        if((down&HidNpadButton_ZR)&&view_.screen==Screen::Grid&&view_.offset+count<view_.page.total)loadScreen(Screen::Grid,view_.parent,view_.container,view_.offset+count,view_.search,false);
        if((down&HidNpadButton_ZL)&&view_.screen==Screen::Grid&&view_.offset>0)loadScreen(Screen::Grid,view_.parent,view_.container,std::max(0,view_.offset-60),view_.search,false);
    }
    void handleTouch(const TouchResult& result) {
        if(result.gesture==Gesture::None)return;
        if(result.gesture==Gesture::Scroll){if(!playing_&&!busy_){scrollTo(view_.targetScroll-result.dy);if(view_.targetScroll>550&&hasHero())view_.section=3;}return;}
        if(result.gesture==Gesture::SwipeLeft||result.gesture==Gesture::SwipeRight) {
            if(busy_)return;
            int direction=result.gesture==Gesture::SwipeLeft?1:-1;
            if(playing_&&!menu_)player_->seek(-direction*10);
            else if(hasHero()&&view_.targetScroll<200&&!view_.page.items.empty()){rotateHero(direction);view_.section=0;}
            lastInput_=SDL_GetTicks();return;
        }
        if(result.gesture==Gesture::Tap) {
            if(busy_){if(result.point.y>640&&result.point.x>850)api_.cancel=true;return;}
            for(auto it=hits_.rbegin();it!=hits_.rend();++it)if(it->bounds.contains(result.point)){auto action=it->action;lastInput_=SDL_GetTicks();action(result.point);return;}
            if(playing_){if(menu_){menu_=false;variants_.clear();}else lastInput_=SDL_GetTicks()-lastInput_<4000?SDL_GetTicks()-5000:SDL_GetTicks();}
        }
    }
    void event(const SDL_Event& e) {
        if(e.type==SDL_QUIT){run_=false;return;}
        if(e.type==SDL_FINGERDOWN)touch_.begin(e.tfinger.fingerId,{e.tfinger.x*1280,e.tfinger.y*720});
        else if(e.type==SDL_FINGERMOTION)handleTouch(touch_.move(e.tfinger.fingerId,{e.tfinger.x*1280,e.tfinger.y*720}));
        else if(e.type==SDL_FINGERUP)handleTouch(touch_.end(e.tfinger.fingerId,{e.tfinger.x*1280,e.tfinger.y*720}));
        else if(e.type==SDL_MOUSEBUTTONDOWN&&e.button.which!=SDL_TOUCH_MOUSEID)touch_.begin(-1,{float(e.button.x),float(e.button.y)});
        else if(e.type==SDL_MOUSEMOTION&&e.motion.which!=SDL_TOUCH_MOUSEID&&touch_.active())handleTouch(touch_.move(-1,{float(e.motion.x),float(e.motion.y)}));
        else if(e.type==SDL_MOUSEBUTTONUP&&e.button.which!=SDL_TOUCH_MOUSEID)handleTouch(touch_.end(-1,{float(e.button.x),float(e.button.y)}));
        else if(e.type==SDL_MOUSEWHEEL&&!playing_)scrollTo(view_.targetScroll-e.wheel.y*80);
    }
