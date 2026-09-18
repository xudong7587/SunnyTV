# SunnyTV 发布签名

2026-09-18 用户明确取消 dev14 证书限制，该要求取代此前固定 dev14 的升级基线。

- 当前安装包 ID 保持 `io.github.xudong7587.sunnytv.debug`；版本号递增，不重置 ID。
- 签名身份来自现有 `SUNNYTV_DEBUG_KEYSTORE_B64` 仓库 Secret。不得提交、回显或下载私钥给聊天；不得更改 Secret 或自动生成替代密钥。
- 发布前使用现有密钥公开证书的 SHA256，运行 `scripts/verify-release-signer.py --expected-certificate-sha256`，验证 APK 签名有效且证书与该密钥匹配。
- 不再要求 dev14 APK 哈希、dev14 证书或从 dev14 保留数据的覆盖升级。
- Secret 缺失、APK 签名无效或 APK 与实际选定密钥不匹配仍停止发布，不采用随机调试密钥兜底。
- 不修改既有 dev14 标签或安装包。证书指纹可写入验证报告；私钥和密码不得写入日志/报告。

旧安装包证书不同的设备不保证覆盖安装；不自动卸载或清数据，不宣称 dev14 覆盖升级已验证。
