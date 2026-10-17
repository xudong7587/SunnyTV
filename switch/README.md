# SunnyTV Switch 0.2.10

基于 SunnyTV Android `v0.2.8`（`dcc09409c98f0819b63f763913e57cc822fdaec6`）新增的独立原生客户端。目标为支持自制程序的 Nintendo Switch，交付 `SunnyTV.nro`，自用侧载。

## 安装与运行

把构建包中的 `switch/SunnyTV/` 复制到 SD 卡的 `/switch/SunnyTV/`，从 Homebrew Menu 使用完整应用内存模式启动。普通原厂系统不能运行 NRO。Applet 模式、真机播放性能与休眠恢复尚待验证。

首次启动填写 Emby 服务地址、用户名和密码；绿联选择“绿联影视中心”，使用具有有效证书的 HTTPS 媒体服务地址，根地址会补 `/emby/`。应用不访问或修改 NAS 媒体文件。

“记住登录”默认关闭。开启后访问令牌保存在设备 SD 卡 `switch/SunnyTV/config.json`，密码不保存；可在设置清除。SD 卡配置文件不加密，不要分享。系统字体仅在本机复制给字幕渲染器，分发包不含字体；可把自己的字体放入 `fonts/`。

## 操作

左摇杆或方向键选择，A 确认，B 返回；L/R 切换首页、媒体库和设置；Y 搜索；ZL/ZR 翻页；− 退出。

支持触摸点击、纵向滑动、首页横划轮播和进度条点击跳转。

播放中 X/A 暂停，L/R 或左右快退/快进 10 秒，＋ 打开菜单，B 关闭菜单或退出播放。菜单提供音轨、字幕、字幕偏移、MediaIndex 115 云端清晰度和解码信息。字幕偏移正值表示延后，负值表示提前。

## 已实现的代码范围

- Emby 登录、媒体库、续播/最新列表、分页、搜索、剧集/季、详情和原生图片。
- 绿联兼容接口的基础路径和 HTTPS 限制；尚未真实联调。
- 直放/直流选择、HTTP STRM、单次服务端后备、Emby 播放会话回报。
- libmpv + SDL/OpenGL ES 播放（视频与 UI 使用共享纹理、独立 GL 上下文）、立体声输出、音轨/字幕切换及微调。
- MediaIndex cloud-video-v1 查询/创建/停止；默认 direct，不修改 MediaIndex。
- 本机播放网关逐跳执行 TLS、凭据作用域与取消检查；HLS 清单、密钥、分片统一改写至回环地址。远程地址不交给 mpv 日志。

以上描述代码范围，不能代替构建、服务联调或真机验收。最新证据见 `STATUS.md`。

## 构建

使用 devkitPro devkitA64、libnx 和以下 portlibs：

```sh
dkp-pacman -Syu --needed switch-libmpv switch-sdl2_ttf switch-sdl2_image switch-curl switch-pkg-config
. /opt/devkitpro/switchvars.sh
make -C switch -j2 package
```

仓库 `.github/workflows/switch.yml` 构建独立 Actions artifact，不发布 Android Release。产物带 SHA-256、源码 SHA、实际工具链包版本。当前容器镜像和 portlibs 使用仓库可用版本；包清单用于定位，尚非锁定工具链的位级可复现构建。

宿主机核心测试：`bash switch/tests/run.sh`。可用 `SANITIZE=1` 启用 ASan/UBSan。

## 当前限制与下一步

1. 首期可运行性需要交叉编译和 Switch 真机确认；不承诺 4K、码率上限或特定格式硬解。
2. CloudDrive2 WebDAV 浏览、本地 SD 文件浏览、收藏修改、完整主题/设置和自动下一集尚未实现。
3. 绿联完整目录本地排序、字幕搜索下载、云端切回原画和断流恢复仍需补齐。
4. 依赖 libmpv 的字幕格式、中文字体和 AAC/PCE 兼容性需真机验证；安卓补丁不会自动继承。
5. 当前播放网关不对中途断流做续接重试；遇到失败会退出播放并提示。进度回报按顺序发送，网络失败和立即退出可能导致服务端未收到最后一次事件。
6. 图片仅内存缓存且有上限；大型媒体库需后续完善缓存淘汰与后台任务优先级。
7. 当前没有持续运行的 Switch 模拟器或真机，不把宿主机测试计为真机通过。

GPL-3.0-only，沿用根目录 LICENSE。依赖说明见 `THIRD_PARTY.md`。

## 0.2.10 修订

修复 Switch SDL 后端名为 `opengles2` 时被桌面 `opengl` 名称检查错误拒绝的问题。显式选择 Switch 的 GLES 后端，隔离 mpv 与 SDL 的 GL 状态，使用共享纹理输出并同步 GPU，增加颜色、朝向、界面叠加与上下文恢复检查。

对照 Android 0.2.8 的 `RotatingHeroCards` 修正固定大方卡、循环轮播、向下进入媒体区、Logo 左对齐及媒体库卡片尺寸；不宣称完整功能或所有页面已与 Android 一致。

播放方案参考 [Switchfin 播放核心](https://github.com/dragonflylee/switchfin/blob/1cb439ac51d0a228061b95e2a40e4ce9a01cce1e/app/src/view/mpv_core.cpp) 及 [mpv 的 GL 互操作说明](https://github.com/mpv-player/mpv/blob/v0.36.0/libmpv/render_gl.h)。未复制 Switchfin UI、源码或其打包二进制；本版仍使用 devkitPro 官方 portlibs。Switchfin 的 deko3d、定制 FFmpeg 和直接硬解路径尚未移植，不能据此宣称相同硬解能力。
