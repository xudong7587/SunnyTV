# 2026-10-16 SunnyTV 0.2.5 / 57

基于 0.2.4 增加播放器右上角实时信息开关，使用界面字体，左上角每秒刷新真实格式、下载字节速度、缓冲和解码器；标称码率与接收速度分开。软件升级页改为 GitHub 在前、WebDAV 默认关闭，开启后显示已加密保存的配置；WebDAV 直接下载 GitHub latest 同名 APK，不读取 DAV JSON，验证包名、两个版本字段及签名后交给系统确认。关于页 MediaIndex 使用文字 GitHub 详情链接。

用户已确认 0.2.4《功夫梦》云端 1080P / 4K 播放正常；本轮没有修改 AAC / PCE 原生解码逻辑。Windows 使用上一正式版相同 native 二进制，仅本机临时替代构建脚本，提交前已恢复；正式 Linux 流程仍从源码构建四 ABI。

本地 debug / Android 测试包编译、146 项 JUnit、172 项核心契约和 3 项发行检查通过，debug/release lint 0 错误、42 警告。定向 Android 共 40 项。复测曾暴露只有一条长字幕时，Media3 将外部字幕时长估为最后起点 + 10ms，缓冲释放后拖动会被判为超出结尾；云端字幕解析器现改用实际 cue 结束时间发布时长，保留全部原断言。字幕切档测试核对实际暂停位置，避免固定起点。修复后本机 40 / 40 项 Android 回归通过；正式 Linux 门禁结果以本版 Actions 及发行附件为准；原失败日志仍保留在本机 .local-tests/025。

WebDAV 发布增加与 GitHub 完全同名、相同字节的 APK，保留哈希 APK 和旧 JSON 入口让 0.2.4 客户端升级。仅写授权升级目录，未操作 NAS 媒体或服务、未连接手机。新增界面尚未经过实体设备用户验收。详见 [0.2.5 说明](RELEASE-0.2.5.md)。

以下为历史记录。

# 2026-10-15 发布已核验

GitHub 已保留 v0.2.1 原包，并发布 v0.2.2（源码 9c439cf，versionCode54）为正式 latest。公开 `/releases/latest` 实际跳转 v0.2.2；通过 latest 下载的 BUILD_INFO、SHA256SUMS 及 APK 完整字节与本地一致。APK 26340399 字节，SHA256 `db1003971dee7a48b9e3230088a34d0e8d7926d377d09fbf842a0d87d78cf32a`。WebDAV latest.json 同步选择0.2.2 /54，远端完整回读校验一致，0.2.0及0.2.1历史包保留。API30 发布后再次执行官方版本检查/下载校验2/2通过，无自动安装到实体设备。官网线上10文件、响应式与原生截图验证记录见 WEBSITE-DEPLOYMENT。

# 2026-10-15 官网与 GitHub latest 更新

0.2.1 / 53 已以原 WebDAV 字节发布 GitHub（标签 v0.2.1，源码 a02e493）。0.2.2 / 54 修正应用内网页下载固定到官方 `/releases/latest`，原生版本查询读取 `latest/download/BUILD_INFO.txt`，仍不使用 REST API；保留严格 TLS、重定向边界、哈希/包身份/签名验证。新增数字正式版发行工作流，默认 latest，拒绝覆盖标签/资产。

官网已在既有 myblog 发布，10 个线上文件 HTTP 200 且完整 SHA-256 匹配；320/390/768/1366 宽度无横向溢出，外观与绿联步骤展开已验证。原生页面截图由独立模拟器测试生成，原创媒体数据和海报；未读取真实媒体。README 已增加官网、绿联说明与截图。

0.2.2 debug/release、Android 测试包构建成功；141 项 Gradle 单测、172 项核心契约、3 项发行检查通过，lint debug/release 各 0 错误/40 警告。API30 实连 GitHub 检查与下载校验 2/2 通过（首次验证时 latest 为 0.2.1，安装版本 0.2.2，因此下载后正确拒绝降级安装）；原生演示截图 1 项通过。此前 0.2.1 的 31 项定向 Android 测试证据保留，本轮未重跑完整历史 TV 套件，未进行实体 TV 播放验收。

