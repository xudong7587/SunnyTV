# SunnyTV

**让自己的媒体库，回到大屏。**

独立的 Android TV 原生媒体客户端，统一呈现总首页、各媒体库、继续观看、最新入库、影片详情、云盘和设置。复用 Emby 的原生库图、海报、背景和透明片名 Logo，适配 CloudDrive2 WebDAV 与 MediaIndex STRM / 302 / 代理流。

> **0.1.0-dev2 · 源码开发快照。**
> 源码与网页建仓/构建工具已交付；114 项纯 Kotlin 契约测试、18 项离线初始化安全测试通过。
> **本次交付时尚未运行 Android 整包编译、远程 Actions 或电视播放，没有随包 APK。**
> 网页初始化后的实际编译状态以仓库 Actions 为准。本说明不会因上传而自动变成“全部功能已验证”。

## 首选：继续在网页端开发

阅读 **[网页建立项目与构建](docs/WEB_SETUP.md)**。

用户只需在自己的 GitHub 新建 `SunnyTV` 空项目（可仅带 README），将交付的完整工作流保存为 `.github/workflows/sunnytv.yml`。工作流会校验并展开内嵌源码、提交到本仓库、生成标准 Gradle Wrapper，然后运行测试、lint 和 APK 构建。**已有 Android 工程不会被内嵌快照覆盖。**

不需要把 GitHub Token、Emby 密码、CloudDrive2 密码或签名密钥发给聊天。初始化只允许 `xudong7587/SunnyTV`；不会改 MediaIndex。Android 构建成功后才有 `SunnyTV-test-apk`，失败则保留日志供继续修复。

源码快照本身仍可直接在 PC 构建，不依赖初始化器运行。

## 设计与开发入口

- [整体规划](docs/DEVELOPMENT_PLAN.md) · [统一视觉规范](docs/DESIGN_SYSTEM.md)
- [完成状态与明确缺项](docs/STATUS.md) · [本轮改动](CHANGELOG.md)
- [构建与签名](docs/BUILD.md) · [后续 Codex 交接](docs/CODEX_HANDOFF.md)
- [模块约束](AGENTS.md) · [第三方说明](THIRD_PARTY_NOTICES.md)

## 源码覆盖与边界

已写：Kotlin/Compose TV 总首页、所有媒体库、单库沉浸首页、分页海报墙、详情/季集、搜索、文件浏览、设置；真实 Emby HTTP、播放事件回报；CD2 WebDAV 目录/视频/STRM；Media3、音轨字幕选择、实际首帧回调；鉴权作用域、重定向与 STRM 安全策略。

未完成或未验收：完整 Emby DeviceProfile/转码协商、CD2 原生 gRPC、自动下一集、字幕延迟、CD2 NFO 增量读取、持久媒体缓存、实机焦点与完整性能优化。**源码存在不等于服务联调和电视可用。**

## 私有评审预览

完整版源码附件的 `preview/index.html` 与单文件 HTML 是离线视觉原型，不是 APK 截图，不接收账号、不播放实际视频。总首页和单库沉浸顶部是两个不同层级。

GitHub 初始化内嵌包**刻意排除** `preview/`、评审截图、示意海报和原始参考截图，避免把这些素材当作可再分发的应用资源。Android 界面从用户 Emby 获取图片，不受此排除影响。完整设计文字规范保留在 `docs/DESIGN_SYSTEM.md`。

## 本地构建与离线测试

```bash
# 需要 JDK17、SDK35/Build Tools35.0.0 和网络
bash scripts/build.sh
# 独立核心逻辑测试，只需 kotlinc/JDK
bash scripts/test-core.sh
# 无网络的初始化归档安全测试
python3 scripts/test-bootstrap.py
```

Windows 可运行 `scripts/build.ps1`。固定依赖不自动整体升级。标准 Wrapper 由网页工作流调用 `scripts/generate-wrapper.sh` 生成并提交，不伪造 jar。

包名 `io.github.xudong7587.sunnytv`，测试版追加 `.debug`，不覆盖 Moonfin。项目默认只读，不改变 NAS 整理、删除、STRM 签名或网盘流程。没有复制 Moonfin 代码；任何后续移植先做许可证审计。原创代码发布许可证仍由所有者决定，首次建议建立私有仓库。
