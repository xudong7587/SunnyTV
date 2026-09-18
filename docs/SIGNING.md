# SunnyTV 固定升级签名

2026-09-18，用户明确要求以后沿用 dev14 的签名，不再沿用 dev13。

- 当前安装包 ID 保持 `io.github.xudong7587.sunnytv.debug`；版本号递增，不重置 ID。
- 签名身份来自现有 `SUNNYTV_DEBUG_KEYSTORE_B64` 仓库 Secret。不得提交、回显或下载私钥给聊天；不得更改 Secret 或自动生成替代密钥。
- 参考包：GitHub Release `v0.1.0-dev14` 的 `SunnyTV-v0.1.0-dev14.apk`。
- 参考包 SHA-256：`6f798dae3c4b3d3dd9dbd969859a6e190d49fc642d4fa41fe346eb1325538b04`。
- 所有后续发布必须用 `scripts/verify-release-signer.py` 校验参考包完整性、两份 APK 的签名有效性及证书完全一致。仅提示签名不同仍然发布，不再允许。
- Secret 缺失、参考包获取失败、签名不匹配或验证失败时停止发布，不建议用户卸载/清数据绕过。
- 不修改既有 dev14 标签或安装包。证书指纹可写入验证报告；私钥和密码不得写入日志/报告。

本规则定义升级基线，不表示已经测试所有设备上的覆盖安装。dev15 的模拟器流程额外执行先安装 dev14、写入本地保留标记、用 `adb install -r` 升级到 dev15，再确认标记保留。
