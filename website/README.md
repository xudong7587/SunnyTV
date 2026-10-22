# SunnyTV 官网

线上：https://sunnytv.dunn.fun:666/ 。纯静态 HTML/CSS/JS，发布到既有 myblog 的 `sites/sunnytv`。

2026-10-22：保留 1.0.0 /73 的产品介绍、SP658E 灯带说明、MediaIndex 图标和 GitHub 入口；所有下载入口继续指向 GitHub Latest。官网仅提供白色和黑色两种主题，首次访问默认白色。网页模拟器固定使用柔软浮雕，支持媒体库、片名搜索、详情、收藏、模拟播放和遥控器操作，不连接真实媒体服务。

## 电影素材

本次使用用户提供的 `Seven_Cinema_Visual_Collections.zip`：七部虚构影片各有完整海报、横版场景与透明片名 Logo。21 张源图按包内清单逐一校验，通过常规 WebP 压缩供网站使用，未重新生成或改画。完整海报展示保留 2:3 构图；应用轮播的窄条是原生交互样式。原始包和解包目录不纳入仓库。

## 原生截图

首页、海报墙及液态玻璃首页来自 Android 14 / API 34 模拟器的真实 Compose 页面。玻璃特写来自同一模拟器内的原生 TopNav 材质演示：以提供的彩色场景作为背景，把未修改的导航组件移到画面中央，再裁出局部。没有后期合成折射或高光。应用发行代码和 v1.0.0 APK 不变。

液态玻璃需要 Android 13 / API 33 及以上、硬件加速、开启高开销主题，且不处于低负载模式；不满足时按原生策略回退清晰默认。网页柔软浮雕模拟器不执行 Android RuntimeShader，也不代表设备上的玻璃性能。

复现：在仓库根目录运行 `python scripts/prepare-website-seven.py`，将公开网站素材复制到被忽略的 Android 测试资产目录；构建 Debug 与 AndroidTest，在 API 34 模拟器以 1920×1080、320 dpi 运行 `WebsiteCaptureTest`。测试只启动本地虚构 Emby 数据服务。截图保存到 Debug 应用私有缓存，可用 adb run-as 提取。

## 发布与检查

白名单共 37 个实际使用的文件，资源先行、首页最后替换，旧文件留本地备份；不删除 NAS 其它文件。已完成四种宽度检查（320 /390 /768 /1366），模拟器交互与主题切换检查；37 个线上文件 HTTP 200 且完整 SHA256 与本地一致。详见 `docs/WEBSITE-DEPLOYMENT.md`。
