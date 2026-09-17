# SunnyTV 0.1.0-dev1 — 交付状态与已知缺项

更新：2026-09-17。此文件用于区分“写了源码”和“验证可用”；接手后按真实结果更新，不能仅删除“未验证”字样。

## 已交付内容

| 部分 | 源码状态 | 本轮验证 |
|---|---|---|
| 原生总首页/媒体库列表/单库首页/海报墙/详情季集/搜索/云盘/设置 | 已写主页面和统一组件 | 未完成Android编译，未TV验收 |
| Emby用户登录、Views、Latest、Resume、NextUp、图片、收藏、PlaybackInfo、回报 | 已写真实HTTP逻辑 | 未接用户Emby；网络JVM测试待运行 |
| CD2 WebDAV目录/视频/STRM | 已写真实PROPFIND/GET及只读约束 | XML策略已测试，真实CD2未联调 |
| 共享STRM/URL/重定向/作用域策略 | 已写 | 纯逻辑测试通过；真实HTTP栈测试待运行 |
| 播放事件排序/心跳合并 | 已写SessionEvents和串行reporter | 纯事件顺序测试通过，Emby实际回报待验收 |
| Media3播放器/OSD/轨道选择/首帧诊断 | 已写 | 未Android编译、未实际播放 |
| Keystore、共享图片缓存、取消旧请求、删除来源隔离 | 已写 | 未Android生命周期/硬件验证 |
| 整体离线HTML视觉原型 | 已写，可本地打开 | 12项浏览器检查通过 |
| Gradle配置、构建脚本、CI、AGENTS、规划和交接文档 | 已交付 | 脚本/XML基础检查通过；Android CI未运行 |

## 实際执行过的检查

1. `bash scripts/test-core.sh`：**102 passed / 0 failed**，用kotlinc1.9.0/JDK21编译6个纯逻辑源码文件及测试，结果见`core-test-results.txt`。
2. `python scripts/test-preview.py`：**12项通过**，使用Chromium的离线HTML原型，不是Android界面。
3. `python scripts/check-project.py`：**13项源包检查通过**，范围包括XML、Python/Bash/JavaScript语法及APK目录未混入演示海报/字体。不包含Kotlin Android依赖解析。

原型首次使用file://自动化导航时遇到受管Chromium阻止本地URL；测试脚本已改为将原型和图片内联到临时HTML再set_content，重跑后12项通过。

## 未执行的验证

没有Android SDK、Gradle及可下载依赖的DNS环境，未执行`assembleDebug`/`lintDebug`/`testDebugUnitTest`。`TransportTest.kt`四项及`ContractSuiteTest.kt`JUnit桥接已写但没有在Gradle中跑过。未安装APK，未联系用户NAS，未修改GitHub仓库或运行远程CI。

**源码包里没有APK。不能说“已经编译完成”“已经秒开”“已经比Moonfin更流畅”。**

## 下一位开发者必须面对的缺项

| 优先级 / ID | 具体缺项 | 接手位置与验收方向 |
|---|---|---|
| P0 BUILD-01 | Android整包首次编译、lint、依赖组合/资源/API/R8验证；生成官方Wrapper | app/build.gradle.kts、scripts、BUILD.md |
| P0 PLAY-01 | 完整设备能力与Emby DeviceProfile/媒体源/转码协商 | EmbySource.playback、PlayerActivity；不能虚报全格式支持 |
| P0 NET-01 | 真实302/Range/代理流/字幕URL/根相对反代路径兼容 | TransportTest、SafeHttp、StrmResolver；补测401/403/409/416与TLS |
| P0 TV-01 | 真机焦点、模态面板隔离、输入法、Surface/生命周期 | feature/ui和feature/player；全程不用鼠标 |
| P1 PLAY-02 | 自动下一集/跨季、字幕延迟/默认轨道、MediaSession | 当前仅手动选集、轨道选择，不含这些高级能力 |
| P1 CACHE-01 | 媒体页/目录缓存的容量驱逐、持久化和进程死亡恢复 | AppModel/ConfigStore；不能无限保留所有浏览数据 |
| P1 UX-01 | 原生胶囊导航、单库主海报突出、平滑背景过渡与原型对齐 | 原型是目标，不是当前原生截图 |
| P1 UX-02 | 完善加载/空/错误状态、来源标识、筛选、搜索翻页 | 当前只有基础筛选查询能力和部分错误反馈 |
| P1 AUTH-01 | 账号编辑/重新登录与更严谨的凭据失效恢复 | 当前提供添加/移除/重置，需要扩展编辑路径 |
| P2 CD2-01 | CloudDrive2原生gRPC | 目前没有生成proto或RPC客户端；只能称WebDAV模式 |
| P2 META-01 | CD2同目录NFO、海报和字幕增量读取 | 不在启动时全盘扫描，不自动联网刮削 |
| P2 PERF-01 | 从点击播放到首帧的全链路计时、Macrobenchmark、Baseline Profile | 当前首帧不含上一页解析；不能直接对照总起播耗时 |
| P2 CODEC-01 | HDR/DV/音频直通/刷新率匹配及样本回归 | 未移植Moonfin兼容补丁，未内置第二播放引擎 |

其他限制：当前Emby海报墙查询主要为Movie/Series，不等于全媒体类型客户端；进度回报遇到网络失败只有失败计数、没有离线重传；重试按钮重试当前播放入口，并非通用刷新所有过期直链；严格降级/路径作用域策略可能要求用户修正反代配置。公开发布前还需选择原创代码许可证、核对依赖及移除无再发布授权的评审素材。