额外执行旧 bootstrap 工具测试在 Windows 下 16 项通过、1 失败、1 错误：ZIP 反斜线样例在 Windows 规范化后未触发预期拒绝，以及当前用户无创建符号链接权限；未禁用或跳过测试。该工具用于新仓库初始化，未参与本次既有项目发布；Linux CI 仍要求运行。源码/XML/Python sanity 检查通过。证据位于 `.local-tests/0.2.2-*`，截图与部署记录位于 `.local-tests/website-*`。

# 2026-10-15 SunnyTV 0.2.1 已同步 WebDAV

版本0.2.1 / 53，在当前整合入口 `D:/Documents/ChatGPT/SunnyTV-transcode-main` 继续开发，保留原0.2.0及旧目录覆盖层。媒体来源添加按钮常显完整名称，清除配置使用垃圾桶；连接表单移除内部类型切换和外链，首次连接在独立入口选择服务，说明可用遥控器上下阅读。

绿联新增名称升降序、评分、年份、时长、播放日期、大小、比特率和随机排序；名称升序沿用服务端分页，其它先读取完整目录再本地处理。总数/重复/不完整分页、10000项、8Mi字符、90秒和取消保护，缺少字段置后、全缺时提示，不写回NAS。工具说明保留在原工具行lazy item内，不改变海报网格索引。

MediaIndex云端功能复用条件已用HTTP测试核对：保留原始`/api/play/{assetToken}`时可通过sourceMediaUrl识别并查询档位，绿联token不发送到MediaIndex。真实绿联先前样本只有最终115 CDN直链，原始授权入口已丢失，当前样本的云端档位仍需入口映射/接口支持，不能宣称已完成真实联调。未读取NAS的STRM文件、提取凭据或修改MediaIndex。

核心172/172、Gradle单测139/139、发行检查脚本3/3、API30定向31/31通过；debug/release构建及lint通过（各0错误/40警告）。UI检查涵盖常显标签、首次类型选择、无内部切换/外链、说明/字段遥控、排序菜单及工具行到首海报导航，保留首页、云端/字幕/缓存/更新回归。真实绿联排序及TV播放仍待用户验收，完整历史TV失败清单保留。

沿用既有签名与安装ID，非debuggable发行包通过身份/内容/四ABI解码库及许可证检查；已用分发脚本更新WebDAV并全文回读校验哈希及大小，旧包保留。包`SunnyTV-v0.2.1-efae19468f82.apk`，26340403字节，SHA256 `efae19468f825df4698cf25f62056e8da6f1790499e3c017168b5cc7c2c4f249`。未发布GitHub、未向实体手机安装。详见[0.2.1说明](RELEASE-0.2.1.md)。以下为历史记录。

# 2026-10-15 SunnyTV 0.2.0 已同步 WebDAV

本次在已发布 dev36.4 / 51（基线 `480ccfa`）上整合原 SunnyTV 目录的绿联适配，发行整合入口为 `D:/Documents/ChatGPT/SunnyTV-transcode-main`，本地分支 `codex/ugreen-webdav-0.2.0`。版本 `0.2.0` / 52，从本版起使用纯数字版本号。原目录开发覆盖层保留，不能用旧 dev33 源码替代本版。

新增绿联连接引导及接口能力分支；首页仅“顶部按钮→向下”优先落在第一个继续播放，没有/隐藏续播时仍进入方块海报。海报左右轮换和媒体区返回路径不变。保留现有音频、115清晰度、字幕和原生GitHub更新功能；绿联不提供字幕搜索下载，UI及适配器均限制。

