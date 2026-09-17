> 历史档案：已由 `docs/DEVELOPMENT_PLAN.md` 与 `docs/CODEX_HANDOFF.md` 取代。
> 不要按旧名称、旧阶段声明或未核对的设备信息重新创建工程。

# MediaIndex TV：开发规格与 Codex 任务书

> 暂定项目名：MediaIndex TV；建议独立仓库 `mediaindex-tv`。这是一份实施规格，不是已完成的源码或 APK。本文中的性能数字均为待真机验证的工程目标，不是实测结果。
>
> 视觉基准：本次对话用户提供的 LumiPlayer 首页截图。未完整读取到 B 站／抖音演示，不能把未观察到的播放控制、过场和设置页当作 LumiPlayer 已有设计。

## 1. 产品定位和不可变约束

做一个以电视遥控器为第一输入方式的 Android TV 播放器：LumiPlayer 风格的沉浸式首页，Emby 影视库，CloudDrive2 文件接入，对 MediaIndex STRM／302／代理流的稳定播放，以及可测量的界面流畅度和起播时间。

用户此前提供的目标设备是雷鸟 85 寸鹏5，曾使用 Moonfin 2.4.0 TV 版并反馈界面卡顿、界面清晰度不理想。具体 Android 版本、应用可用 ABI、UI 合成分辨率和当前 Moonfin 版本仍需设备报告；不能仅凭电视处理器名称推定系统支持 64 位 APK，也不能把此前版本当作当前版本。

不可变约束：

- 新建独立播放器项目，不修改 `xudong7587/media-index` 的 main，不改变其目录、命名、STRM 内容、签名、鉴权、删除联动和网盘 Provider 行为。
- 第一版必须包含 Emby、CloudDrive2 和 STRM 播放。多源能力先实现多 Emby 实例和 CD2 来源，不把 Plex、直播、阅读器、下载中心等同时纳入首版。
- 默认只读；播放器不发起网盘删除、移动、重命名，不触碰已有媒体整理流程。
- 不依赖 LumiPlayer 的安装包、源码或未核实接口。只参考用户截图中的视觉语言，自行实现界面和交互，使用独立名称、包名与图标。
- 不用网站套 WebView 冒充原生 TV 客户端；不把 Moonfin 简单改色当作性能优化。
- 不能宣称采用 Kotlin／Compose 后就一定比 Flutter 快。优化有效性由同机对照测试决定。

## 2. 本次已核对的技术事实

### 2.1 Moonfin

已读取官方仓库 `Moonfin-Client/Moonfin-Core` 的 README 和 LICENSE。当前主线是 Flutter 跨平台项目，README 标明支持 Jellyfin 和 Emby，项目声明 GPL-2.0-or-later。

仓库包含 Android 原生播放代码：

```text
packages/moonfin_native_video/android/src/main/kotlin/org/moonfin/nativevideo/
```

目录中已看到 `Media3PlayerActivity.kt`、`Media3Bridge.kt`、`AudioPassthroughPolicy.kt`、`DisplayModeChooser.kt`、`DoviCompatExtractorsFactory.kt` 等文件。它们是待进一步审计的参考模块，不代表已验证可以直接拔出来编译。

建议利用方式：以官方 Media3 为第一版可编译基础，审计 Moonfin 的播放策略和必要兼容处理，按实际失败样本逐项移植。不整体复制 Flutter 插件桥接、平台通道、全局状态及无关多平台功能。不得仅根据文件名承诺某种杜比视界或音频格式必然可用。

只要实际复制、改写或翻译其受 GPL 约束的代码，必须核对相关文件及依赖许可证，保留版权、修改记录和许可证，分发衍生 APK 时按许可证提供对应源码。换一种语言不自动解除许可证义务。记录在 `THIRD_PARTY_NOTICES.md` 与 `docs/UPSTREAM_AUDIT.md`。

