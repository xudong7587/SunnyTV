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
