# SEC-01 dev29 审查修复

日期：2026-09-27。基线：6eada70。状态：VERIFIED / 已发布。源码提交 `7126885a618aabe1eec179b01aec9c8fc1948367`。

范围：修复审查中的账号搜索隔离、公开包调试、DAV 资源限制、API 总期限、跨页全库排序五项问题。车载候选不进入此分支或发布。

决定与兼容性：[ADR](../decisions/SEC-01-boundaries.md)。Emby/CD2/STRM 继续使用现有协议和凭据作用域；无存储迁移、无新增依赖、无远端文件操作变更。

本地验证（Windows / JDK 17 / Gradle 8.11.1 / SDK 35）：

- `scripts/test-core.ps1`：172/172，通过。
- `testDebugUnitTest`：79 项方法，0 失败。覆盖 Emby/CD2/STRM 原有 HTTP 用例及新增边界。
- `lintDebug`、`lintRelease`：各 0 错误 / 28 警告。
- `assembleRelease`、`assembleDebugAndroidTest`：通过。本地未选择签名密钥，release 为 unsigned，不用于发布。
- API 30 Android 模拟器：`Dev29SecurityTest` 3 项 + `Dev27UiTest` 6 项，9/9 通过。Android SAX 正常拒绝 6 MB / 150 万元素 XML；账号候选隔离与同键缓存更新通过。
- `scripts/test-release-package.py`：3 项通过，包含拒绝 debug、testOnly、错误版本、车载入口、私有字体及密钥的子场景。
- `verify-release-package.py`：实际本地 release APK 身份与非调试校验通过；既有 Media3 1.9.4 数字字体以内容 SHA-256 白名单保留，其它字体禁止。
- `scripts/check-project.py` 和 workflow YAML/空白检查通过。

关键限制：全库大小/码率排序先收齐数据，上限 10000 项、累计 8 Mi 字符、90 秒；服务端分页变化、重复或缺项明确失败。大库初次排序会增加请求量。没有用部分排序替代全库排序。

公开包通过专用 workflow 使用现有仓库 Secret 签名，发布前同时验证签名、安装 ID、版本和调试标志；不存在替代密钥兜底。实际签名/Release URL/下载复核结果在发布后追加。

未执行：本轮没有重跑完整 TV 仪器化集合，既有 6 项历史失败不撤销；没有真实 Emby/CD2/STRM 服务联调、实体电视/手机、Apple 验证。没有宣称其它路线图缺口已完成。

下一任务：继续 FND-01 的完整 TV 已知问题逐项复核。

## 发布与独立下载复核

[Release v0.1.0-dev29](https://github.com/xudong7587/SunnyTV/releases/tag/v0.1.0-dev29) 已于 2026-09-27 发布；[签名发布流水线](https://github.com/xudong7587/SunnyTV/actions/runs/36295332325) 与常规 CI 均成功。云端额外以 Dev29SecurityTest 作为硬门槛通过，未使用 continue-on-error。

重新下载四个 Release 资产后逐个复核 SHA256SUMS；APK 9,995,123 字节，SHA-256 `b8ade02cf411630f7b1bb1a089e7b8c164aaac97d51b6272d3f880e23c41a984`。实际签名证书 SHA-256 `5e8dcd5e1eee828e064682ba6f8dc7d54dfcf59d3182dd6b427a23d1214d6b00`，与仓库选定现有密钥的验证记录及 dev27 实际证书相同。包名、versionCode=29、versionName=0.1.0-dev29、debuggable=false、无 Carlink 入口及私有字体检查全部通过。

API 30 模拟器重新安装官方 dev27 后使用 `adb install -r` 覆盖安装下载的 dev29：Success，firstInstallTime 保持不变，启动 Status: ok，进程存活。`run-as` 返回 package not debuggable（预期拒绝），没有读取任何账号数据。这证明模拟器覆盖升级和启动，不外推为实体电视播放或账号迁移全场景验证。
