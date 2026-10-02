# SunnyTV dev34.3

按用户反馈改为电视本机解码原音轨。基于 dev32，未修改 dev33。

- 内置 Media3 1.9.4 官方 FFmpeg 音频扩展，启用 AC3、EAC3（含 JOC 的基础音频）、DTS/DTS-HD、TrueHD 解码，输出 PCM。优先用于这些格式，视频继续硬件解码。
- 移除自动申请 Emby 音频转码及相关提示。
- 保留音轨和字幕选择、字幕搜索下载、片尾修复。

构建包括四种 Android ABI；CI 在 Android 模拟器检查原生库和合成 EAC3、DTS 六声道音频解码为非静音 PCM。真实剧集、电视扬声器和音响效果仍需实机验证，不能保证 Atmos/DTS:X 对象音频透传。

扩展来源：androidx/media 1.9.4，提交 75ccb55ec085d76cbbf12e2f1af8241d378a753a（Apache-2.0）。FFmpeg n6.0.1，提交 c41ff724ede7da657762d61097e26fac296c53bf，仅音频解码（LGPL-2.1-or-later，未启用 GPL/nonfree）。源代码、静态库和 JNI 重链接材料随发行提供；APK 内包含许可证。

使用现有签名，versionCode 37。云工作区无 WebDAV 升级目录挂载，未同步。
