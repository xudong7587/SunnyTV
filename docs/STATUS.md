# SunnyTV 0.1.0-dev2 — 真实开发状态

## 2026-09-17 dev8 更新（当前）

实际字幕轨道与库级优先级分离、演员详情/作品分页、手机竖屏海报背景和可滚动细条已实现。
28 个 JUnit 方法及 Android 构建/lint 通过；设备交互仍待实测。详见 [UI-DEV8.md](UI-DEV8.md)。

## 2026-09-17 dev7 更新

新增三种单集排布、库内返回按钮与 Back/Escape 处理，修正头部向下焦点入口、入库标题按钮位置和行间距。
构建、22 个 JUnit 方法及 lint 通过；11 项设备交互测试编译但未执行。README 已改用完全虚构的原创 SVG 素材，采用 GPL-3.0-only。
详见 [UI-DEV7.md](UI-DEV7.md)。

## 2026-09-17 dev6 更新

TV 焦点与回顶、导航可见性、浅色遮罩、按钮渲染、独立库视图、单集海报及继续播放圆形缩略图已更新。
22 个 JUnit 方法（含 114 项核心检查）和构建/lint 通过；9 项设备交互测试编译但未执行。
4K 虚拟显示器脚本与低内存图片策略已加入。详见 [UI-DEV6.md](UI-DEV6.md)。

## 2026-09-17 dev5 更新

已补充手机播放手势、轮播标题行距、混合库文件夹入口、排序方向合并和五档整体缩放。
20 个 JUnit 方法（含 114 项核心检查）以及构建/lint 通过，APK 已生成，随后按用户明确要求覆盖安装到手机。
用户要求稍后再测手机，因此未运行设备测试。详见 [UI-DEV5.md](UI-DEV5.md)。

## 2026-09-17 dev4 更新

已实现手机竖屏适配、太阳 Logo、固定方形轮播、导航焦点入口、首次 Emby 连接引导，
并处理状态写入与反馈问题。17 个 JUnit 方法（含 114 项核心检查）及构建/lint 通过。
设备测试按用户要求暂缓，dev4 最终包尚未覆盖安装。参见 [UI-DEV4.md](UI-DEV4.md)
与 [通知日志排查](NOTIFICATION-INCIDENT-20260917.md)。

## 2026-09-17 dev3 更新（优先于以下历史状态）

主题、首页轮播、库内视图、详情与设备诊断已增量实现；本机 Android 构建、
13 个 JUnit 方法（含 114 项核心检查）、lint 和 3 项手机 Compose 交互测试通过。
dev3 APK 已安装并补齐手机桌面图标，电脑 1080p 预览已启动。
真实媒体播放、完整视觉验收、长时间闲置及电视高刷仍待验证。
提交、APK SHA-256 和限制见 [UI-DEV3.md](UI-DEV3.md)。

## 2026-09-17 BUILD-01 更新（优先于下方历史交付状态）

现有 GitHub 仓库已接续，`codex/build-01` / PR #1 完成首次真实 Android 构建修复。
代码提交 `709a56a`，Actions 合并验证提交 `94374f2`；run `35180031649` 的
`testDebugUnitTest lintDebug assembleDebug` 全部成功。JUnit 5 个方法通过，其中包含
114 项核心契约检查和 4 个 HTTP 传输测试；lint 为 0 错误、23 警告。
APK 已生成并下载，本机 SHA-256 与 CI 一致。详见 [BUILD-01.md](BUILD-01.md)。

尚未连接电视、安装或联调 Emby/CD2，也未测得实际 4K/144 Hz 能力。
深浅主题、十组重点色、新轮播与媒体 Logo 等新需求尚待后续独立提交；要求见
[LATEST_REQUIREMENTS.md](LATEST_REQUIREMENTS.md)。下方“未编译/无 APK/未建仓”描述属于原始交付历史。

更新：2026-09-17。dev2 是在 dev1 上的增量开发，不是重命名演示壳。原始交付状态见 `history/STATUS-dev1.md`。

## 当前做到哪里

