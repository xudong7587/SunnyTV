# 2026-10-22 官网 1.0.0 内容更新已上线

用户最新要求先改官网内容，并认为本轮海报不合意。本次只发布内容与对应排版：1.0.0 /73、标语「精致入眼，流畅入戏。」、外观与播放修订、SP658E控制及适用范围、MediaIndex图标和GitHub链接、继续GPL开源说明、FAQ。图像沿用既有页面展示素材，本轮生成的7张海报、7张横版背景及新截图不发布。

仅部署首页实际引用的10个文件到既有 `Z:/docker/myblog/sites/sunnytv`，先写资源、最后替换首页；原文件备份于 `.local-tests/website-100/content-before`。每个线上文件HTTP200且完整SHA256与本地一致，320/390/768/1366实际iframe视口无横向溢出，下载链接保留GitHub Latest。证据 `.local-tests/website-100/content-http-verified.json`，线上截图 `live-content.png`。不修改应用发行包、NAS媒体或MediaIndex仓库，不重启服务。

以下为历史部署说明。

# SunnyTV 官网部署

线上入口：https://sunnytv.dunn.fun:666/ 。源码 `website/`，现有 myblog 发布器的 `sites/sunnytv` 静态目录，不新增托管服务。

本次采用更明确的首屏、大幅原生截图与分段产品说明，沿用 Sunny UI 外观令牌和许可。首页、海报网格、浅色主题截图来自 API30 模拟器内真实 Compose 页面，媒体名与海报为原创测试素材，截图方法见 `website/README.md`；不发布私有媒体、邮件附件或设备签名。

部署通过用户授权的现有挂载目录 `Z:/docker/myblog/sites/sunnytv`，先写资产再原子替换首页，旧文件在本地 `.local-tests/website-before-redesign` 备份，不清理 NAS 其它目录。线上文件需要逐一 HTTP 200 及完整 SHA-256 比对；网页不需要重启容器。反代沿用既有规则。

所有下载入口固定使用 GitHub `/releases/latest`，实际版本由发行的 latest 标记决定。
