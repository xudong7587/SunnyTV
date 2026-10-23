# SunnyTV 1.0.1 本地测试包 · 2026-10-22

版本 1.0.1 / versionCode74。用户确认整套新图标并要求在原 tv apk 目录提供覆盖安装包；本轮仅本地交付，未新建 GitHub Release、标签或推送此版本变更。既有1.0.0与历史APK保持不变。

## 内容

- 采用已确认的54个SunnyTV原创矢量图标：三推杆设置、叠放媒体卡片、饱满的播放/暂停/上下集及统一工具图标。原生界面与SVG共用路径。
- 液态玻璃只让外层导航壳折射，内部按钮扁平，取消圆形焦点底，减轻饱和度、折射和高光。
- 保留1.0.0灯带、绿联、Emby、CloudDrive2、STRM、字幕及云端清晰度功能；未修改网络、数据模型或原生音频代码。

## 验证

192项Gradle单测、172项纯Kotlin契约通过；Debug/Release完整lint均0错误/44项既有警告。release与release AndroidTest编译通过，未关闭门禁。

隔离API34模拟器先安装原目录公开1.0.0 /73，再以`install -r`升级最终原始签名1.0.1 /74；未卸载或清数据，firstInstallTime不变。最终包11项原生回归通过：全54图标在20dp/40dp无裁切、玻璃透明/取色/材质4项、播放面板焦点、比例恢复、灯带生命周期、设置2项和实际视频PixelCopy取色。图标与玻璃测试使用正式声明的MainActivity宿主，可验证非debuggable发行包。实体电视视觉与灯带效果由用户安装后测试。

签名有效且匹配既有1.0.0公开证书 `5e8dcd5e1eee828e064682ba6f8dc7d54dfcf59d3182dd6b427a23d1214d6b00`；安装ID继续`io.github.xudong7587.sunnytv.debug`，non-debuggable /non-testOnly，四ABI与许可证齐全，无私钥、未批准字体或网页评审素材/测试视频。

Windows CMake本次链接的ffmpegJNI库与Linux正式1.0.0有字节差异。为让这次纯UI更新沿用已验证的解码器，最终包直接保留原目录1.0.0 /73的8个公开原生库（四ABI），源码与该正式提交的decoder-ffmpeg无差异；其它DEX、资源和Manifest与本轮release编译产物逐字节一致。重新做16KiB页对齐并使用Gradle已选定的原密钥签名，最终再次验证签名/身份/原生库哈希。构建、原生来源与验证证据保存在`.local-tests/101`，私钥和密码未进入报告或源码。

## 交付

`Z:/V3 Download/tv apk/SunnyTV-v1.0.1-74.apk`

大小 29,376,511字节，SHA256 `9b300553d6f0acac84959b567bd114a4aeddb447222990b82828483b3a541344`。NAS回读哈希一致；只写入此新APK，不写NAS JSON、不发布WebDAV、不覆盖历史同版本文件。最终本地原包`.local-tests/101/SunnyTV-v1.0.1-74.apk`。