| 部分 | 当前实际状态 | 仍需验证 |
|---|---|---|
| 总首页、所有媒体库、单库沉浸首页、海报墙、详情季集、搜索、云盘、设置 | 有原生源码和统一组件，dev2 改进胶囊导航 | Android 编译、遥控器与视觉真机对齐 |
| Emby 原生库图/海报/Backdrop/Logo、Resume/Latest/NextUp、登录、收藏、播放回报 | 有真实请求逻辑，不使用 HTML 的假数据替代 | 用户 Emby 联调、版本和权限差异 |
| CloudDrive2 | 已写只读 WebDAV 目录、视频、STRM；不是原生 gRPC | 真实 CD2 服务和网络错误 |
| MediaIndex STRM/302/代理流 | 有纯逻辑契约与 HTTP 适配源码 | 实际302/Range/UA/错误码与网盘链路 |
| Media3 播放与轨道面板 | 已写；dev2 做下层焦点隔离 | Android API 编译、Surface、音视频、生命周期 |
| 起播计时 | dev2 区分点击/源解析/交接/引擎首帧；时序计算已测 | 不代表已取得真机首帧数据或已经提速 |
| 网页建仓与构建 | 已生成一次上传的工作流、离线安全测试和说明 | 用户实际新建仓库/上传，随后远程执行 |
| GitHub 远程状态 | 本轮未建仓、未提交、未运行 Actions | 当前聊天连接仅有读取接口 |
| APK | 本交付包没有 APK | `testDebugUnitTest lintDebug assembleDebug` 首次跑通 |

## 本轮实际测试与边界

- `scripts/test-core.sh`：**114 passed / 0 failed**。使用当前可用 kotlinc/JDK 编译并执行不依赖 Android 的源码；见 `core-test-results.txt`。
- `scripts/test-bootstrap.py`：**18 项通过**。校验归档、路径穿越/符号链接、敏感文件、已有仓库保护与 README 备份；见 `bootstrap-test-results.json`。
- `scripts/test-bootstrap-workflow.py`：检查生成的完整 YAML、嵌入的实际 Python/ZIP/SHA256、所有 run 步骤 Bash 语法及可重复恢复；结果见 `bootstrap-workflow-test-results.json`。这是离线工作流验证，不是 Actions 运行。
- `scripts/check-project.py`：源包 XML 和脚本语法；具体数量与结果见 `project-check-results.json`。不证明 Compose/Media3 API 可编译。
- HTML 原型检查的范围仅为离线设计预览，见 `preview-test-results.json`；它不证明任何 Android 接口或电视性能。

没有 Android SDK/Gradle，也无法从本运行环境下载工具链依赖；没有执行 Android 整包编译、Gradle JUnit/MockWebServer 测试、lint、安装、NAS 联调或 TV 性能测试。不能用 114 + 18 的数量推导功能完成率。

## 接下来按此顺序

1. **BUILD-01**：用户上传一次初始化工作流；Actions 提交源码、生成官方 Wrapper、执行完整 Android 检查并保留日志。首轮报错先修，不能绕过测试强发 APK。
2. **PLAY-01**：验证真实 Emby 播放和进度；补完整设备能力、媒体源和转码协商。当前只实现基础直放/流播放路径，不伪报全格式支持。
3. **NET-01**：真实 MediaIndex 302/proxy、Range、续播、CD2 WebDAV、外部字幕；扩充 HTTP 层测试。当前 `TransportTest.kt` 未在 Gradle 执行。
4. **TV-01**：在用户电视测试连续按键、模态焦点、返回恢复、输入法、Surface/休眠、硬解/音轨。Macrobenchmark/Baseline Profile 与真机日志到此阶段再考虑 PC/Codex。

仍有明确缺项：自动下一集与跨季、字幕延迟、默认音轨、MediaSession、持久分页缓存/容量驱逐、账号编辑/重新登录、CD2 原生 API/NFO、特定 HDR/DV/音频直通补丁。不得把已有按钮或设计图写成这些功能已完成。

## 不变约束

不改 MediaIndex；不让播放器执行 NAS 或网盘删除/移动；不嵌入账号、个人地址、网盘 Cookie 或签名凭据。身份信息仅由用户在本机/应用设置输入。内嵌初始化源码排除评审海报及原始截图；发布前还要确定原创代码许可证、签名与依赖声明。
