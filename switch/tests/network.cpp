#include "sunny/gateway.hpp"
#include "sunny/json.hpp"
#include <curl/curl.h>
#include <chrono>
#include <iostream>
#include <sstream>
#include <thread>

using namespace sunny;
int checks = 0;
void check(bool ok, const char* label) { ++checks; if (!ok) throw std::runtime_error(label); }
void rejects(const std::function<void()>& f, const char* label) { bool rejected = false; try { f(); } catch (...) { rejected = true; } check(rejected,label); }
int main(int argc, char** argv) {
    if (argc != 3) return 2;
    curl_global_init(CURL_GLOBAL_DEFAULT);
    try {
        Http http; std::string base=argv[1], tls=argv[2];
        Scope scope{base+"/scope/", {{"X-Emby-Token","secret"},{"Authorization","Bearer secret"},{"Cookie","session=secret"}}};
        Request req; req.url=base+"/scope/echo?sig=a%2Fb"; req.scope=scope;
        auto r=http.fetch(req); auto j=Json::parse(r.body);
        check(j["token"].text()=="secret" && j["authorization"].text()=="Bearer secret", "scoped headers sent");
        check(j["path"].text()=="/scope/echo?sig=a%2Fb", "wire query untouched");
        for(const auto& path:{"/scope/redirect-origin","/scope/redirect-path"}) {
            req.url=base+path; auto result=Json::parse(http.fetch(req).body);
            check(result["token"].text().empty() && result["authorization"].text().empty() && result["cookie"].text().empty(), "redirect strips all credentials");
            check(result["path"].text().find("sig=a%2Fb")!=std::string::npos, "redirect signed URL preserved");
        }
        req.url=base+"/scope/loop"; rejects([&]{http.fetch(req);},"redirect loop bounded");
        req.url=base+"/scope/large"; req.maxBytes=32; rejects([&]{http.fetch(req);},"body budget enforced"); req.maxBytes=8*1024*1024;
        req.url=tls+"/"; rejects([&]{http.fetch(req);},"untrusted TLS certificate rejected");
        req.url=base+"/scope/slow"; std::atomic_bool cancel{false}; std::thread stopper([&]{std::this_thread::sleep_for(std::chrono::milliseconds(100));cancel=true;});
        auto begin=std::chrono::steady_clock::now(); rejects([&]{http.fetch(req,&cancel);},"request cancellation"); stopper.join();
        check(std::chrono::steady_clock::now()-begin<std::chrono::seconds(5),"cancellation responsive");
        {
            Gateway gateway(std::string(48,'a')); Request local; local.url=gateway.add(base+"/scope/index.m3u8",scope);
            auto playlist=http.fetch(local); check(playlist.status==200,"gateway HLS status");
            check(playlist.body.find("secret")==std::string::npos && playlist.body.find("a%2Fb")==std::string::npos,"rewritten manifest hides remote tokens");
            auto start=playlist.body.find("URI=\""); auto end=playlist.body.find('"',start+5); Request key; key.url=playlist.body.substr(start+5,end-start-5); auto keyReply=Json::parse(http.fetch(key).body); check(keyReply["token"].text()=="secret","HLS key receives only scoped credentials");
            auto last=playlist.body.rfind("http://127.0.0.1:"); auto newline=playlist.body.find('\n',last); Request segment; segment.url=playlist.body.substr(last,newline-last); auto seg=Json::parse(http.fetch(segment).body); check(seg["token"].text().empty(),"CDN segment receives no Emby token");
            Request movie; movie.url=gateway.add(base+"/scope/movie",scope); movie.headers["Range"]="bytes=2-5"; auto bytes=http.fetch(movie); check(bytes.status==206&&bytes.body=="2345"&&bytes.headers["content-range"]=="bytes 2-5/10","gateway range and content-range");
            movie.method="HEAD"; auto head=http.fetch(movie); check(head.status==206&&head.body.empty(),"gateway HEAD");
            Request denied; denied.url=local.url.substr(0,local.url.find('/',7))+"/invalid/1"; check(http.fetch(denied).status==404,"gateway rejects unknown resource");
        }
        std::cout<<checks<<" network integration checks passed\n";
    } catch(const std::exception& e) { std::cerr<<"FAIL after "<<checks<<": "<<e.what()<<'\n'; return 1; }
    curl_global_cleanup();
}
