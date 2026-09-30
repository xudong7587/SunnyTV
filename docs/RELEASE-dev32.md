# SunnyTV v0.1.0-dev32

手机和平板播放页支持锁定当前方向，躺卧观看时不会随姿态切换。仅保留重新绘制的锁图标。

「设置 → 实验升级」可保存WebDAV目录与账号，通过明确的「保存设置」「检查更新」按钮操作，保存成功弹出提示。自动更新开关开启后，每次启动/进入前台检查下载，并打开Android系统安装确认。包名、既有签名身份和配置数据保持兼容；首次安装来源权限由系统授权。

完整版本记录见 [CHANGELOG](../CHANGELOG.md)，配置与分发格式见 [WebDAV升级说明](WEBDAV_UPDATES.md)。

本地验证：核心172/172、JUnit/HTTP85/85、API30升级相关定向5/5；debug/release lint各0错误/31警告，release/debug/测试APK构建通过。发行APK不可调试，签名证书与既有仓库Secret公开证书一致，NAS同步后的SHA256回读一致。完整TV历史失败保留；真实NAS、手机方向锁定与实体设备安装仍待使用者反馈。

GitHub资产使用与NAS在线升级相同的已验证APK，SHA256 `57a2f6bc5e2368ef1dd3e7d450769f7321b1026e8e1e975bc988ba50e9ef3496`，签名证书SHA256 `5e8dcd5e1eee828e064682ba6f8dc7d54dfcf59d3182dd6b427a23d1214d6b00`。
