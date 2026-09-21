# SunnyTV 0.1.0-dev2 — 真实开发状态

## 2026-09-21 dev26 更新（当前 · 公开版）

按用户实机反馈修"首帧前读取超时"。报错为 `读取媒体超时，请检查电视到 Emby 的连接 · 错误码 2001 · 首帧前读取 · video/mp4`，异常链是 `HttpDataSourceException → IOException → ExecutionException → SocketTimeoutException`，出问题的媒体是别人用 MP（MoviePilot）生成的 STRM（与 MediaIndex 无关）。反编译本地 Maven 缓存里的 `media3-datasource-okhttp-1.9.4` 确认两件事：`OkHttpDataSource.executeCall` 用 `call.enqueue(...)` + `SettableFuture.get()` 取响应、`catch ExecutionException` 后包成普通 `IOException`，而它只在 `open()` 里调用 —— 所以这个链等于"请求已发出、响应头没在预算内到达"，不是读 body 中断；错误码 2001（而非 2002 连接超时）只是 `createForIOException` 的 `cause instanceof SocketTimeoutException` 看不到那层包装，分类变粗。当时全项目共用 8 秒连接 / 25 秒读取，首帧失败没有任何自动重试。

改动：①播放传输单独放宽首次响应预算（连接 8→15 秒、读取 25→60 秒，新增 `SafeHttp.playbackClient` 与 `scopedClient(scope, playback=true)`），API、图片、STRM 文本维持 8/25 秒，跳转上限 8 次、跨域剥离 Emby/CD2 认证头、禁止 HTTPS 降级、统一 UA、有界 TLS 全部逐条不变；②首帧前对瞬时传输失败（2001/2002 或链含 `SocketTimeoutException`/`ConnectException`）自动重试一次（新增纯 Kotlin `PlaybackRecovery`，`MAX_AUTO_RETRIES=1`，Media3 侧 `getRetryDelayMsFor` 返回 1500 ms，其余情况仍返回 `C.TIME_UNSET`；DNS、TLS、HTTP 4xx、越界与解析错误不重试），重试期间显示"网络读取超时，正在自动重试一次…"；③诊断新增"请求主机 host:port（Emby 本机 / 直连媒体源，不是 Emby）"、"阶段 等待响应（连接或首字节）/ 读取数据流"与"已自动重试 N 次"，只打印主机与端口，路径、查询与签名不出现，并删掉会误导的"请检查电视到 Emby 的连接"；④顺带去掉 HTTP 409 与设置页直接播放测试里的 MediaIndex 特指文案。详见 `docs/PLAYBACK-DEV26.md`。

验证：纯 Kotlin 契约测试 144/144（dev22 基线 119 + 本轮 25 项）；Gradle 单元测试 70 项方法全通过（`TransportTest` 6 项含新增 2 项、`PlaybackFailureTest` 8 项含新增 4 项）；`scripts/check-project.py` 通过；`assembleDebug`、`assembleDebugAndroidTest` 与 `lintDebug` 通过（0 错误 / 28 警告，与 dev25 逐条一致）；API 30 TV 模拟器 `connectedDebugAndroidTest` 59 项失败 8 项，与 dev22 基线失败集合逐个同名、无新增。发布 APK `dist/SunnyTV-v0.1.0-dev26.apk`（14,436,245 字节，SHA256 `74a36eecc5f180c9a620599ea4ff882aaefd4ea56571c47cb62025acaa4ffc33`）由仓库既有密钥签名（证书 SHA-256 `5e8dcd5e…`），未新建密钥、未替换既有标签或资产。Windows 本机 `scripts/test-bootstrap.py` 仍为 16/18（反斜杠 ZIP 条目与符号链接两项属本机平台限制，Linux CI 上通过）。实体电视与真实 MP/Alist 播放未复测，本轮不宣称已解决服务端取流慢。

**公开版已发布**：GitHub Release `v0.1.0-dev26`（源码提交 ea07c77），资产 `SunnyTV-v0.1.0-dev26.apk`
14,436,245 字节、SHA256 `74a36eecc5f180c9a620599ea4ff882aaefd4ea56571c47cb62025acaa4ffc33`，
签名证书 SHA-256 `5e8dcd5e…`（仓库既有密钥，未新建密钥），另附 `SIGNING_VALIDATION.json`、`BUILD_INFO.txt`
与 `SHA256SUMS.txt`；从发行页重新下载后复核 SHA256 与签名均与本地一致。
本版安装包在维护者本机（Windows / JDK 17 / Gradle 8.11.1 / SDK 35）构建，未新增 dev26 发布工作流，
以免与已发布的标签重复发布；常规 CI 仍在每次推送 main 时执行。