来源：
- https://github.com/Moonfin-Client/Moonfin-Core
- https://github.com/Moonfin-Client/Moonfin-Core/blob/main/LICENSE
- https://github.com/Moonfin-Client/Moonfin-Core/tree/main/packages/moonfin_native_video/android/src/main/kotlin/org/moonfin/nativevideo

### 2.2 MediaIndex 的实际播放契约

已查看 `xudong7587/media-index` 的 README、STRM 生成相关搜索结果、`backend/app/api/playback.py` 和 `backend/app/services/playback.py`。

当前读取到的 STRM 生成形式是：

```text
{base_url}/api/play/{token}\n
```

README 中默认独立播放端口是 8097；实际部署地址、反向代理前缀和端口必须从用户配置读取，不能硬编码。

已确认的服务端行为：

| 条件 | 服务端行为 | 客户端要求 |
|---|---|---|
| 上游不需要额外网盘请求头 | HTTP 302，带 `Cache-Control: no-store` | 正确跟随重定向，不永久保存最终 CDN URL |
| 上游需要请求头 | 服务端代理输出媒体字节 | 不强行改成直连，不向电视索取网盘 Cookie |
| GET／HEAD 与 Range | 已有专门处理 | 保持请求语义，测试起播、续播与拖动 |
| 播放资产或令牌失效等错误 | 播放入口可返回 409 | 展示可理解的源错误，不能假装是解码问题 |
| 115 直链解析 | 传入客户端 User-Agent，缓存键包含其摘要 | 同一播放会话保持 UA 一致 |
| 部分代理上游返回 401／403 | 服务端存在有界刷新重试 | 客户端不得再叠加无限重试 |

播放响应还会标记 `X-MediaIndex-Playback-Mode: redirect` 或 `proxy`，可用于诊断。令牌属于播放凭据，日志不得记录完整 `/api/play/{token}` 路径。

这些结论适用于本次读取的主线代码，不代表已核对用户 NAS 当前运行的镜像版本。

来源：
- https://github.com/xudong7587/media-index/blob/main/README.md
- https://github.com/xudong7587/media-index/blob/main/backend/app/services/strm_reconciler.py
- https://github.com/xudong7587/media-index/blob/main/backend/app/api/playback.py
- https://github.com/xudong7587/media-index/blob/main/backend/app/services/playback.py

### 2.3 CloudDrive2

官方说明确认有 WebDAV 服务及开放 gRPC API，提供 proto 和开发指南。具体登录、目录列举、播放地址等 RPC 的名称、参数、权限与版本支持，必须以所连 Core 的版本和对应 proto 为准；本文不虚构 REST 端点或未核实的 RPC 签名。

实现独立 `CloudDriveSource`，内部提供两种传输适配：

1. WebDAV 兼容模式：读取目录、媒体文件、STRM 和同目录辅助文件，先完成可用播放闭环。
2. 原生 API 模式：核对版本和能力后，生成 gRPC 客户端，实现目录、文件标识、有效播放地址及可用的增量能力。

第一版不能只有一个名为 CloudDrive2 的空设置页。至少 WebDAV 模式要完成真实连接、目录浏览、视频及 STRM 播放；原生 API 模式必须有明确完成状态，不伪装已支持。

CD2 提供的是文件来源，不天然等于带完整影片介绍、演职员和观看进度的 Emby 媒体库。已有 Emby 入库内容优先从 Emby 获取海报元数据；CD2 独立内容先用文件列表／基础卡片，按需读取同目录 NFO 和图片，缺失时使用默认封面。不在电视启动时全盘刮削。

来源：
- https://www.clouddrive2.com/
- https://www.clouddrive2.com/api/CloudDrive2_gRPC_API_Guide.html
- https://www.clouddrive2.com/api/clouddrive.proto （开发时下载、锁定版本并核验）

## 3. 技术路线

首选：独立 Android 原生 TV 应用，Kotlin + Compose for TV + Media3／ExoPlayer + SurfaceView。

