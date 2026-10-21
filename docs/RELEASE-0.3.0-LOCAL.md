# SunnyTV 0.3.0 本地测试包 · 2026-10-21

版本 0.3.0 / versionCode 62。用户明确要求仅本地分发与 USB 手机安装，不提交、推送或发布 GitHub；灯带版另行处理。

## 来源与改动

实际打包目录：`C:/Users/Sunny/.codex/worktrees/ugreen-lan-tls/SunnyTV`，基线 `dcc0940`（0.2.8 / 60），保留本目录既有绿联内网 HTTPS 修复。此前主题实现位于 `SunnyTV-transcode-main`；仅把主题增量经三方合并迁入本目录，未以旧文件覆盖新版网络、来源和播放器。未合入 0.2.9 灯带版。

新增 26 套配色、清晰默认 / 精致描边、独立圆角档位及默认关闭的高开销主题许可。保持原生 Kotlin / Compose TV / Media3、现有页面与焦点逻辑、图片预算和性能模式。当前没有加入玻璃材质；高开销开关不会凭空增加玻璃选项。详见 UI-SUNNY-2-LIGHTWEIGHT.md。

## 验证与边界

- 代码已实现；release、debug、androidTest 编译通过。
- 核心契约 172/172，Gradle 单元测试 166/166，发行校验脚本测试 3/3。
- API30 模拟器定向 Android 回归 11/11：主题 4、主页导航、Banner间距、播放器焦点、真实 Android 绿联自签名 TLS 登录/浏览/图片/Range 播放。
- release lint：0 错误，app 37 警告、decoder 4 警告；未禁用检查。
- APK 签名有效，与已安装 0.2.8 的公开证书一致；包名、0.3.0 / 62、非 debuggable/testOnly、无私钥和未批准字体检查通过。
- Windows 构建继续使用已核验的 0.2.8 FFmpeg 原生静态库，本轮未重编 FFmpeg。没有实体 TV 性能或真实 NAS 播放验收结论。

## 分发

仅复制 APK 到 `Z:/V3 Download/tv apk/SunnyTV-v0.3.0.apk`，回读 SHA256 一致；未写 NAS JSON、未同步旧目录、未执行 WebDAV 发布。没有覆盖历史包。

SHA256：`7bd5fa6d8b835e223814db92c239c120b95402c1d4c95f2e44d8a219f53dc1a6`

USB vivo V2454DA 经用户确认安装后覆盖升级成功（adb install -r / Success），系统包信息核实 0.3.0 / 62；未卸载或清除数据。使用完整 MainActivity 类名启动成功，进程存活。实体电视性能与真实内容播放仍待用户验收。