核心172/172、Gradle单测132/132、发行检查脚本3/3通过；debug/release编译、APK及测试APK构建、lint通过（各0错误/40警告）。API30模拟器定向30/30通过：首页入口4项、绿联5项、既有表单2项、原生解码器可用/声道混合3项，以及播放、清晰度、字幕连续性、缓存和WebDAV更新相关16项。本轮未重跑EAC3/DTS合成包解码两项；保留完整TV历史失败及真实设备待验范围，不宣称全套TV/真实绿联进度验收。

非debuggable发行包已核对既有签名 `5e8dcd5e1eee828e064682ba6f8dc7d54dfcf59d3182dd6b427a23d1214d6b00`、安装ID、四种ABI的FFmpeg及许可证、无私有字体/密钥/联调材料。使用 `scripts/publish-webdav-update.py` 发布，`Z:/V1 Tools/SunnyTV-updata/latest.json` 已指向0.2.0/52；远端APK全文回读的哈希/大小与本地一致，所有旧包保留。

升级包：`SunnyTV-v0.2.0-a6ea40209949.apk`，26340403字节，SHA256：`a6ea40209949a0b5cba9e5137302b0c653e5fad29083df7d447b55d8672bfd21`。用户通过“设置→实验升级→检查更新”手动安装；未向实体手机安装，未新建GitHub发行或tag，未修改MediaIndex/NAS服务。详见[0.2.0发布记录](RELEASE-0.2.0.md)。

以下为历史记录。

# 2026-10-14 dev36.4 已发布并同步 WebDAV

最终版本 `0.1.0-dev36.4` / 51，发行源码 `8f949ab184e7cae37a498acf1eeef146d70ce1d2`（PR #14；清晰度/原生更新 UI 为 PR #13）。[GitHub Release](https://github.com/xudong7587/SunnyTV/releases/tag/v0.1.0-dev36.4) 与 [签名发行工作流](https://github.com/xudong7587/SunnyTV/actions/runs/38509938911) 均成功，发行 CI 的 16 项 Android 测试通过。

APK 下载后重新验证官方清单哈希、包身份/非 debuggable 及既有签名。已用 `scripts/publish-webdav-update.py` 同步 `Z:/V1 Tools/SunnyTV-updata`，`latest.json` 选中版本 51；dev36.3 / 50 及全部旧包保留。SHA256：`0cfd22b1ed05d260d5057ba9a1a33b309cb4199a4a13a5498449a43fc4bc6da0`。

发布后再次验证：官方 Atom 订阅最新入口为 dev36.4，模拟器原生 GitHub 检查通过。dev36.3 发布后的 API 403/额度 0 问题已通过移除 REST API 依赖修复；新版读取官方订阅/BUILD_INFO/SHA256SUMS，并保留 APK 签名检查。旧客户端需先通过 WebDAV 或官方 APK 安装此版，才有新入口。未安装实体手机、未修改 MediaIndex/NAS 服务或媒体；TV 实机、真实字幕和 CD2 验收仍待完成。

以下为实施记录。

# 2026-10-14 dev36.4：GitHub 更新 API 限额修复

dev36.3 已发布并同步 WebDAV，但发布后复查发现未登录 GitHub REST API 已返回 HTTP 403，额度为 0；不是应用“已最新”。按用户原有更新修复授权继续追加 dev36.4 / 51，保留原版本。

更新检查使用官方 `releases.atom` 及 BUILD_INFO，包含开发测试版；下载使用官方 SHA256SUMS 校验，不再依赖受限 REST API。保留 TLS、重定向限制、取消及签名/身份检查，增加订阅 DTD/外部实体/深度/大小约束。在同一限额网络下 Android 模拟器实连通过。当前分支 `codex/github-update-feed`，基于 `c20b221`。本地 JUnit 116 项通过，debug/release 编译和 lint 通过（0 错误/40 警告）；更新回归 5 项及 GitHub 实连检查/下载校验 2 项通过。核心接口未改，延续本轮 172 项契约结果。最终发行与 WebDAV 回执在完成后追加。

