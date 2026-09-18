# 第三方依赖与素材

SunnyTV 原创部分采用 [GPL-3.0-only](LICENSE)。第三方组件遵循各自许可证。

本次发布的实际 APK 运行时依赖共计 89 个构件，已按 Gradle 解析版本核对 POM 及父 POM，声明均为 Apache License 2.0。包括 AndroidX / Compose / Media3、Kotlin / kotlinx、OkHttp / Okio、Coil、Guava 等。

- [完整构件与版本清单](docs/licenses/DEPENDENCIES.md)
- [构件内原始许可证与 NOTICE](docs/licenses/THIRD_PARTY_NOTICES.txt)
- Gradle Wrapper 及测试/开发工具保留上游文件中的原始许可证；JUnit、MockWebServer 等测试工具不属于应用运行时。

README 内的太阳标识和界面示意图为本项目原创矢量图。示例片名、海报图形、简介、年份及播放进度均为虚构数据，可由 `scripts/generate-readme-art.py` 重建。它们不是实际媒体截图，也不进入应用作为影视内容分发。

应用运行时展示用户自行连接的 Emby 内容。此次 GitHub 发布没有包含用户真实媒体、账号、截图、签名密钥或网络地址。未复制 Moonfin 或 LumiPlayer 源码和品牌资产。


## User-supplied fonts (dev13)
SunnyTV contains integration slots for three user-supplied fonts. Binary font files are not committed by this change. See docs/FONT-ASSETS.md for names, checksums and the redistribution note.