## 2026-09-21 dev25 更新（公开版）

以 dev24 快照为基线，按用户反馈只改手机 / 平板（触摸设备）媒体库与文件夹视图里 banner 与媒体区之间的空隙；电视端按用户确认"没问题了"，取值逐值不变。新增 `BannerMediaGapTest` 在固定尺寸容器里量「轮播媒体块底边 → 工具行顶边」：修复前手机横屏、手机竖屏、电视都是 122dp（= 18dp banner 底边距 + 22dp 网格项间距 + 82dp 工具行上内边距）。其中 82dp 是电视端"整屏切换"需要的顶栏内边距（`enterTools()` 把工具行整行对齐到视口顶端，靠它把标题/按钮留在顶栏下方、上方不露 banner），而触摸设备是自由滚动，这段就是纯空白。修复：触摸设备工具行上内边距改为 8dp、`LibraryHero` 底部留白 18→10dp（电视分别保持 `pageTopPadding` 与 18dp）；`enterTools()` 与文件夹模式返回工具行的落位偏移由 `pinnedTop` 改为 `toolsTopInsetPx`（电视上两者逐值相同，触摸设备上落在 8dp，接遥控器按下键时内容仍不会跑到顶栏底下）。结果：手机横屏 40dp、手机竖屏 40dp、电视 122dp。

验证：`BannerMediaGapTest` 通过（手机横屏/竖屏断言 ≤45dp，电视断言 =122±0.5dp 即几何未变），并导出三种形态截图；整包仪器化测试 59 项失败 8 项，与 dev22 基线失败集合完全一致（无新增）；单元测试 64 项通过；`check-project.py` 通过；`lintDebug` 14 error / 28 warning 与 dev22 逐条一致。发布 APK `dist/SunnyTV-v0.1.0-dev25-self.apk`（16,256,704 字节，SHA256 `2C357663B980EC71C54C18D3D7BE95C0074C10272AE4BF36E06A85BCF39BC273`）由仓库既有密钥签名，未新建密钥。手机 `10AF4B15MG001YJ`（vivo）在锁屏状态下会被设备端拒绝安装（`INSTALL_FAILED_ABORTED: User rejected permissions`），用户解锁后覆盖安装成功（`versionName=0.1.0-dev25`，`lastUpdateTime=2026-09-21 15:12`），启动无 FATAL 异常。详见 `docs/UI-DEV25.md`。

本次同时完成公开版清理：`app/src/main/assets/fonts/` 下开发期间临时内置的第三方字体、`FontCatalog` 中的
对应条目与常量、设置页字体说明里的自用措辞、`app/build.gradle.kts` 的字体 `noCompress` 项全部移除，
`scripts/check-project.py` 改为「仓库内不得出现任何 `.ttf/.otf/.ttc`」。字体选项只保留系统默认与用户自定义上传，
既有的 `fontChoice` 归一化逻辑会把旧的自用字体 id 回退为系统字体，不会因缺字体导致启动失败。

**公开版已发布**：GitHub Release `v0.1.0-dev25`（源码提交 776fb24），资产 `SunnyTV-v0.1.0-dev25.apk`
16,256,704 字节 → 14,435,701 字节（去掉内置字体后），SHA256 `3e3861cab2fdbefe58c7a47ca20a89ed6aaa2a53a725204445c58036bd5a1825`，
签名证书 SHA-256 `5e8dcd5e…`（仓库既有 Secret，未新建密钥），并附 `SIGNING_VALIDATION.json`、
`BUILD_INFO.txt`（`bundledFonts=none`）与 `SHA256SUMS.txt`。发布工作流与常规 CI 均通过；
工作流里额外断言了"APK 内不得出现字体文件"，并在公开版清理时顺带修掉 dev22 起累积的 14 项 lint 报错
（`PinyinIndex` 的 ICU 转写加 API 29 守卫——旧设备上原本可能抛 `NoSuchMethodError`、Media3 预览改用
`androidx.annotation.OptIn`、播放器 `dispatchKeyEvent` 定点抑制），`lintDebug` 现为 0 错误 / 28 警告。
仪器化测试本轮只记录不作为发布门槛，8 项滞后断言清单见 `docs/KNOWN-ISSUES.md`。

## 2026-09-21 dev24 更新