Compose 用于首页、海报列表、详情和设置；播放 Surface 与界面生命周期清晰分离。可以用独立原生播放 Activity 承载 PlayerView／SurfaceView，再叠加 TV 控制层；实施时测试 Surface 与 Compose 混合的裁切、层级、黑屏和生命周期问题，不把“能显示一次”当作稳定兼容。

依赖类别建议：Kotlin Coroutines、OkHttp、Room／DataStore、按显示尺寸解码的图片加载库、Media3。具体稳定版本在项目启动时核对并写入版本目录，禁止使用 `+`、动态 latest 或擅自升级核心依赖。

第一版只集成一个内置播放引擎。外部播放器可作为诊断／手动回退入口；只有实际片源验证出 Media3 无法满足的缺口，才评估 libmpv／libVLC 等第二引擎及其包体、许可证和维护代价。

Compose for TV 和 SurfaceView 的参考依据：
- https://developer.android.com/training/tv/playback/compose
- https://developer.android.com/media/media3/ui/surface

### 建议目录边界

以下是逻辑分层，不要求每个目录都变成独立 Gradle module。初期避免过度拆分：

```text
app/
  core/
    model/         # 跨来源媒体标识、播放请求、错误模型
    network/       # 重定向、鉴权作用域、超时、日志脱敏
    storage/       # 配置、观看记录、海报和元数据缓存
    playback/      # Media3、轨道、音视频能力、生命周期
  source/
    emby/          # 用户登录、媒体库、PlaybackInfo、进度回报
    clouddrive/    # WebDAV + 经过验证的原生 API 适配
    strm/          # 内容解析、递归限制、稳定入口与错误分类
  feature/
    home/
    library/
    detail/
    player/
    sources/
    settings/
benchmark/         # 真机性能测试
 docs/
```

UI 不直接拼 Emby URL、不理解网盘 Cookie，也不直接调用 CD2 RPC。各 Source 输出统一的媒体条目和播放请求；播放层只执行已解析的播放请求并报告状态。

统一播放请求至少包含：稳定条目标识、来源标识、原始入口 URL、容器／MIME 提示、受作用域限制的请求头、起始位置、可选字幕、可选 Emby 会话上下文及重试／刷新策略。临时 URL 和稳定入口必须区分存放。

观看记录的主键包含来源与条目标识，不能仅按文件名或片名覆盖。不同来源的同名影片也不能未经映射就互写进度。

## 4. 界面和遥控器规格

### 4.1 首页

从截图保留：大幅背景、深色渐变遮罩、顶部胶囊导航、左侧大标题与简介、播放主按钮、继续观看区域、右侧横向海报。

改成 16:9 TV 布局，不机械照搬桌面截图比例。移除窗口最小化／最大化／关闭、小尺寸鼠标控件和会员装饰。

默认导航：`首页 / 影视库 / 云盘 / 设置`，搜索用单独可聚焦入口。UI 文案允许后续修改，不硬编码到网络模型。

建议布局：顶部安全区内为导航；中部左侧约 38% 显示影片介绍和操作，右侧约 62% 显示当前选中大卡片及相邻海报；底部提供继续观看行。海报可以采用一张主卡片加相邻普通卡片，避免把其余影片压成无法阅读的细条。

选中影片时先立即更新焦点边框和标题。背景图片在焦点停留约 200–300ms 后才替换，旧请求取消；背景淡入约 150–220ms。这些是待调参初值。

第一版不实现首页自动预告播放、实时全屏模糊、粒子、循环视差或连续 3D 翻转。用预先确定的遮罩、色彩与短动画保留氛围。

焦点动画优先轻微缩放（建议初值 1.02–1.04）、描边和透明度，不以每帧更改全部卡片宽度来制造手风琴效果。

### 4.2 遥控器

上／下跨区域，左／右在当前行移动。每个区域保留最近聚焦项；从详情返回时恢复原行、原卡片和滚动位置。

