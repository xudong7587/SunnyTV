# BUILD-01：首次真实 Android 构建与修复

日期：2026-09-17。此轮范围为建立真实构建基线；最新产品需求见 [LATEST_REQUIREMENTS.md](LATEST_REQUIREMENTS.md)。

## 仓库核验

- 本地最初只有未提交过的 Git 目录，没有待保留的源码修改。已连接现有 `xudong7587/SunnyTV` 并 fetch，未使用旧归档覆盖源码。
- 核验时 `main` 与 `feat/themes-hero-carousel` 均为 `d08a7ecdca2161a8b976d86efd9afbea5c4d5ca8`，没有已有 PR。
- 在此基线创建 `codex/build-01`；PR：https://github.com/xudong7587/SunnyTV/pull/1 。未回退分支、未修改 MediaIndex。

## 最小修复

- `3f4139a`：Actions 的 Android SDK 安装显式请求 `platform-tools`，避免 setup-android 默认请求已不可用的 `tools` 包。保留 SDK 35 / Build Tools 35.0.0 安装步骤，并同步工作流模板。
- `709a56a`：播放器从 AndroidX 受限的 `dispatchKeyEvent` override/super 调用迁到公开 `onKeyDown` 回调。已聚焦控件先处理导航键，未处理的播放、快进及隐藏控制层按键由 Activity 处理；未处理事件交给父类。真机遥控行为仍须验收。
- 同一提交修复离线工作流测试硬编码 `python3`：使用 `sys.executable`，允许 Windows 使用正在运行测试的 Python。没有删除测试、屏蔽 lint 或升级依赖。
- 两份网页交接文件正文已读取，需求归档已保存；原文件下载被 Edge 拦截，归档不是原附件副本。

## 已执行

首次修复后 Actions：https://github.com/xudong7587/SunnyTV/actions/runs/35179534826 。

执行命令：`./gradlew --no-daemon --console=plain --stacktrace testDebugUnitTest lintDebug assembleDebug`。

- Kotlin Android 编译成功；JUnit 5 个方法通过，其中一个执行 114 项核心契约检查，另外 4 个为 HTTP 传输测试。失败 0、跳过 0。
- lint 报 3 个错误、24 个警告：3 个错误均针对播放器同一处受限 API，已由 `709a56a` 修复。
- 此失败 run 没有发布 APK。
- 离线初始化安全测试 18 项通过；本机完整工作流离线测试 7 项通过。Windows 执行后者时将 Git Bash 的 bin 加入当前进程 PATH，并设置 `PYTHONUTF8=1`。

第二次完整构建：https://github.com/xudong7587/SunnyTV/actions/runs/35180031649 ，**成功**。

- PR 源分支提交为 `709a56a2a1e107893808fce4ae6d23d213e81b8d`；Actions 实际 checkout 的 PR 合并验证提交为 `94374f297a3c56d5162157f93edfc2ea5e1176fd`，其父提交为 `d08a7ec` 和 `709a56a`。已 fetch 并核对其源码树与分支提交一致。
- `testDebugUnitTest lintDebug assembleDebug` 全部通过。JUnit 5 个方法、114 项内部核心契约检查及 4 个 HTTP 测试再次通过，失败/跳过均为 0。
- lint：**0 errors, 23 warnings**；无禁用检查或基线压制。
- APK：`dist/BUILD-01/app-debug.apk`，13,552,150 字节；已下载并以本机 `Get-FileHash` 对比 CI `SHA256SUMS.txt`，一致。
- SHA-256：`8cc9ebc355d27b69c12fc7be8af2fa720bdcd53b261713fafaf680a95f60d827`。
- 本机完整 CI 日志：`.ci-logs/run-35180031649.log`；JUnit/lint 报告：`.ci-logs/run-35180031649/app/build/`。Actions 产物名为 `SunnyTV-test-apk` 和 `SunnyTV-build-reports`，保留 14 天。
- 构建报告与状态文档随后独立提交；上述验证与 APK 对应的确切代码提交保持如上，文档提交不冒充另一次 Android 构建。

## 未验证与后续

- 电视未连接（`adb devices -l` 无设备），未进行安装、遥控器、Emby/CD2/STRM 实服播放或性能测试。
- 用户报告的面板规格为 4K / 144 Hz；尚无 Android 提供给应用的模式、窗口尺寸和帧时间测量，不代表已达到 4K 144fps。
- 后续按独立提交推进深浅主题与十组重点色、首页轮播/焦点、媒体 Logo、显示能力诊断与高清高刷新率适配。
- 保留 lint 的依赖更新、target API、TV 横屏、横幅向量尺寸、备份规则及 KTX 建议。HTTP 明文允许来自既有 LAN/STRM 兼容配置；应在后续网络验收中继续验证来源鉴权隔离与降级保护，不能将 lint 通过解释为安全或实服验收。
- PR 构建使用临时 debug 签名，后续不同签名 APK 可能需要卸载后安装；不是正式发行或最终性能测量包。
