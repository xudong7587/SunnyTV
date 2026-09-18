# SunnyTV dev14：发布验证与升级限制

记录日期：2026-09-18。

## 已发布

- 版本：`v0.1.0-dev14`，GitHub Prerelease（开发测试版），不是稳定版发布承诺。
- 实际构建源码：`15b3804d06cdcb55c5c3070c65cd78f21154eb4d`。
- APK：`SunnyTV-v0.1.0-dev14.apk`，14,173,585 字节。
- APK SHA-256：`6f798dae3c4b3d3dd9dbd969859a6e190d49fc642d4fa41fe346eb1325538b04`。
- 版本号：`versionCode=14`；应用 ID：`io.github.xudong7587.sunnytv.debug`。
- Release：https://github.com/xudong7587/SunnyTV/releases/tag/v0.1.0-dev14
- Actions：https://github.com/xudong7587/SunnyTV/actions/runs/35314038292
- 报告归档：该次运行的 `SunnyTV-dev14-validation` artifact。

发布标签及 APK 保持与上述已验证提交对应；本文件是后续文档记录，不表示 APK 经过重新构建。

## 已执行并通过

`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` 实际运行成功；并非只有源码或语法检查。

Android 10 / API 29 / x86_64 模拟器实际执行 `HomeHotfixTest` 的全部 5 项测试，0 失败、0 跳过：

1. 连续向下进入四个懒加载媒体区，并向上恢复媒体库卡片。
2. 从第三个媒体库进入该库内容，返回同一媒体库卡片。
3. 下一集区域保持可达，首个媒体库为空时向下不会困住焦点。
4. 横向遍历九个媒体库入口并返回，检查焦点卡片两端留白。
5. 关于页保留 SunnyTV 版本及功能说明，不显示无关项目介绍。

模拟器采用 1920×1080、density 240；测试固定为减少动画模式，以确定性验证焦点与布局。不能据此宣称已验证真实电视流畅度、动画效果或全部影视播放兼容性。

本轮未连接用户电视、Emby、CloudDrive2 或网盘。播放、网络和 MediaIndex 契约代码未改动；dev13 的纵向 4K MP4 实机结论仅作为历史记录保留，并非本轮复测。

## 重要：当前 APK 与 GitHub dev13 签名不一致

构建步骤确实使用了仓库配置的 `SUNNYTV_DEBUG_KEYSTORE_B64`。随后使用 Android `apksigner` 比较本版与 GitHub Release 中 `SunnyTV-v0.1.0-dev13.apk` 的证书 SHA-256，结果是 `sameSignerAsDev13=no`。

因此不能把这个 APK 当作可直接覆盖 GitHub dev13 的无损升级包。没有比对用户电视上可能自行构建的其他安装包，不能从上述结果推断它们的签名。

请保留旧安装及其配置，不要为了安装新包而先卸载、清数据。需要无损覆盖时，应由持有 dev13 原签名密钥的可信本地/CI 环境重新构建或重签同一份 dev14 代码，核对证书后再分发。只拿旧 APK 的公开证书无法代替原私钥。

本轮没有更改仓库签名 Secret，没有输出或提交私钥，签名差异的具体来源尚未确认。不要把密钥或密码发进聊天、Issue 或 Git 仓库，也不要生成新密钥冒充原签名。不要静默替换已发布的标签和 APK；后续兼容签名包应明确标识并单独校验。
