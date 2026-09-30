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