以 dev23 快照为基线，修用户反馈的最后一处手感问题：媒体库（含文件夹内部）从媒体区按「上」回到 banner 时会"卡一下"。用新增的 `BannerReturnMotionTest` 停住时钟逐帧采样工具行位置，量出修复前是两段动画——`99,62,35,18,10,6,2,1,1,0,0` 后接 `173,296`，即第一段衰减到完全停住再重新起步。根因：工具行按「上」走的 `GridFocusNavigator.move(0)` → `revealItem(0, alignTop=true)`，而 `revealItem` 在目标项不在可见列表时只能按"当前可见首行高度"逐次滚动，banner 恰好是占满视口的整项、回到它时整项都在视口上方，所以必然先滚一段、停住、再滚一段。现改为与已验收的"banner → 媒体区"同一套写法：新增 `leaveToolsForBanner()`，一次 `animateScrollToItem(0)` 回到 banner 自己的偏移，焦点在并行协程里逐帧重试，全过程 hold 住视口并在结束后多保持两帧；`beforeMove(0)` 一并改为单次滚动；文件夹模式从文件夹行回到工具行改为一次 `animateScrollToItem(1, pageTopPadding)` 落位，不再依赖 hold 释放后的补滚动。

验证：`BannerReturnMotionTest` 在 dev24 通过（逐帧位移 `398, 286` 后工具行离开视口，单段单调减速；返回后 `library-carousel:featured` 顶部与进入媒体区前一致 ±1dp，焦点回到 banner 区，工具行不再显示），把同一份测试拿到 dev23 源码上运行**失败**并复现该停顿，可证明用例有效；整包仪器化测试 58 项失败 8 项，与 dev22 基线集合完全一致（无新增）；单元测试 64 项通过；`check-project.py` 通过；`lintDebug` 14 error / 28 warning 与 dev22 逐条一致。发布 APK `dist/SunnyTV-v0.1.0-dev24-self.apk`（16,256,708 字节，SHA256 `003DBB65AB05F88F361DEA0956B737AC80085BFB81A9FB4D6A753C9AFBFC972C`）由仓库既有密钥签名，未新建密钥。手机 `10AF4B15MG001YJ` 已覆盖安装（`versionName=0.1.0-dev24`，`lastUpdateTime=2026-09-21 14:00`）并启动无 FATAL 异常。首页两侧切换按用户确认未改动。详见 `docs/UI-DEV24.md`。

（未公开的私有构建曾临时内置第三方字体，已在 dev25 的公开版清理中移除。）

## 2026-09-21 dev23 更新

以 dev22 快照为基线，只做用户截图指出的两处细节修复。①**聚焦外框的角包裹不完全**：`FocusTile` 原本把轮廓整体缩小一个描边宽度但保留圆角半径，圆角弧心比卡片弧心内偏 `stroke/2`，沿角平分线描边外沿落在轮廓内侧约 0.4dp，浅色海报上就是一道角缝；现新增 `concentricInsetOutline()`，平移轮廓的同时把圆角半径减去同一量，描边与卡片轮廓一圈同心同宽。②**首页选中阴影被分区阻拦**：柔和阴影画在卡片外（横向 13.4dp、下方偏 3.6dp），而承载行会裁剪自身边界，之前横向留白为 0，最左媒体库卡片与最左折叠媒体块左侧的阴影被整条切掉（实测边框旁就是纯背景色）；现在阴影留白集中在 `FocusElevation.kt`（左右 14dp、上 12dp、下 18dp），折叠行视口由 183dp 调整为 195dp，首页「我的媒体库」行、每个媒体库的推荐行、`MediaShelf("接着看下一集")`、媒体库工具栏按钮排、详情页演员表圆头像行与云盘目录列表按同一套数值内缩，外层内边距相应减回，标题与第一张卡左边缘仍为 30dp。

验证：新增 `FocusFrameVisualTest`（4 项）在 API 30 TV 模拟器上渲染真实首页/媒体库页面并导出 PNG，像素级确认最左媒体库卡片与最左折叠媒体块左侧由"紧邻边框即纯背景"变为平滑阴影，卡片左边缘仍为 60px（30dp）；同时断言聚焦外框轮廓与卡片圆角同心。整包 `am instrument` 57 项：失败 8 项，与 dev22 基线在同一模拟器上的 8 项失败集合完全一致（无新增失败），其中两项已用 dev22 源码单独复现确认为既有失败；另三处旧断言按新布局把期望值由 0dp 更新为 14dp 留白，保留原意图。详见 `docs/UI-DEV23.md`。

