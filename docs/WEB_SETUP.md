# SunnyTV：只用网页建立项目并开始构建

适用于 `0.1.0-dev2`。当前聊天交付的是源码与初始化文件；**仓库没有由聊天自动创建，也没有已经成功的 Actions 或 APK**。

## 最少三步

### 1. 建立独立仓库

在 GitHub 的 New repository 页面选择自己的 `xudong7587` 账号，名称填写 **SunnyTV**，建议先选 **Private**，勾选 **Add a README file**，默认分支使用 **main**。暂不添加 `.gitignore` 或 License，避免初始化覆盖已有文件。

仓库预计地址：`https://github.com/xudong7587/SunnyTV`。这里是创建后的目标位置，不表示它已经存在。不要在 MediaIndex 仓库里执行本流程。

### 2. 新建一个工作流文件

在新仓库选择 **Add file → Create new file**，文件名完整填写：

```text
.github/workflows/sunnytv.yml
```

用记事本打开本次交付的 `SunnyTV_网页初始化与构建.yml`，复制**全部内容**到 GitHub 编辑区，然后提交到 **main**。也可在完整源码包中找到相同内容 `.github/workflows/sunnytv.yml`。

注意：不是把源码 ZIP 当作单个文件上传；GitHub 不会替你把普通 ZIP 展开成项目。这一个 YAML 已经携带压缩源码、SHA-256 校验及恢复逻辑。可对照源码附件审查，离线工具 `scripts/test-bootstrap-workflow.py` 会检查其中的真实内容。

### 3. 打开 Actions 查看本次结果

工作流名称为 **SunnyTV initialize and build**。若没有自动运行，选择 **Run workflow**，分支选 main。

成功运行后，源码文件出现在仓库；该次 Actions 的 Artifacts 中有：

- **SunnyTV-test-apk**：真正编译成功的测试 APK、SHA256SUMS.txt、构建提交号。
- **SunnyTV-build-reports**：编译/检查日志和测试报告。
- **SunnyTV-initialization-logs**：有生成 Wrapper 日志时提供。

**只有完整测试、lint 与 assemble 成功才发布 APK artifact。** 如果红色失败，先保留运行链接；有了该仓库读取权限后，可继续在当前聊天检查日志和出修复文件，不需要立即转 Codex。当前连接没有直接提交能力，修复仍需你在 GitHub 保存所提供的文件/补丁。

## 权限、首次运行和安全

初始化 job 只向 `xudong7587/SunnyTV` 的 main 写源码和标准 Gradle Wrapper；普通构建 job 只有 contents:read，PR 不执行写入初始化。使用 GitHub 为工作流自动提供的 `GITHUB_TOKEN`，不用填写或向聊天发送 PAT。

若推送报 403 或仓库策略禁止 Actions 写入，在该新仓库的 Settings → Actions → General 检查 Workflow permissions 和相关策略。只授权本仓库必要的 contents 写入；不要关闭组织/重要仓库的安全规则。受保护 main 拒绝时，采用经审查的普通 Git/PR 导入，而不是强制推送。

现有 `app/build.gradle.kts` 存在时，内嵌源包**不覆盖任何应用源码**；重复运行只做 Wrapper 完整性和当前提交的构建。仅有初始 README 时保留到 `docs/INITIAL_REPOSITORY_README.md`。发现其他已有文件时拒绝初始化，避免误伤。

SOURCE_SHA256SUMS.txt 用于校验内嵌源快照；后续自动生成的 Wrapper 和用户新提交不属于这个初始清单。GitHub 源码包不含 `preview/` 和参考图片，完整设计文字仍保留；应用海报从用户 Emby 获取。

## 常见情况

**初次编译可能发现 API/依赖错误。** 这是要完成的实际开发步骤，不是上传后一定成功；不要为了出 APK 把失败测试、lint 全部关闭。容器没有 Android SDK/Gradle，所以目前没有 Android 编译通过的证据。

**源码先提交，构建后执行。** 即使下载 Gradle 或首次编译失败，已成功提交的源码仍在仓库。工作流不依赖机器人 push 自动触发另一个任务，而是在同一次运行中继续构建明确提交。

**私人仓库的 Actions 额度受你的账号套餐/限制影响。** 额度不足时保持现有预算设置，不为此自动开通付费额度；也不要为了跑构建直接将含私有内容的仓库公开。

**新仓库读取权限**：若聊天里的 GitHub 连接只授权了 MediaIndex，在该连接的 GitHub 仓库授权中加入 SunnyTV 后，才能在此读取私有源码与构建日志。请不要提供账号密码或 Token。

**测试签名**：默认 runner 自动生成临时 debug key，后续 APK 可能无法直接覆盖安装。卸载测试版会丢本地配置。长期测试时由你保存一份标准 Android debug keystore，并可将其 Base64 放到仓库 secret `SUNNYTV_DEBUG_KEYSTORE_B64`；不要提交密钥，也不要发到聊天。正式版另定长期签名方案。

## 何时切到 PC / Codex

现在的页面开发、逻辑测试、源码审查和 GitHub CI 错误分析可以继续留在网页。开始需要通过局域网 ADB 安装到电视、读取真实 `logcat`、做 Perfetto/帧耗时或音视频设备兼容检查时，再转本地开发更合适。无需重做工程；根目录 AGENTS 和 docs/CODEX_HANDOFF.md 已保留交接边界。

## 资料依据

- GitHub 网页创建文件：https://docs.github.com/en/repositories/working-with-files/managing-files/creating-new-files
- 工作流令牌权限：https://docs.github.com/en/actions/tutorials/authenticate-with-github_token
- GITHUB_TOKEN 事件限制：https://docs.github.com/en/actions/concepts/security/github_token
- 工具链固定版本：https://developer.android.com/build/releases/agp-8-9-0-release-notes
