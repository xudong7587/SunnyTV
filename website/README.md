# 2026-10-24 功能特色页

当前官网按精致设计、个性主题与字体、Emby/绿联媒体来源、原生播放、STRM大文件、MediaIndex原生云端档位、遥控焦点及灯带展示。每项保留简短说明；原生截图、网页演示和概念配图区分标注。页面保持默认白色/黑白外观，配色示意仅影响自身，不改变官网主题或应用设置。当前公开发行1.0.6，下载使用GitHub Latest；没有恢复完整网页模拟器。

新增资源为features.css、features.js、assets/features/cloud-native.webp，概念图来源和最终生成提示词见[配图说明](assets/features/README.md)。遥控示意可点击或用左右方向键、Home/End操作，边界停止；仅展示焦点，不连接电视或触发真实播放。

当前部署与核验见docs/WEBSITE-DEPLOYMENT.md。以下为历史素材及截图说明。

# SunnyTV 官网

2026-10-23：接入SunnyUI2.1的真实网页背景玻璃，使用同一份无React构建；工具栏和内层焦点保持同心圆角，说明弹窗外框玻璃/内层磨砂、外侧不压暗。网页仍默认白色/黑白主题，没有恢复完整模拟器。正式下载更新为GitHubLatest的1.0.4 /77，说明Android11兼容、灯带防闪/重连和等比裁切，并保留实体电视待验范围。SunnyUI2.1安装包和设计Skill链接指向其正式Release。玻璃组件完整源码与许可随网站提供下载。当前部署与校验见docs/WEBSITE-DEPLOYMENT.md；下面保留旧截图和部署方法，旧Android13门槛仅指此前正式版。

线上：https://sunnytv.dunn.fun:666/ 。纯静态 HTML/CSS/JS，沿用既有 myblog `sites/sunnytv` 托管。

2026-10-22 当前页面按功能组织对应图像：原生首页、海报墙、详情、来源、播放器、字体、性能及灯带设置，另用明确标注的灯光氛围示意与 MediaIndex 功能流程图补充说明。参考 Apple 产品页的图文节奏和留白，使用本项目的图像与文字；没有使用 Apple 产品图片。

官网只提供白色、黑色两种主题，首次默认白色。MediaIndex 图标保留原彩。用户要求暂停网页模拟器，因此首页没有 iframe、遥控器或预览脚本，`preview.html` 也只显示暂停提示；原预览源码保留供以后恢复。

七套原创虚构电影海报来自用户提供的 `Seven_Cinema_Visual_Collections.zip`，21 张源图已按包内清单核验，常规 WebP 压缩后使用；完整封面保留 2:3 构图。素材包和私有媒体不纳入构建。

## 原生截图与本轮图标

应用使用重新绘制的 SunnyTV Symbols：统一的 24×24 路径、实心播放控制、三推杆设置和叠放媒体卡片。原生界面与可分享 SVG 共用 `design/icons/sunny-symbols.json`；生成与检查命令见该目录 README。

玻璃导航只让外壳执行折射，内部图标随后独立绘制；去掉子按钮的重复玻璃与圆形焦点底，减轻背景饱和度、折射强度与高光。特写在模拟器中增加原生绘制密度后截取原始像素，不放大低分辨率截图，也不在后期叠加 SVG 伪造应用画面。普通截图来自 Android14 /API34 原生 Compose 与 Media3 页面，播放的是新素材包场景生成的30秒测试视频。

复现：运行 `python scripts/prepare-website-seven.py`，准备被 Git 忽略的公开海报与本地合成视频（需 OpenCV）；构建 Debug 和 AndroidTest。使用隔离 API34 模拟器、1920×1080、320dpi，运行 `WebsiteCaptureTest`，从 Debug 应用缓存提取 `showcase-seven-*.png`。`glass-detail-hd` 使用2倍原生组件绘制密度，裁切中心见同缓存中的 bounds 文件。真实4K TV framebuffer实验被系统限制为1080p，已放回本地实验归档，不宣称成功取得4K屏幕截图。

1.0.4 的 Android11–12L 使用玻璃兼容模式，Android13 /API33 及以上使用原生着色器；两者都需要硬件加速、允许高开销主题及非低负载模式。条件不足按原生策略回退。官网原生截图仍来自Android14，兼容路径的实体电视效果待确认。GitHub正式发布保留此前已交付APK的原始字节。

## 部署

只将实际引用的25文件发布到授权站点目录，资源先行、首页最后替换；原文件留本地备份，不删除 NAS 其它文件。保留所有下载入口的 GitHub Latest 约定。