确认键执行当前控件动作；海报确认进入详情，详情的播放／继续播放进入播放器。长按菜单可以后置，不作为唯一可达操作入口。必须处理连续按键，不卡队列、不丢焦点，不让焦点跳入尚未加载的项目。

返回键优先关闭面板／弹窗，再返回上一级；播放控制层可见时先隐藏控制层，再按返回才执行离开播放器的约定动作。不能因为一个底部抽屉而重建整个播放器。

### 4.3 播放中界面

截图未包含实际播放控制层，以下为新设计：底部一条精简进度控制，确认键显示／隐藏；左右短按按配置步长快退／快进，长按渐进加速。上键打开音轨／字幕入口，下键显示剧集／播放列表。屏幕明确显示当前焦点，遥控器无需鼠标即可操作所有功能。

第一版提供：暂停／继续、从头／续播、进度拖动、音轨选择、字幕选择及关闭、字幕延迟、下一集、画面比例、可关闭的技术信息面板。

缩略图预览、片头片尾跳过、字幕在线搜索、杜比视界特殊转换等，只在后续逐项接入并验证，不用图标假装可用。

## 5. Emby 接入

使用用户登录和用户 Token，保留账号权限；不要默认要求管理员 API Key。地址规范化需要兼容端口、HTTPS、反向代理路径前缀和合理的 `/emby` 路由形式，不能把用户地址简单裁掉路径。

首版读取影视库、电影、剧集／季／集、海报、简介、最新入库、继续观看、收藏；支持基本搜索和分页。根据用户权限隐藏不可用入口。

播放前通过实际 Emby API 和返回数据获取媒体源信息及会话上下文，根据设备真实解码能力选择可用路径。优先 Direct Play／Direct Stream；需要转码时遵循用户设置与服务器能力，显示原因，不伪造全格式支持来强行直放。

MediaIndex STRM 经 Emby 提供可播放入口时，第一版沿用该入口。不得假设 Android TV 能直接读取 NAS 上的 `/strm/...` 或 `/volume...` 文件路径。

若服务器返回的路径不可从电视访问，应使用服务端提供的播放 URL；只有明确配置并验证的路径映射才可尝试直连。不要对任意服务器路径做全局字符串替换。

即使视频最终直接来自 MediaIndex 或网盘，仍须按 Emby 会话语义回报开始、进度、暂停和停止，保留 ItemId／MediaSourceId／会话标识。预取元数据不算开始播放。回报不得阻塞 UI；事件顺序和重试必须可控。

API 参数以所连接 Emby 版本的 API Browser 为准：
- https://dev.emby.media/doc/restapi/index.html

## 6. STRM 与起播优化

### 6.1 首选链路

```text
Emby 提供海报、详情、观看进度和播放源信息
    → 电视获取可播放入口
    → MediaIndex /api/play/{token}
        → 302：电视跟随到云端媒体流
        → proxy：电视接收 MediaIndex 代理流
    → Media3 解码并显示首帧
```

CD2 为另一条独立来源：

```text
CD2 文件列表
    → 视频文件：经验证的 CD2 HTTP／WebDAV／API 播放路径
    → STRM 文件：读取小文本，再交给 STRM 解析器
    → Media3
```

不要为了统一架构，让原本可用的 MediaIndex STRM 再绕过 CD2／FUSE；也不要强制所有内容通过新的电视端网盘 SDK。

### 6.2 解析规则

允许 UTF-8 BOM、CRLF、LF 和首尾空白。给 STRM 文本设置较小读取上限，拒绝把大视频文件当成文本下载。第一版仅接受经过校验的 HTTP／HTTPS 目标；其他协议明确标记不支持，不任意启动外部 Intent。

保留 URL 原有查询参数、签名、路径转义，不二次整体编码或擅自解码 `%2F`。中文、空格、`+`、`#`、百分号等都用测试覆盖。