# 2026-10-14 dev36.3 发布修订

用户已授权推送 GitHub、更新 WebDAV，并修复应用 GitHub 入口。当前分支 `codex/github-update-native`，基于 `44b96b4` / 已发布 dev36.2，版本 50。

清晰度面板仅保留档位。GitHub 入口使用原生页面和官方 Releases API，包含开发测试版，读取 BUILD_INFO 比较当前与最新版本，保留升级包内容及签名校验；网络错误不宣称最新。实连 GitHub 模拟器检查首次网络失败、重试通过。本地核心 172/172、JUnit 116/116、Android 定向 10/10 通过；debug/release 编译和 lint 通过（各 0 错误/40 警告）。GitHub 实连用例不纳入依赖网络可达性的发行 CI，纯筛选/校验测试仍执行。最终签名发行和 WebDAV 回执在完成后追加。详见 [dev36.3](RELEASE-dev36.3.md)。

# 2026-10-14 dev36.2 已发布并同步 WebDAV

发行源码 `04806dd42b360cda87873751af6a156cd38af10e`（PR #12），版本 `0.1.0-dev36.2` / 49。[GitHub Release](https://github.com/xudong7587/SunnyTV/releases/tag/v0.1.0-dev36.2) 与 [发行工作流](https://github.com/xudong7587/SunnyTV/actions/runs/38506001866) 均成功。

发行 CI 的 16 项 Android 测试通过，含原生音频解码及本轮播放/字幕/缓存回归；沿用现有签名密钥，APK 非 debuggable。下载后重新校验清单哈希、签名及发行内容，并通过 `scripts/publish-webdav-update.py` 同步 `Z:/V1 Tools/SunnyTV-updata`，`latest.json` 已指向版本 49，旧包保留。

APK SHA256：`baf4397e1d523453c1c1f0fa3c8dad46abcbf8fb9b4eb874e97cc427b8a8eda2`。本轮未向实体手机安装，也未修改 MediaIndex/NAS 服务；真实字幕服务、CD2 与 TV 实机验收仍待完成。新增主题质感已按用户要求撤回。以下为实施时记录。

# 2026-10-14 dev36.2 发布准备

当前入口 `D:/Documents/ChatGPT/SunnyTV-transcode-main`，分支 `codex/playback-theme-polish`，基于已发布 dev36.1（`ec49f82`）。用户已取消新增质感/配色，授权完成其余修订、发布新版并同步 WebDAV；本轮没有修改 MediaIndex 或 NAS 服务。dev36.1 已于 2026-10-14 发布并完成此前授权的分发/部署，下方未发布描述为历史状态。

本轮实现首帧后 115 自动查询、第三方 STRM 清晰度说明、字幕语义选择跨播放器恢复、首页轮播缓存及手机设置分类宽度。共享字幕模型只归一化 Media3 的数字源索引前缀，保持精确匹配及不擅自选择同语言其它字幕。网络策略和 CD2 接口未变。详见 [dev36.2 发布说明](RELEASE-dev36.2.md)。

本地已编译 debug/release，纯 Kotlin 172/172，JUnit 114/114，debug/release lint 各 0 错误/35 警告。Android API 30 定向 11/11 通过；最终签名发行、WebDAV 回执将在完成后追加。未宣称真实字幕服务、CD2 或 TV 实机验收；保留历史完整 TV 测试失败。

# 2026-10-13 当前开发入口

继续 `D:/Documents/ChatGPT/SunnyTV-transcode-main` 的 `codex/115-cloud-playback`；配套网关在 `D:/Documents/MediaIndex前端/.worktrees/115-cloud-playback`，不要覆盖旧工作区。当前基线为 SunnyTV main `4546c74` 与 MediaIndex main `3409aa6`。详见 [实现、测试和待验收](115_CLOUD_PLAYBACK.md)。