本轮验证汇总：JUnit / 纯 Kotlin 单元测试 64 项全部通过；`scripts/check-project.py` 通过；`lintDebug` 14 error / 28 warning 与 dev22 基线逐条一致（均为既有项，未新增）；发布 APK `dist/SunnyTV-v0.1.0-dev23-self.apk`（16,256,708 字节，SHA256 `01BE4DD6969381DA867232D9CA2F0DE6AD4AB26F5C76C9DDB40F547BCDBD071B`）由仓库既有密钥签名（证书 SHA-256 `5e8dcd5e…`），未生成替换密钥。手机 `10AF4B15MG001YJ` 在 13:44 已覆盖安装 dev23 并启动无 FATAL 异常（该次安装为清理未使用 import 之前的同一份源码）；清理 import 后重新构建的最终 APK 再次覆盖安装时，手机端安装确认被拒绝（`INSTALL_FAILED_ABORTED`），因此最终包是否已装入手机需用户自行确认，本记录不将未完成的安装计为通过。

（未公开的私有构建曾临时内置第三方字体，已在 dev25 的公开版清理中移除。）

## 2026-09-20 dev22 更新

以 dev21 快照为基线做十项体验优化：手机（含横屏）改用单一可触摸滚动结构，修复横屏下滑拉不起「我的媒体库」；媒体库推荐栏换成与首页相同的一大块+小条块随机轮播，左侧按钮不变，并把首页/媒体库 Hero 的渐变后移让背景图更完整；首页与媒体库按上到顶/滑到顶可刷新轮播，使用播放器同款品牌 loading；播放进度条常显已播放圆点，电视可聚焦后长按左右连续快进快退并显示标记时间，手机取消中三分之一限制并可拖动圆点定位；字体并入外观（系统默认字体 + 用户自定义上传），新增跟随主题色的字幕外观设置；长按确定/长按触摸打开刷新元数据、刷新媒体库、删除菜单（删除需二次确认）；媒体来源下移到性能下方；品牌 logo 加浅色聚焦阴影；手机顶部按挖孔安全区下移；遥控菜单键直达设置，手机非交互区域滑动模拟方向键。

验证：纯 Kotlin 契约测试 119/119（基线 114 + 字幕外观 5 项）；`scripts/check-project.py` 通过；Android `:app:assembleRelease` 成功，APK 已用仓库既有密钥签名（证书 SHA-256 5e8dcd5e…）并覆盖安装到用户手机，启动无异常；API 30 TV 模拟器执行仪器化测试 53 项，48 通过，5 项为既有遗留断言/超时。实体电视与手机手感仍需人工验收。

验收第一轮（同日）按用户逐条反馈修订：媒体库推荐栏与首页同排版（宽屏媒体块在按钮右侧、下对齐、58% 宽度；窄屏在下方并留出间距）；详情页改成媒体库页那种固定全屏背景；修复首页「按上到顶刷新」不生效的两处原因（顶栏为兄弟节点收不到按键、轮播持有焦点时不换样本）；字幕外观加入播放器同源实时预览并合并为一张卡片；品牌阴影只作用于 logo 与文字本身；按结论移除手机滑动模拟方向键。

验收第二轮（同日）按真机截图反馈修订：媒体库轮播与工具栏之间固定留白（宽屏 30dp、竖屏 56dp），细条媒体块不再压住标题与工具按钮；顶部整体下移只在手机竖屏生效（此前 TV 预览虚拟显示的 smallestScreenWidthDp=540 被误判为手机），横屏与电视恢复原高度；左上角 logo 去掉看起来像圆形按钮的柔光；媒体库轮播改为 1 大块 + 6 细条并与首页共用左键跳出逻辑；首页顶栏按上直接刷新轮播（不再依赖 moveFocus 失败判定）。

（未公开的私有构建曾临时内置第三方字体；这些字体已在 dev25 的公开版清理中移除，仓库与发布包均不含字体文件。）

详见 `docs/UI-DEV22.md`。

## 2026-09-19 dev21 更新

以用户真机实测 dev20 为基线继续局部优化。TV 首页 Hero 改为完整占满视口，并与“我的媒体库”首屏同时预布局；Hero 向下不再滚动 LazyColumn，而是只做两个已渲染全屏层的 GPU 平移。首页最多预取 6 个媒体库的 latest 10 条数据，避免进入下方行时再首次请求。相邻媒体行纵向导航改为先交接焦点、同步开始滚动，TvAccordion 本地视觉选中状态与 FocusRequester 同帧更新，缩短展开和 Artwork 过渡。播放器快退/快进改为环形 10 秒箭头，倍速仅显示数值，子菜单关闭图标取消聚焦文字浮层。