MediaIndex 播放入口没有 `.mp4`／`.mkv` 后缀。播放判断不能仅依赖 URL 扩展名；结合真实媒体源容器信息、响应 Content-Type 和提取器探测，不把所有未知地址强制当 HLS。

对嵌套 STRM 设递归上限（建议初值 3）和循环检测。HTTP 重定向另设独立上限（建议初值 5–8），检测环路。具体数字可配置，但必须有界。

### 6.3 网络与凭据

保持会话 UA 一致，尤其是解析与实际媒体 GET。相对 Location 按响应 URL 正确解析。HTTPS 降级 HTTP 不做无条件放行；兼容用户明确配置的本地服务时设可审计的规则。

Emby、CD2 的认证头只发给对应受信来源，不随跨域 302 转发到 CDN。不能使用全局认证拦截器向所有主机附加 Token。不要照搬其他项目的“信任所有 TLS 证书”实现。

默认保留 HTTP Range 语义及 Content-Range 检查。206、200、不支持 Range、416 都要明确处理，不通过下载数 GB 再丢弃数据伪装正常续播。

不要固定走“HEAD 探测一次 → GET 再解析一次 → 正式播放再请求一次”的冗余流水线；按源能力选择必要探测，尽量直接进入播放器可复用的读取路径。

MediaIndex 的 302 标记了 no-store，因此不持久缓存最终 Location。已知短期 CDN 地址过期时，回到原始稳定入口重新请求；对原始入口自身的鉴权错误与资产失效分别提示，不无限刷新。

客户端刷新与引擎重试共用统一预算，保留恢复位置；避免播放器、Source 和 HTTP 层各自重复重试形成请求风暴。

日志对 Token、Cookie、签名参数、完整播放路径和个人服务器地址做脱敏。诊断只需要来源类型、主机类别、状态码、耗时、重定向次数与关联会话的匿名 ID。

### 6.4 缓存和预加载

元数据、图片缓存和视频缓存分开。优先缓存目录、详情与尺寸合适的海报，不在焦点移动时解析所有卡片直链。

初版仅对当前可见区和少量相邻卡片预取图片。下一集默认只预取元数据；只有建立限流并证明有收益后，才做可取消、有上限的视频预缓冲。

初期不将所有云端视频落盘缓存；需要视频块缓存时必须设总量、淘汰、隐私隔离，并使用稳定资产标识作为缓存键，避免过期 URL 造成重复缓存，也避免不同视频错误复用。

缓冲参数按实测网络和码率调节。不把“首缓冲降到极小值”当成唯一优化，它可能只是把等待挪到播放后卡顿。

## 7. TV 性能方案

### 7.1 UI 和视频分开优化

没有真机 profile，不能认定 Moonfin 卡顿由 Flutter、低分辨率、服务器响应或某一个动画单独造成。

本项目限制无关功能和渲染负担：列表只创建可见项；稳定 item key；状态按区域拆分；按键状态不触发整页重组；图片按实际显示尺寸解码；请求取消；主线程不做网络、数据库大查询、XML／NFO 解析或图片处理。

图片内存缓存按设备 memory class 分档设置，并保留低内存回收入口。不能给所有电视固定巨额缓存。图像请求并发与云盘目录请求并发分别限流。

UI 分辨率和视频输出分辨率必须分别显示。Android 官方说明部分电视的 UI 层低于面板分辨率，SurfaceView 可用于面板原生分辨率的视频输出。不能把界面采用较低渲染负担等同于视频只能 1080p，也不能承诺 APK 可以强行绕过系统 UI 分辨率限制。

设计“流畅／高清”显示策略时，优先调整图片分辨率、动画和缓存；仅在系统实际支持的窗口／显示能力内选择模式。不得自动执行 `wm size`、`wm density` 或更改全系统显示设置。

### 7.2 解码能力

