# SunnyTV 官网

线上：https://sunnytv.dunn.fun:666/ 。纯静态 HTML/CSS/JS，沿用 Sunny UI 外观变量和许可，采用更明确的产品排版与原生界面展示。

`assets/home.webp`、`library.webp`、`light.webp` 来自 Android 原生 Compose 页面实际截图。片名、简介与海报全部原创；`scripts/generate-showcase-art.py` 生成几何插画，`WebsiteCaptureTest` 在模拟器内通过本机测试服务加载，完全不连接用户媒体库。截图压缩为 WebP，没有重绘或改动原生界面。

所有下载入口固定指向 GitHub `/releases/latest`，不固定版本或 APK 文件名。部署到现有 myblog 的 `sites/sunnytv`，无需构建或新建托管服务。部署详情见 `docs/WEBSITE-DEPLOYMENT.md`。