纯 Kotlin 核心测试 114/114 通过；Android XML/Python/资源静态检查通过。当前容器缺少 Android SDK 且 Gradle 分发下载不可用，未宣称完成 Android assemble；实体电视需由用户本机编译 dev21 后继续验证。详见 `docs/UI-DEV21.md`。

## 2026-09-18 dev16 更新

最新发布签名指示：用户明确取消 dev14 证书限制，以仓库现有签名 Secret 发布；此要求取代下方历史记录中的 dev14 固定校验和覆盖升级门槛。保留签名有效性及 APK 与实际选定密钥的公开证书一致性检查，不生成或替换密钥，不宣称与 dev14 可覆盖升级。详见 docs/SIGNING.md。

dev16 已合并 main 并发布：https://github.com/xudong7587/SunnyTV/releases/tag/v0.1.0-dev16 。发布源码 e19b1e349f1c23101e49f5fc82545bd23a61cc31；云端编译、单元测试、lint、签名和 Android 模拟器交互验证通过。使用仓库原有 Secret，证书 SHA256 为 5e8dcd5e1eee828e064682ba6f8dc7d54dfcf59d3182dd6b427a23d1214d6b00。发布 APK 下载后已核对 SHA256：374eb085c532d5b1f71f797cc622e4d8691d90dc234e3f30f82975cab3138fae，并再次验证签名有效且匹配现有密钥。Gradle 发布构建显式指定现有 keystore 路径，缺失时失败；没有生成替换密钥或更改 Secret。

本轮整体检查网格跨行焦点：海报墙、媒体库总览、演员作品和剧集数字/竖向列表使用共用导航器，先滚动并等待目标 lazy 项完成布局，再请求焦点；保留列位置并覆盖不完整末行及分页返回。工具栏可下移进入海报内容。延迟自动聚焦执行前重新检查焦点记忆，避免抢回用户已经移开的焦点。

首页每个媒体库增加稳定的排序按钮，循环“最新入库 / 最新上映 / 随机”；最新上映和随机通过现有 Emby 适配器对完整库执行服务端排序，保持库作用域及 10 项上限。切换取消旧请求，避免旧响应覆盖新排序，并重置媒体展开位置；各库选择独立保留于当前 AppModel 生命周期。通用焦点效果不再改变位置或缩放，移除对应动画及 graphicsLayer，保留原有颜色、阴影与描边。Banner 海报墙及媒体库封面均居中 Crop 铺满。

用户追加授权本版更新到 Git；源码提交到 dev16 分支并建立 PR，此授权取代本段下方历史记录中的“仅本地、不推送”要求。不触发既有 dev15 发布工作流，不替换旧标签或资产。后续公开 APK 发布仍必须遵循 docs/SIGNING.md 的 dev14 固定签名校验，本地既有 debug 安装不能作为公开发布签名凭据。

最终验证：Android 编译、APK/测试 APK 构建、51 项 JUnit 方法（包含既有 114 项核心契约检查及 Emby HTTP 排序检查）、lint（0 错误 / 28 警告）及源码/XML/脚本检查通过。API 30 模拟器执行全部 47 项交互测试通过。竖屏首次连接测试保留原有可见性断言，补充等待 Android Dialog 完成窗口放置；修正旧测试中的 14dp 留白及新增排序前的标题焦点预期。

已知验证限制：Windows 本机 bootstrap 安全测试为 16/18 通过，反斜杠 ZIP 条目在 Windows 写入时已转换，符号链接测试缺少系统权限；未改动初始化器、跳过测试或将该结果记作全通过。实体电视、真实视频方向切换和倍速音视频同步仍待实机验证。本轮最终包未装入手机：ADB 仅检测到 QA 模拟器，先前手机已断开。

文件夹横向展开补充跨行焦点路径：工具栏下移到首个文件夹标题，标题下移到展开媒体，媒体下移到下一文件夹，上移逐级返回；空文件夹及错误/空状态按钮可继续向下，分页按钮可上移返回末尾文件夹。文件夹模式切换按钮改为稳定标识。最新包编译、50 项单元测试、lint 和 8 项相关模拟器交互回归通过，包含文件夹跨行及空行、字体大小、首页五项和实际播放器子菜单恢复。

按追加要求移除播放器右上角退出按钮；保留底部工具栏退出，播放失败时在错误提示内保留退出入口。

按用户追加要求，媒体库 banner 由 Fit 改为居中 Crop 铺满卡片，允许裁切边缘以消除两侧留白；此要求覆盖原始“库图完整 Fit”偏好，仍复用 Emby 原图。

