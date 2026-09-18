# dev13 内置字体资产

用户提供的三份 TTF 已在本轮检查内部名称及 OS/2 fsType。三者 fsType 均为 8（Editable embedding 标记）。

目标文件名：

- `app/src/main/assets/fonts/fz_zhenghei.ttf` — 方正正准黑简体 — SHA-256 `a92f243f92a8af7b1b9b0f31782b965bd9882a7331e88b10d3b2100d0941fd94`
- `app/src/main/assets/fonts/fz_youhei.ttf` — 方正悠黑简体 512B — SHA-256 `2050762a1c6478d1ef85208013cd2d72cf3248c8f50b02ef91ec3d4570a0f03a`
- `app/src/main/assets/fonts/coca_cola_care.ttf` — 可口可乐在乎体 文本细 — SHA-256 `2c1075fddb3445501e9f7b3fd4ed01c796f2ac90cffe55059b45003ad3701192`

应用在资产缺失时不会崩溃：字体菜单显示“待内置”，Theme 回退系统 Sans Serif。资产加入后无需再改 Kotlin。

注意：fsType 是字体文件里的嵌入技术标记，不等于完整的公开再分发许可证。仓库为 public；提交原始 TTF 或把它们随公开 APK 分发前应确认相应授权。