用户先要求完成代码和模拟测试、暂不登录，随后授权优先直连并发布两个项目 Git 版和 SunnyTV WebDAV 更新。当前版本为 dev36 / MediaIndex 0.8.4，最终发行状态查对应 GitHub Release。真实 115 验收仍需要有效登录态；不要擅自登录或部署 MediaIndex NAS 服务。已知限制包括真实响应尚未核实、会话进程内保存、签名流失效需手动重新选档；默认只有清单/密钥过 NAS，视频 CDN 直连，代理需要手动选择。旧发行/实机遗留问题仍保留，以下是历史记录。

# 2026-09-30 dev32接续入口

已基于公开dev29增量加入方向锁定与WebDAV实验升级，取消dev28车联和手动旋转。源码、功能、验证结果见 [RELEASE-dev32](RELEASE-dev32.md)，变更列表仅放 [CHANGELOG](../CHANGELOG.md)。本地核心172/172、JUnit85/85、Android定向5/5，debug/release lint各0错误/31警告；旧完整TV失败仍保留，实体在线升级待反馈。

## 2026-09-27 dev29 审查修复入口

本版修复 dev27 审查的五项问题，不含 Carlink。实现与本地验证见 [SEC-01](iterations/SEC-01-dev29.md)，用户可见变化见 [发布说明](RELEASE-dev29.md)。核心172项、Gradle单测79项、Android定向9项通过，debug/release lint各0错误/28警告。使用非调试release变体，安装ID与既有签名不变；签名发布状态以SEC-01追加记录和GitHub Release为准。下方为历史记录。

# 网页优先开发的补充

当前先按 `docs/WEB_SETUP.md` 建立 GitHub CI。仅当真机/本地工具链需要时转 Codex；不要要求用户现在重开项目。新增计时和初始化器代码详见 CHANGELOG，所有未编译/未联调状态仍有效。

# Codex 接续开发入口

你正在接手 **SunnyTV 0.1.0-dev2**，不是从零开始规划，也不要重新命名为 MediaIndexTV。

## 最短开工顺序

先读根目录 `AGENTS.md`、`docs/STATUS.md`、`docs/DEVELOPMENT_PLAN.md`、`docs/DESIGN_SYSTEM.md`、`docs/BUILD.md`，再查看源码与 `preview/index.html`。`docs/reference/previous-spec.md` 仅存档，新规划优先。

本轮已交付 Android 工程代码、原型、核心测试和构建配置；**没有**通过 Android 编译或真机验收。第一步是建立可信构建，不要直接宣布功能完成。

## 用户已确认的产品选择

名称 SunnyTV；电视原生 Kotlin/Compose/Media3；总首页与媒体库首页分开；原截图只对应单库的沉浸顶部；所有媒体库使用 Emby 原生图片；海报、Backdrop、Logo 和观看进度优先复用 Emby；首要来源 Emby + CloudDrive2；MediaIndex 的 STRM 起播兼容是硬要求。

不使用 WebView，不整体 Fork Moonfin，不复制其尚未审计的兼容补丁，不修改用户 MediaIndex 仓库。本轮没有向 GitHub 创建仓库、提交代码或发起 Actions；源码包就是本地基线。

## 第一轮任务：BUILD-01

1. 检查本机 JDK、Android SDK 和 Gradle 网络，使用 JDK 17 / SDK35 / Build Tools35.0.0 / Gradle8.11.1。当前依赖表在 app/build.gradle.kts，先尝试现有固定版本。
2. 执行 `scripts/build.ps1` 或 `scripts/build.sh`。生成标准 Wrapper；不要伪造 wrapper jar，不要把 SDK/密钥/缓存提交到仓库。
3. 修复 Kotlin、Compose TV、Media3 API 不匹配和 Manifest/R8/lint 问题。保留功能和测试；单项升级必须说明原因。
4. 跑 `testDebugUnitTest lintDebug assembleDebug`。纯核心测试还可单独用 `scripts/test-core.sh` 运行；文档中的 114 项不是 Android 整包测试。
5. 输出 APK、SHA-256、编译日志摘要、测试失败列表、补丁说明。不用模拟器结果声称电视已优化。