剧集竖向列表的缩略图追加圆角矩形裁切：卡片外圆角 20dp、图片内圆角 8dp、内缩 12dp，同心圆角关系为 20−12=8；继续使用既有剧集原图，不改图片来源。

追加修正：字体大小五个按钮可向下滚动并进入下一项字体设置；首页从媒体库及 NextUp 向下按页面区域顺序浏览，不再跳转选中媒体库的最新入库，向上返回仍恢复原库选项。本轮 Android 构建、50 项单元测试、lint 和 6 项模拟器交互测试通过（HomeHotfixTest 五项及字体大小五按钮下移一项）。首页单元测试已依据用户新要求更新原先“按选中库跳转”的预期。

后续六项界面修正：设置重点色在视口边缘使用显式滚动及逐行焦点转移，手机最后一个不完整色卡行仍可访问；顶部固定导航关闭焦点上浮；演员表与相似推荐可向下及向上转移焦点；播放器子菜单退出恢复到打开菜单的按钮，快速返回也可恢复；深黛蓝交换预览左右色块；浅色设置未选中条目使用重点色浅色的不透明底色。详情剧集入口去掉重复焦点请求器绑定，并滚动到实际首个下层控件。

最终包的 Android 构建、50 项 JUnit 方法、lint（0 错误 / 28 警告）通过。SunnyTV_QA_API30 模拟器执行 6 项相关交互测试全部通过，包含重点色连续下移、演员表到相似推荐往返、详情操作入口、顶部导航下移、播放器控件和实际 PlayerActivity 子菜单恢复；子菜单测试仅使用本地受控响应，不访问用户媒体服务。实体电视及真实媒体播放仍不属于这次仪器验证。

按用户要求仅在本机修改并安装，不提交或推送 Git。播放器移除 Manifest 固定横屏：手机竖屏视频按 Media3 显示尺寸进入竖屏，其他手机视频跟随设备方向；TV、大屏与外接预览显示器保持横屏。底部控制按钮从 54dp 缩至 48dp，间距缩至 2dp，控制区下移；手机按钮支持横向滚动。增加 1x / 1.25x / 1.5x / 2x 倍速选择和显式退出播放，播放错误时也可退出。倍速保持于本次播放器生命周期及下一集切换，退出播放沿用既有停止回报与位置保存。

Android 编译、50 个 JUnit 方法（包含现有核心契约检查及新增 2 个方向策略方法）和 lint 已通过；真实媒体的方向切换、倍速音视频同步及实体电视视觉仍需验证。没有复制 Moonfin 源码。按用户本轮明确要求继续使用 dev10 之前的本机 debug 签名，未生成或替换密钥。

已知限制：手机竖屏视频当前限制在两个竖屏方向；手机控制按钮超过屏宽时横向滚动。V2454DA 上单项控件仪器测试未完成：ActivityScenario 请求 ComponentActivity，但日志收到 MainActivity 生命周期，持续等待；已停止测试并恢复手机主屏的应用，未将该测试记作通过。不将编译通过当作 TV 验收。

## 2026-09-18 dev13 更新（当前）

用户已确认原本本地纵向 4K MP4 播放问题已经解决，并完成真实设备播放验证。dev13 完成深浅主题焦点、字体选择、详情页及播放器控制布局调整；播放器按钮与控制区已重置，补充章节、字幕、音轨、演职员、睡眠、播放信息、片头/片尾跳过及下一集提示。字体保持“系统默认 / 用户自定义上传”，APK 与公开仓库不内嵌第三方字体文件。


## 2026-09-18 dev12 候选

已针对实机反馈提交焦点阴影/描边、首页 D-pad 焦点路径和 MP4 错误 2000 的定向修复。Media3 由 1.6.1 升至 1.9.4，并仅在首帧前检测到 video/mp4 + UnexpectedLoaderException + IndexOutOfBoundsException 时启用一次忽略 edit list 的兼容重试。该重试不启用转码、不修改媒体文件、不无限重试。

本段在 PR 创建时只代表源码已修改；CI 结果与真实电视复测需要分别记录，不能据此宣称故障 MP4 已修复。


## 2026-09-17 dev11 更新（当前）

恢复深浅主题焦点描边、增加平移阴影开关、优化首页下移与慢速动画、最新入库退出收起回位和标题整体露出；播放控件改为圆形图标与聚焦提示。
39 个 JUnit 方法（含 114 项核心契约检查）、27 项 TV 模拟器交互测试通过；lint 0 错误、28 警告。播放错误 2000 按用户要求暂缓排查，未宣称修复。详见 [UI-DEV11.md](UI-DEV11.md)。

