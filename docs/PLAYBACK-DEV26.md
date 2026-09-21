# dev26 · 播放首帧超时、有界重试与路由诊断

用户实机反馈：一条由 MP（MoviePilot）生成的 STRM 直接播放时报
「读取媒体超时，请检查电视到 Emby 的连接。错误码 2001 · 首帧前读取 · video/mp4」，
异常链为 `HttpDataSourceException → IOException → ExecutionException → SocketTimeoutException`。

## 根因：这个错误码证明失败发生在"响应头还没到"

反编译本地 Maven 缓存里的 `media3-datasource-okhttp-1.9.4` 得到两个事实：

1. `OkHttpDataSource.executeCall(Call)` 用 `call.enqueue(...)` + `SettableFuture.get()` 发起请求，
   `catch (ExecutionException e) { throw new IOException(e); }`，而 `executeCall` 只在 `open()` 里被调用。
   body 读取走的是 `readInternal()` → `InputStream.read()`，失败时链里不会出现 `ExecutionException`。
   所以报错中的那两层包装等于"请求已发出、但响应头没有在预算内到达"。
2. `HttpDataSourceException.createForIOException()` 用 `cause instanceof SocketTimeoutException` 决定
   错误码 2002（连接超时）；这里传进来的直接 cause 是上面那层普通 `IOException`，因此被判成
   2001（`ERROR_CODE_IO_NETWORK_CONNECTION_FAILED`）。这是分类变粗，不代表"不是超时"。
   应用侧 `PlaybackFailure` 是全链扫描，所以文案仍然正确地报超时。

结合当时的实现：全项目共用一套 OkHttp 预算（连接 8 秒 / 读取 25 秒，见 `SafeHttp`），首帧失败没有任何自动重试。
对"服务器先去网盘换直链、再 302 到 CDN"这类来源，25 秒很容易被一次慢启动吃满。

## 本轮改动

1. **播放专用的首次响应预算**。`HttpPolicy` 增加
   `PLAYBACK_CONNECT_TIMEOUT_SECONDS = 15`、`PLAYBACK_READ_TIMEOUT_SECONDS = 60`；
   `SafeHttp` 增加 `playbackClient`，并通过 `scopedClient(scope, playback = true)` 暴露。
   API、图片、STRM 文本仍走原来的 8 / 25 秒。跳转上限 8 次、跨域剥离 Emby/CD2 认证头、
   禁止 HTTPS 降级、统一 UA、有界 TLS 全部逐条不变（新增测试断言）。

2. **首帧前的一次有界自动重试**。新增纯 Kotlin 的 `PlaybackRecovery.shouldRetry(...)`：
   只在"首帧尚未渲染 + 本播放器实例还没用过 + 瞬时传输原因"时允许，`MAX_AUTO_RETRIES = 1`。
   瞬时原因 = 错误码 2001/2002，或链里出现 `SocketTimeoutException` / `ConnectException`；
   DNS 解析失败、TLS 失败、HTTP 4xx/404/403、字节位置越界、解析错误一律不重试。
   Media3 侧由 `DefaultLoadErrorHandlingPolicy` 的 `getRetryDelayMsFor` 返回 1500 ms 延迟完成重试，
   其余情况仍返回 `C.TIME_UNSET`（不重试）。重试期间界面提示「网络读取超时，正在自动重试一次…」。
   手动「重试此播放入口一次」保留不变。

3. **诊断指明是哪一跳**。`PlaybackFailure.describe(...)` 新增两段信息：
   `请求主机 cdn.test:5244（直连媒体源，不是 Emby）` 与 `阶段 等待响应（连接或首字节）`，
   超时后还会显示 `已自动重试 N 次`。主机来自 `HttpPolicy.hostLabel()`，只保留 host 与显式端口，
   路径、查询与签名一律不打印（新增测试断言签名片段不出现）。
   原来的「请检查电视到 Emby 的连接」被删除：失败也可能发生在电视直连的 CDN 上，这句话会误导。

4. **顺带修掉两处 MediaIndex 特指文案**：HTTP 409 提示改为"请检查来源令牌或直链是否已过期"，
   设置页直接播放测试的输入框改为"HTTP 媒体地址（网盘直链或播放入口）"。
   SunnyTV 与 MediaIndex 没有代码耦合，STRM 一律按"文件内唯一一条 HTTP(S) 地址"处理。

## 仍然存在的限制

- 仍然不申请转码（`EnableTranscoding = false`），也仍然不发送 DeviceProfile（`emby/AGENTS.md` 记录的 P0 缺口）。
  Emby 可能对电视解不了的编码也报 `SupportsDirectPlay`，这类失败发生在解码阶段，与本次修复无关。
- STRM 里保存的是过期临时直链时，仍只有一次用户手动重试，没有"重新读取 STRM + 重做 PlaybackInfo"的自动恢复。
- 自动重试只覆盖首帧之前的瞬时传输失败；播放中途的断流仍按 Media3 默认行为失败并提示。
- 放宽的是超时预算，不是安全策略：TLS 校验、跨域头剥离、跳转上限与降级拦截均未放宽。

## 测试证据

- 纯 Kotlin 契约测试 `tests/CoreContractTest.kt`：144 项全通过（dev22 基线 119 项 + 本轮 25 项），
  新增覆盖超时预算、host 标签、重试门槛、阶段命名与路由标签。
- Gradle 单元测试：70 项方法全通过，其中 `TransportTest` 6 项（新增 2 项：播放预算只放宽超时、
  以及延长超时后跨域跳转仍然剥离 Emby 认证头），`PlaybackFailureTest` 8 项（新增 4 项）。
- `lintDebug`：0 错误 / 28 警告（与 dev25 基线逐条一致）；`assembleDebug`、`assembleDebugAndroidTest` 通过；
  APK 签名与仓库既有密钥的公开证书一致。
- 仪器化测试：API 30 TV 模拟器执行 59 项，失败 8 项，与 dev22 基线失败集合逐个同名、无新增；
  清单与逐条原因见 `docs/KNOWN-ISSUES.md`。
- 未执行：实体电视/手机真实播放复测（本轮没有连接真实设备与服务），Linux CI 上的 `scripts/test-bootstrap.py`
  （Windows 本机为 16/18，反斜杠 ZIP 条目与符号链接两项属本机平台限制）。