## 第二轮任务：PLAY-01

重点读 `source/emby/EmbySource.kt` 与 `feature/player/PlayerActivity.kt`。目前基础直放链路有源码，但完整 DeviceProfile 尚未实现。不要伪造支持全部视频/音频编码；根据实际 MediaCodec/音频输出和媒体字段做协商。服务端禁止直放或需要额外打开会话时要明确处理。

验证 `ErrorCode: null`、多 MediaSources、容器为空、`RequiredHttpHeaders`、外部文本字幕和根相对返回地址。已有 `.strm` 解析器不等于所有服务器 STRM 都已能播放。不要在 Android 直接读取 `/volume...`。

回报开始/进度/停止必须有序，不能把预取元数据当作播放开始，失败不阻塞 UI。已有 SessionEvents 不允许被改回会丢开始/停止的 DROP_OLDEST 队列。

## 第三轮任务：SOURCE-01

使用用户本地配置依次测试：普通 Emby 媒体，MediaIndex /api/play 的 302 与 proxy，CD2 WebDAV 的目录、视频与 STRM。无测试服务时补 MockWebServer，不编造联调结果。

CD2 目前仅 WebDAV；不能在 README 写“原生 CD2 API 已支持”。后续 gRPC 需从真实 Core 版本读取 proto 并单独开发。

## 第四轮任务：TV-01

对照 `preview/` 校准原生页面，尤其总首页和单库首页的层次差异。dev2 已改为胶囊导航，但单库重点海报、过渡与整体细节仍需对齐视觉稿，并经 Android 编译和真机验收。

逐页检查焦点、返回、输入法、面板隔离和屏幕安全区。修复需带 UI/仪器测试，不能只增大动画时长。保留原生媒体库完整图片；库图文字不裁切。

## 本地配置与隐私

在 APK 设置里填用户服务器；不要求用户把密码、完整 STRM Token 发给聊天。测试日志只记录匿名来源类别、状态码和耗时。

真机信息可由 `scripts/device-report.sh` 获取（只读）；型号/Android/ABI仍需核对。不要根据处理器是64位就只构建arm64或改变系统分辨率。

## 每轮汇报格式

- 已修改：模块、文件、行为、兼容性影响。
- 已执行：命令、真实结果、日志/报告位置。
- 已验证：Android编译、Mock测试、模拟器、服务、电视分别说明。
- 仍未完成：具体项与阻塞，不以一个“大部分完成”百分比替代。
- 下一步：一个明确任务，不能擅自扩展成网盘整理或手机平台。

## 可以直接作为新对话第一条消息

```text
请接手当前 SunnyTV 工程，继续开发，而不是重新生成一个新项目。
先阅读 AGENTS.md、docs/STATUS.md、docs/CODEX_HANDOFF.md、
docs/DEVELOPMENT_PLAN.md、docs/DESIGN_SYSTEM.md、docs/BUILD.md，
并打开 preview/index.html 理解整个 TV 界面。

用户已确认：独立 Kotlin/Compose TV/Media3 客户端；Emby 原生库图片与
元数据；总首页、单库沉浸首页、海报墙、详情、云盘、设置风格统一；
Emby + CD2 WebDAV + MediaIndex STRM/302/proxy 为核心。
不要更改 MediaIndex 仓库，不用 WebView，不随意升级全项目依赖。

已有源码尚未完成 Android 整包编译。请先完成 BUILD-01：核对工具链、
生成官方 Gradle Wrapper、编译并修复、执行测试和 lint，输出真实 APK、
SHA-256 和报告。之后推进 PLAY-01 的设备能力与 Emby 协商，再做
SOURCE-01 和 TV-01。不得删除未编译通过的功能来伪造完成。
不要把浏览器原型测试、102 项纯 JVM 测试或模拟器结果说成 TV 验收。
```