## 2026-09-17 dev10 更新

TV 首页两段焦点导航、横向纵轴锁定和协调宽窄海报已实现；页面/主题/图片过渡统一使用可调速度，新增自定义字体、五档字号及剧集/季的从头播放与继续播放。
36 个 JUnit 方法（含 114 项核心契约检查）、23 项 TV 模拟器交互测试通过；构建通过，lint 0 错误、27 警告。实体电视及真实媒体播放性能仍待验证。详见 [UI-DEV10.md](UI-DEV10.md)。

## 2026-09-17 dev9 更新

修复 TV 来源表单输入法确认与遥控器焦点：逐项跳转、可见确认按钮、纵向字段及始终显示的保存按钮。
28 个 JUnit 方法、Android 构建与 lint 通过；13 项交互测试编译但未执行，当前无连接设备。详见 [UI-DEV9.md](UI-DEV9.md)。

## 2026-09-17 dev8 更新

实际字幕轨道与库级优先级分离、演员详情/作品分页、手机竖屏海报背景和可滚动细条已实现。
28 个 JUnit 方法及 Android 构建/lint 通过；设备交互仍待实测。详见 [UI-DEV8.md](UI-DEV8.md)。

## 2026-09-17 dev7 更新

新增三种单集排布、库内返回按钮与 Back/Escape 处理，修正头部向下焦点入口、入库标题按钮位置和行间距。
构建、22 个 JUnit 方法及 lint 通过；11 项设备交互测试编译但未执行。README 已改用完全虚构的原创 SVG 素材，采用 GPL-3.0-only。
详见 [UI-DEV7.md](UI-DEV7.md)。

## 2026-09-17 dev6 更新

TV 焦点与回顶、导航可见性、浅色遮罩、按钮渲染、独立库视图、单集海报及继续播放圆形缩略图已更新。
22 个 JUnit 方法（含 114 项核心检查）和构建/lint 通过；9 项设备交互测试编译但未执行。
4K 虚拟显示器脚本与低内存图片策略已加入。详见 [UI-DEV6.md](UI-DEV6.md)。

## 2026-09-17 dev5 更新

已补充手机播放手势、轮播标题行距、混合库文件夹入口、排序方向合并和五档整体缩放。
20 个 JUnit 方法（含 114 项核心检查）以及构建/lint 通过，APK 已生成，随后按用户明确要求覆盖安装到手机。
用户要求稍后再测手机，因此未运行设备测试。详见 [UI-DEV5.md](UI-DEV5.md)。

## 2026-09-17 dev4 更新

已实现手机竖屏适配、太阳 Logo、固定方形轮播、导航焦点入口、首次 Emby 连接引导，
并处理状态写入与反馈问题。17 个 JUnit 方法（含 114 项核心检查）及构建/lint 通过。
设备测试按用户要求暂缓，dev4 最终包尚未覆盖安装。参见 [UI-DEV4.md](UI-DEV4.md)
与 [通知日志排查](NOTIFICATION-INCIDENT-20260917.md)。

## 2026-09-17 dev3 更新（优先于以下历史状态）

主题、首页轮播、库内视图、详情与设备诊断已增量实现；本机 Android 构建、
13 个 JUnit 方法（含 114 项核心检查）、lint 和 3 项手机 Compose 交互测试通过。
dev3 APK 已安装并补齐手机桌面图标，电脑 1080p 预览已启动。
真实媒体播放、完整视觉验收、长时间闲置及电视高刷仍待验证。
提交、APK SHA-256 和限制见 [UI-DEV3.md](UI-DEV3.md)。

## 2026-09-17 BUILD-01 更新（优先于下方历史交付状态）

现有 GitHub 仓库已接续，`codex/build-01` / PR #1 完成首次真实 Android 构建修复。
代码提交 `709a56a`，Actions 合并验证提交 `94374f2`；run `35180031649` 的
`testDebugUnitTest lintDebug assembleDebug` 全部成功。JUnit 5 个方法通过，其中包含
114 项核心契约检查和 4 个 HTTP 传输测试；lint 为 0 错误、23 警告。
APK 已生成并下载，本机 SHA-256 与 CI 一致。详见 [BUILD-01.md](BUILD-01.md)。

尚未连接电视、安装或联调 Emby/CD2，也未测得实际 4K/144 Hz 能力。
深浅主题、十组重点色、新轮播与媒体 Logo 等新需求尚待后续独立提交；要求见
[LATEST_REQUIREMENTS.md](LATEST_REQUIREMENTS.md)。下方“未编译/无 APK/未建仓”描述属于原始交付历史。

