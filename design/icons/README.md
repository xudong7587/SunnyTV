# SunnyTV Symbols

54 个为 SunnyTV 重绘的原创矢量图标，采用 24×24 坐标。参考 iOS / SF Symbols 的比例与视觉重量处理，设置使用三根调节推杆，媒体库使用叠放的媒体卡片。播放、暂停、上下集、跳过和睡眠等采用饱满实心图形，导航和工具根据结构保留留白。

- `sunny-symbols.svg`：可直接打开的整套图标总览，仍然是可缩放矢量。
- `svg/`：54 个独立 SVG，默认 24px，支持 currentColor。
- `sunny-symbols-sprite.svg`：供网页 use 引用的 symbol 合集。
- `sunny-symbols.json`：原始矢量数据，包含线条粗细和实心分层。
- `index.html`：20 /32 /56px 对照预览。

在仓库根目录运行 `python scripts/generate-sunny-icons.py` 同步 SVG 与 Android 的 SunnyIconCatalog.kt；添加 `--check` 可检查生成文件是否一致。原生 LineIcon 和播放器图标使用同一份路径，并按实际尺寸直接绘制，不依赖小 PNG 放大。清晰度、比例与倍速仍显示实际文字值。

图形为本项目自行绘制，没有打包 Apple 字体或导出的 SF Symbols。许可沿用 SunnyTV 的 GPL-3.0-only，详见仓库 LICENSE。

视觉参考：https://developer.apple.com/sf-symbols/
