# 0.2.5 起：同名 APK 更新

在「设置 → 软件升级」先选择 GitHub 官方渠道，或开启下方 WebDAV 开关后保存目录与账号。
关闭 WebDAV 不清除已保存配置，但隐藏字段、取消下载并阻止后台自动检查。

客户端通过 GitHub latest 的 BUILD_INFO 确定正式最新版，再直接 GET WebDAV 目录下的 `SunnyTV-v版本.apk`。
不请求 WebDAV latest.json，不需要 PROPFIND/目录枚举权限，也不需要 SHA 清单文件。
只有最新版 versionCode 高于本机才下载；最多 256 MiB，支持取消，保留凭据作用域、严格 TLS 和有限重定向。
下载完成核对 APK 包名、versionCode、versionName 及与本机相同的签名，再交给系统确认安装。
缺少文件、APK 损坏或身份不符会拒绝安装，不会按目录中的任意 APK 降级。

发布仍使用 `scripts/publish-webdav-update.py`：先验证既有公开证书及包内容，再发布与 GitHub 相同字节的正式文件名；
同时保留带哈希的不可变 APK 与 latest.json，让 0.2.4 及更早客户端继续升级。同版本不同字节拒绝覆盖，历史包保留。
GitHub 最新版查询需要联网；WebDAV 密码继续在本机加密保存，不显示到日志。

以下为旧版协议的历史说明。

# WebDAV 实验升级

终端在「设置 → 实验升级」保存完整HTTP/HTTPS目录URL与账号。目录提供固定入口 `latest.json`，例如：

```json
{
  "applicationId": "io.github.xudong7587.sunnytv.debug",
  "versionCode": 32,
  "versionName": "0.1.0-dev32",
  "apk": "SunnyTV-v0.1.0-dev32-57a2f6bc5e23.apk",
  "size": 10012327,
  "sha256": "57a2f6bc5e2368ef1dd3e7d450769f7321b1026e8e1e975bc988ba50e9ef3496"
}
```

APK文件名仅允许目录内的普通文件名；清单64KiB、APK256MiB上限。下载使用统一UA、严格TLS、有界跳转和origin/路径作用域认证；流式SHA256、大小、安装ID、版本与证书检查通过后才提供安装。终端只执行GET，不修改服务端文件。

开启自动更新后，每次启动/进入前台检查并下载，然后调用系统安装器。离开前台取消未完成任务；安装器返回不马上重复检查。首次安装权限须系统授权，授权后手动安装或下次启动应用。厂商固件可能限制未知来源安装，真实NAS与设备需自行验证。

发布已验证的release APK：

```powershell
python scripts/publish-webdav-update.py --apk dist/SunnyTV-v0.1.0-dev32.apk --destination "Z:/V1 Tools/SunnyTV-updata" --aapt .sunny-tools/android-sdk/build-tools/35.0.0/aapt.exe --apksigner .sunny-tools/android-sdk/build-tools/35.0.0/apksigner.bat
```

脚本沿用既有公开证书身份，先检查包名、版本、签名及不可调试/包内容，再复制不可变版本APK并更新latest.json，核对回读哈希。拒绝倒退版本、替换同版本不同内容；保留历史APK。目录须已挂载存在。密钥/密码不进入仓库或升级目录。
