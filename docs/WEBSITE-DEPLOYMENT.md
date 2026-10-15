# SunnyTV 官网部署

线上入口：https://sunnytv.dunn.fun:666/ 。源码 `website/`，现有 myblog 发布器的 `sites/sunnytv` 静态目录，不新增托管服务。

本次采用更明确的首屏、大幅原生截图与分段产品说明，沿用 Sunny UI 外观令牌和许可。首页、海报网格、浅色主题截图来自 API30 模拟器内真实 Compose 页面，媒体名与海报为原创测试素材，截图方法见 `website/README.md`；不发布私有媒体、邮件附件或设备签名。

部署通过用户授权的现有挂载目录 `Z:/docker/myblog/sites/sunnytv`，先写资产再原子替换首页，旧文件在本地 `.local-tests/website-before-redesign` 备份，不清理 NAS 其它目录。线上文件需要逐一 HTTP 200 及完整 SHA-256 比对；网页不需要重启容器。反代沿用既有规则。

所有下载入口固定使用 GitHub `/releases/latest`，实际版本由发行的 latest 标记决定。
