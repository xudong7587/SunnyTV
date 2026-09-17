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
