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

# 2026-09-30 dev32构建记录

已基于公开dev29增量加入方向锁定与WebDAV实验升级，取消dev28车联和手动旋转。源码、功能、验证结果见 [RELEASE-dev32](RELEASE-dev32.md)，变更列表仅放 [CHANGELOG](../CHANGELOG.md)。本地核心172/172、JUnit85/85、Android定向5/5，debug/release lint各0错误/31警告；旧完整TV失败仍保留，实体在线升级待反馈。

## 2026-09-27 dev29 审查修复入口

本版修复 dev27 审查的五项问题，不含 Carlink。实现与本地验证见 [SEC-01](iterations/SEC-01-dev29.md)，用户可见变化见 [发布说明](RELEASE-dev29.md)。核心172项、Gradle单测79项、Android定向9项通过，debug/release lint各0错误/28警告。使用非调试release变体，安装ID与既有签名不变；签名发布状态以SEC-01追加记录和GitHub Release为准。下方为历史记录。

# 构建、测试和签名

> 2026-09-17：工程已具备 Gradle Wrapper 与实际构建产物。当前执行 `./gradlew assembleDebug testDebugUnitTest lintDebug`（Windows 为 `gradlew.bat`），需要 JDK17/21 和 SDK35。下方首次交付环境限制为历史记录，最新结果以 STATUS.md 为准。

## 当前环境证据

本轮容器有 JDK21、kotlinc1.9.0、Node22和Chromium；没有 Android SDK 和 Gradle 可执行文件。访问 `repo.maven.apache.org`、`services.gradle.org` 的下载尝试出现DNS解析失败。

因此：114项纯Kotlin核心测试已实际编译并运行；Android app整体、MockWebServer/JUnit测试和lint未执行；没有APK。此限制不表示工程天然不可构建，只表示首次验证需要具备工具链的环境；本轮优先使用 GitHub Actions，不要求用户立即转 PC。

## 固定起始版本

| 组件 | 本工程版本 |
|---|---|
| JDK（Android建议） | 17 |
| Gradle | 8.11.1 |
| Android Gradle Plugin | 8.9.2 |
| Kotlin / Compose编译插件 | 2.1.20 |
| compileSdk / targetSdk | 35 / 35 |
| minSdk | 24 |
| Compose BOM | 2025.04.01 |
| TV Material | 1.0.1 |
| Media3各模块 | 1.6.1 |
| OkHttp | 4.12.0 |
| Coil | 2.7.0 |

这是固定开发基线，不宣称所有依赖都是最新版本，组合也尚未通过整体构建验证。首轮遇到AAR元数据/Compose编译/系统API约束时按具体错误调整一个依赖组并记录，不直接全仓升级。暂定自用侧载，不声称满足当前应用商店全部发布政策。

## Windows

安装 Android Studio 的 SDK Platform 35 和 Build Tools35.0.0。配置环境变量，或在根目录创建不提交的 `local.properties`：

```properties
sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
```

执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1
```

脚本尝试发现Android Studio的JBR和本地SDK，下载Gradle8.11.1并验证官方SHA-256，再执行测试/lint/debug打包。若企业代理限制Maven/Google仓库，应修好网络，不关闭TLS校验。

成功后预期路径为 `app/build/outputs/apk/debug/app-debug.apk`；这是预期输出，不是本包已包含该文件。

## Linux/macOS

```bash
export JAVA_HOME=/你的JDK17目录
export ANDROID_HOME=/你的AndroidSDK目录
bash scripts/build.sh
```

可指定任务：`bash scripts/build.sh testDebugUnitTest`。

## 生成标准Gradle Wrapper

本包不带伪造的wrapper jar。bootstrap成功下载Gradle后：

```bash
.sunny-tools/gradle-8.11.1/bin/gradle wrapper --gradle-version 8.11.1 --distribution-type bin
```

Windows：

```powershell
& .\.sunny-tools\gradle-8.11.1\bin\gradle.bat wrapper --gradle-version 8.11.1 --distribution-type bin
```

将官方生成的`gradlew`、`gradlew.bat`、`gradle/wrapper/*`纳入版本控制，配置并验证distributionSha256Sum。之后统一使用Wrapper。

## 独立测试

纯核心，无网络/SDK依赖：

```bash
bash scripts/test-core.sh
```

Windows需已安装kotlinc：`powershell -ExecutionPolicy Bypass -File .\scripts\test-core.ps1`。

核心测试与JUnit编译版本不同；核心通过不代表Compose/Media3引用可编译。测试日志见`docs/core-test-results.txt`。

浏览器原型：

```bash
python -m pip install playwright
python -m playwright install chromium
python scripts/export-preview.py
python scripts/test-preview.py
```

有系统Chromium时可设置 `SUNNY_CHROMIUM` 指向它。结果写入`docs/preview-test-results.json`，并截图到`docs/screens/`。这是HTML测试，不是Android UI测试。

## GitHub Actions

新版 `.github/workflows/sunnytv.yml` 同时承担一次性源码初始化与后续 CI。操作见 [WEB_SETUP.md](WEB_SETUP.md)。当前交付时没有运行过远程 workflow，也没有创建用户 GitHub 仓库。

初始化只在新建仓库展开经 SHA-256 校验的源快照；保存源码后，通过 `scripts/generate-wrapper.sh` 在隔离 Gradle 工程中生成真实 Wrapper，并设置官方分发包的 SHA-256，然后提交。构建明确使用该提交，执行 `./gradlew testDebugUnitTest lintDebug assembleDebug`。下载工具链需要外网，失败日志需保留，不伪造 wrapper jar 或 APK。

完整源包内有离线工作流测试：`python scripts/test-bootstrap-workflow.py`（需 PyYAML）。它可验证 YAML、源码校验值、实际嵌入脚本与重复运行保护，但不证明 GitHub 授权、下载网络或 Android 编译成功。

默认仅debug包。CI临时runner自动生成的debug密钥可能每次不同，导致覆盖安装签名冲突。可由用户创建标准debug测试密钥并以`SUNNYTV_DEBUG_KEYSTORE_B64` secret配置，或固定使用同一台PC签名。密钥不提交源码，不在聊天中传递。

release构建开启R8和资源缩减，但没有配好用户正式签名。正式版需由用户保存并配置长期签名密钥，不能与临时debug密钥混用。首次发布前锁定包名与签名策略。

## 安装与真机采样

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
bash scripts/device-report.sh
```

debug包名是 `io.github.xudong7587.sunnytv.debug`，不会覆盖Moonfin。不要执行全局显示分辨率修改。ABI以`getprop ro.product.cpu.abilist`为准，不能只看CPU型号。