更新：2026-09-17。dev2 是在 dev1 上的增量开发，不是重命名演示壳。原始交付状态见 `history/STATUS-dev1.md`。

## 当前做到哪里

| 部分 | 当前实际状态 | 仍需验证 |
|---|---|---|
| 总首页、所有媒体库、单库沉浸首页、海报墙、详情季集、搜索、云盘、设置 | 有原生源码和统一组件，dev2 改进胶囊导航 | Android 编译、遥控器与视觉真机对齐 |
| Emby 原生库图/海报/Backdrop/Logo、Resume/Latest/NextUp、登录、收藏、播放回报 | 有真实请求逻辑，不使用 HTML 的假数据替代 | 用户 Emby 联调、版本和权限差异 |
| CloudDrive2 | 已写只读 WebDAV 目录、视频、STRM；不是原生 gRPC | 真实 CD2 服务和网络错误 |
| MediaIndex STRM/302/代理流 | 有纯逻辑契约与 HTTP 适配源码 | 实际302/Range/UA/错误码与网盘链路 |
| Media3 播放与轨道面板 | 已写；dev2 做下层焦点隔离 | Android API 编译、Surface、音视频、生命周期 |
| 起播计时 | dev2 区分点击/源解析/交接/引擎首帧；时序计算已测 | 不代表已取得真机首帧数据或已经提速 |
| 网页建仓与构建 | 已生成一次上传的工作流、离线安全测试和说明 | 用户实际新建仓库/上传，随后远程执行 |
| GitHub 远程状态 | 本轮未建仓、未提交、未运行 Actions | 当前聊天连接仅有读取接口 |
| APK | 本交付包没有 APK | `testDebugUnitTest lintDebug assembleDebug` 首次跑通 |

## 本轮实际测试与边界

- `scripts/test-core.sh`：**114 passed / 0 failed**。使用当前可用 kotlinc/JDK 编译并执行不依赖 Android 的源码；见 `core-test-results.txt`。
- `scripts/test-bootstrap.py`：**18 项通过**。校验归档、路径穿越/符号链接、敏感文件、已有仓库保护与 README 备份；见 `bootstrap-test-results.json`。
- `scripts/test-bootstrap-workflow.py`：检查生成的完整 YAML、嵌入的实际 Python/ZIP/SHA256、所有 run 步骤 Bash 语法及可重复恢复；结果见 `bootstrap-workflow-test-results.json`。这是离线工作流验证，不是 Actions 运行。
- `scripts/check-project.py`：源包 XML 和脚本语法；具体数量与结果见 `project-check-results.json`。不证明 Compose/Media3 API 可编译。
- HTML 原型检查的范围仅为离线设计预览，见 `preview-test-results.json`；它不证明任何 Android 接口或电视性能。

没有 Android SDK/Gradle，也无法从本运行环境下载工具链依赖；没有执行 Android 整包编译、Gradle JUnit/MockWebServer 测试、lint、安装、NAS 联调或 TV 性能测试。不能用 114 + 18 的数量推导功能完成率。

## 接下来按此顺序

1. **BUILD-01**：用户上传一次初始化工作流；Actions 提交源码、生成官方 Wrapper、执行完整 Android 检查并保留日志。首轮报错先修，不能绕过测试强发 APK。
2. **PLAY-01**：验证真实 Emby 播放和进度；补完整设备能力、媒体源和转码协商。当前只实现基础直放/流播放路径，不伪报全格式支持。
3. **NET-01**：真实 MediaIndex 302/proxy、Range、续播、CD2 WebDAV、外部字幕；扩充 HTTP 层测试。当前 `TransportTest.kt` 未在 Gradle 执行。
4. **TV-01**：在用户电视测试连续按键、模态焦点、返回恢复、输入法、Surface/休眠、硬解/音轨。Macrobenchmark/Baseline Profile 与真机日志到此阶段再考虑 PC/Codex。

仍有明确缺项：自动下一集与跨季、字幕延迟、默认音轨、MediaSession、持久分页缓存/容量驱逐、账号编辑/重新登录、CD2 原生 API/NFO、特定 HDR/DV/音频直通补丁。不得把已有按钮或设计图写成这些功能已完成。

## 不变约束

不改 MediaIndex；不让播放器执行 NAS 或网盘删除/移动；不嵌入账号、个人地址、网盘 Cookie 或签名凭据。身份信息仅由用户在本机/应用设置输入。内嵌初始化源码排除评审海报及原始截图；发布前还要确定原创代码许可证、签名与依赖声明。