读取实际 MediaCodec、屏幕与音频输出能力；使用硬解优先策略，但不伪造设备支持。HEVC、AV1、HDR、Dolby Vision、DTS、TrueHD 等按实际格式、电视固件及音频链路测试。

视频格式支持、HDR 输出和音频直通不等价。回退可为兼容音轨、PCM 或服务器允许的转码，必须显示真实状态；不悄悄改成高负载软解。

刷新率匹配与系统模式切换可能产生独立黑屏等待，需单独计时；不要把这段时间全部归因于云盘取流。

### 7.3 测试目标

在目标电视、固定刷新率和固定样本下，与当前 Moonfin 做同条件测试。以下是工程目标：

| 项目 | 初始目标／测量方式 |
|---|---|
| 焦点响应 | 按键到可见反馈 p95 低于 100ms |
| 60Hz UI | 按约 16.7ms 帧预算优化；记录 frame overrun／卡顿比例，不只看平均 FPS |
| 快速连按 | 连续浏览至少 200 次方向键，无焦点丢失和明显队列积压 |
| 页面返回 | 恢复原焦点和滚动位置，不重拉整库 |
| 缓存命中的首页 | 分别记录首个可交互时间与图片完成时间 |
| 起播 | 分别记录直连、本地代理、云盘冷／热请求的首帧 p50／p95，不混为一个“秒开”指标 |
| 网络健康下常规片源 | 可先以首帧 1–3 秒作为调优方向；云盘冷解析和模式切换不作保证 |
| 稳定性 | 连续看片／选片 30 分钟以上，观察内存趋势、资源释放、ANR 和恢复行为 |

起播埋点至少包括：按下播放 → 获取源信息 → 请求稳定入口 → 重定向完成／代理响应头 → 首字节 → 解码准备 → 实际渲染首帧。不能用 `prepare()` 返回或状态 READY 冒充首帧。

使用接近 release 的可 profile 构建、R8 和 Baseline Profile；不要拿 debug 包、模拟器或不同设备的测试结果证明性能提升。Macrobenchmark 与 Perfetto 用于验证和定位，电视系统不支持某项工具时明确记录替代测量及其局限。

参考：
- https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
- https://developer.android.com/topic/performance/baselineprofiles/overview
- https://developer.android.com/media/media3/ui/surface

## 8. 必测场景

### 源与网络

- Emby 直连、带路径前缀的反代、已有 STRM 库；允许配置多个服务器。
- CD2 正常／错误凭据、连接超时、空目录、大目录、中文目录、媒体文件、STRM、缺失元数据。
- MediaIndex 无扩展名入口、302、需要请求头的代理分支、无效 Token、资产失效。
- 绝对／相对 302、多次重定向、重定向环路、跨域凭据泄露检查。
- Range 206、起始位置续播、快进、服务端不支持 Range、416、短期地址失效后有界恢复。
- 网络断开恢复、用户取消起播、快速换片、账号退出后缓存隔离。

### 影片

先使用用户有权访问的少量真实样本：普通 H.264 MP4、HEVC MKV、带内封／外挂字幕的视频、多个音轨、MP4 元数据在文件尾部的样本，以及一份实际 MediaIndex STRM。HDR／DV／特殊音频另建能力矩阵，未测试就标记未验证。

### 遥控器和生命周期

所有页面用方向键、确认和返回完成操作；不要求鼠标。验证横纵跨区、空列表、加载期间移动、弹窗关闭、详情返回、播放退出、应用后台恢复、电视休眠恢复，以及播放器资源释放。

## 9. 开发阶段和每阶段交付

### 阶段 A：基础与纵向闭环

核对许可证、依赖和构建环境；新建独立项目；锁定参考仓库 commit。提供可安装 TV 包，能打开一个用户配置的媒体 URL，并记录真实首帧。接入一套真实 Emby 登录／列表／详情／播放／进度回报，提供 MediaIndex 稳定入口的自动化 HTTP 测试。

同时建立基础首页布局和焦点路径。视觉演示可以使用明确标注的本地假数据，但验收播放必须使用真实服务，不得把假数据 demo 说成已完成接入。

### 阶段 B：Lumi 风格首页与 CD2

完成截图风格的首页、继续观看、海报浏览、详情和控制层。完成 CD2 WebDAV 真实可用接入，核对并实现可用的原生 API 能力。支持独立 CD2 视频及 STRM 播放。

### 阶段 C：兼容性和性能

按测试矩阵修复 UA、重定向、Range、字幕和音轨问题；验证跨域认证隔离。实测性能、调节图片／动画／缓冲策略，制作 Baseline Profile。对比 Moonfin，保存报告，不做无数据的“已优化”声明。

### 阶段 D：可维护交付

交付源码、可复现构建说明、已签名测试 APK／正式 APK、版本号、SHA-256、第三方声明、测试结果和已知问题。签名密钥由用户保存，不提交仓库；以后更新保持同一应用签名，避免覆盖安装失败。

仅在依赖实际支持且真机需要时产出相应 arm64-v8a／armeabi-v7a 构建，不假装已支持未经验证的 ABI。包名独立，不覆盖 Moonfin。

## 10. 给 Codex 的开工指令

将以下内容连同本文件及用户的 LumiPlayer 截图一起交给 Codex：

```text
请在一个全新独立仓库中开发 Android TV 播放器，暂名 MediaIndex TV。
先完整阅读《MediaIndexTV_开发规格与Codex任务书.md》与截图，再实施。

目标是 LumiPlayer 截图风格的原生 TV 交互 + Emby + CloudDrive2 + 对
xudong7587/media-index 当前 STRM/302/代理流契约的兼容，不是给 Moonfin
简单换皮，也不是把 PC 页面塞进 WebView。

先核对 Moonfin-Client/Moonfin-Core 的 LICENSE、当前提交和 Android Media3
模块。以 Kotlin + Compose for TV + 官方 Media3 为初始可编译基础，
按需参考或移植 Moonfin 代码；逐文件记录来源与许可证，不强行拖入整套
Flutter 平台桥接或未验证的 HDR/音频补丁。

不要更改 xudong7587/media-index 的任何现有代码、数据、签名或接口。
播放器独立适配现有契约；确需服务端新增能力时，先记录原因并单独提案，
不得先改 main。不要读取或传播用户网盘 Cookie。

本轮先完成阶段 A：可构建、可安装、遥控器可操作、真实 Emby 播放、
进度回报、MediaIndex 入口兼容测试、首帧诊断。随后按阶段 B/C/D推进。
不得以占位页、假数据演示或未执行测试代替完成状态。

建立 AGENTS.md，按 source/emby、source/clouddrive、source/strm、
core/playback、feature/home 和 benchmark 划分责任。
每次改动只解决一个模块或一个明确问题；共享核心改动必须说明影响范围
并补回归测试。禁止无关重构、全项目升级依赖和隐式修改公共模型。

开工后先列出仓库结构、锁定的依赖版本、来源许可证审计结果和阶段 A的
验收清单，然后实际创建项目、运行构建与测试。

每轮报告必须区分：已实现、已编译、已自动测试、已真机验证、尚未验证。
如缺少工具链、电视连接或测试服务，指出具体缺项，继续完成不依赖该项
的工作，不得宣称生成了 APK 或通过了真机测试。
```

## 11. 真机适配时需要的最少信息

不用提交账号密码、网盘 Cookie 或签名直链。先获取系统信息：

```bash
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.product.cpu.abilist
adb shell getprop ro.product.cpu.abi
adb shell wm size
adb shell wm density
```

以上是读取，不是修改分辨率。再用本地配置填写 Emby 和 CD2 服务，检查实际部署版本，并选择少量合法测试片源。STRM 测试样本在分享前须把 Token、签名和个人域名脱敏；真实播放凭据仅存于用户本地开发配置。
