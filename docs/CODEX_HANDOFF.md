# 2026-10-24 官网功能特色版已上线

用户纠正上版过度精简，官网补齐精致设计、多主题/自定义字体、Emby/绿联、STRM大文件优化、MediaIndex原生云端清晰度和遥控操作，以短说明配图组织，保留灯带与下载。新增内置image_gen云端播放概念图、主题配色示意、STRM读取图和键盘可操作焦点演示。25项线上文件HTTP/SHA256、四种屏宽与交互检查通过；仅四个站点路径部署，无Android客户端或发行改动、未再连接电视。详情见[WEBSITE-DEPLOYMENT.md](WEBSITE-DEPLOYMENT.md)。

# 2026-10-24 1.0.7 /80 自适应取色密度已实现，未发行

在已公开1.0.6之后，按用户要求新增按实际开销选择160×90/32×18的采样密度，使用升降级间隔及连续余量避免抖动。当前仍是四边颜色输出；同一边是否需要更多独立颜色分区，已询问用户，尚待选择，不把采样密度当成灯段数量。本地218单测、release/Android测试源码编译及完整lint通过；新Android回归仅编译、未执行，未出具实体TV通过结论或发布1.0.7。详见[PERFORMANCE-1.0.7.md](PERFORMANCE-1.0.7.md)。

官网按“只展示主要功能特色”的新要求已精简并部署，文字约减少82%，六个展示区，19文件线上HTTP/哈希核验通过；源码独立提交0833fcc。用户要求结束后电视休眠：已退出到TCL桌面、发送睡眠键223成功，随后ADB离线，最终电源状态未能回读；不再唤醒或操作电视。

# 2026-10-24 1.0.6 /79 正式发行与后续取色调整

[1.0.6](https://github.com/xudong7587/SunnyTV/releases/tag/v1.0.6) 已于2026-10-24 07:18:22 UTC公开为latest，源码60db13a。用户随后要求正常视频尽量保持精细取色、仅在高开销时降级，已尝试取消正在结束的发行，但公开动作已先完成；不撤回或替换这份APK，后续客户端调整递增为1.0.7 /80。

[稳定工作流40072930782](https://github.com/xudong7587/SunnyTV/actions/runs/40072930782) 的构建、214项单测、完整lint、签名/身份检查、50项debug Android回归及普通API30/真实Android TV各6项release回归全部通过，发行与验证附件上传成功。取消只发生在末尾清理阶段，因此工作流总状态为cancelled，不能记成整轮success，也不是测试失败。

公开APK完整下载回读29,391,543字节，SHA256 `4554368ee28b747d6a28a6ed4450828d3e489b2ec62d2861a50982fecd29cab1`，与GitHub资产摘要及SHA256SUMS一致；签名匹配既有证书，版本和安装ID正确。仅APK复制至 `/Volumes/4900/V3 Download/tv apk/SunnyTV-v1.0.6-79.apk` 并全文回读核验，历史包保留。候选ADB安装遭电视系统 `INSTALL_FAILED_VERIFICATION_FAILURE` 拒绝，电视仍为1.0.5 /78；没有绕过校验、卸载或清配置。尚无1.0.6实体播放验收。

以下为先前实现与发行准备记录。

# 2026-10-24 1.0.6 /79 取色性能与玻璃聚焦修复中

用户要求用电视ADB排查《凡人修仙传》卡顿，随后要求降低取色开销、采用少量颜色点均匀分段，并把液态玻璃聚焦改为系统/主题色。现场对照指向硬件视频层读回拖慢播放：1.0.4取色约950ms且输出一度3–4fps；关闭应用内画面同步后稳定段恢复约25fps，无主线程慢分发。已恢复原同步开关。用户升级1.0.5后仍有602–685ms读回与相邻丢帧，单纯降频不够。

本地1.0.6改为32×18小色网格、四边平均和时间平滑，统一10%读回时间预算，移除等待中的高频轮询；单次读回>=250ms时暂停当前播放的同步并保持灯色，不改持久偏好，关闭/开启同步可明确重试。黑边检测同样遇到慢读回停止扫描并保留当前比例。聚焦文字/图标和现有描边沿用palette.accent；手机玻璃与API33+shader源码未改。详见 [PERFORMANCE-1.0.6.md](PERFORMANCE-1.0.6.md)。尚未发布1.0.6或在实体电视安装候选包。

# 2026-10-24 已核验 SunnyTV 1.0.5 /78 正式发布

[1.0.5](https://github.com/xudong7587/SunnyTV/releases/tag/v1.0.5) 已于2026-10-23公开为latest，源码 `eb42049`，[稳定发行CI39970587833](https://github.com/xudong7587/SunnyTV/actions/runs/39970587833) 成功。APK下载回读29,375,159字节，SHA256 `ae1263005e5e7b0501c82a6004bee7ae036cbd7dd0170ca7195becd6a09c1f30`，与发行校验清单一致；签名匹配既有仓库证书，BUILD_INFO版本、安装ID和源码提交正确。用户于2026-10-24自行覆盖安装，ADB确认1.0.5 /78。未写NAS、未替换旧发行附件。以下“发行准备”为历史记录。

# 2026-10-23 SunnyTV 1.0.5 /78 发行准备

用户明确要求先修复并发布GitHub新版，稍后自行安装复测。当前工作区为 `/Users/sunnydunn/Documents/ChatGPT/SunnyTV 2`，从GitHub main `8893c09`克隆，修复分支 `codex/tv-1.0.5-fixes`。已停止操作用户电视，不覆盖其安装或配置。此前版本/日志/图层来自1.0.4现场；新代码不能标作实体TV验收。

修复包括统一灯带RGB限载及避免重复选灯效、完整4K缓冲取色和读回退避、TV解码器等比裁切，以及仅API30–32电视的实时玻璃图层。手机LegacyGlass与API33+shader未改。具体证据、已知限制及复测项见 [TV-REPAIR-1.0.5.md](TV-REPAIR-1.0.5.md)，发布说明见 [RELEASE-1.0.5.md](RELEASE-1.0.5.md)。本地212单测通过、编译通过、debug/release lint零错误；候选CI [39968054995](https://github.com/xudong7587/SunnyTV/actions/runs/39968054995) 已通过既有签名及60项Android/Android TV回归（50+5+5），修复后保留原裁切画面断言。用户再次要求直接推Git，现合入main并启动正式发行；实体TV仍待复测。以下为历史记录。

# 2026-10-23 GitHub 发布已完成

SunnyTV v1.0.4 /77 与 SunnyUI v2.1.0 均已公开为各自 latest。SunnyTV标签051732d，稳定CI39940136128成功；原签名APK SHA256 1131a9e6c08b306d6dfe81b070f1a0842238ee293d5c2255543142f15c684b99回读一致，实际候选包API30 release回归46项通过。首轮CI因增量Compose差异失败；非增量Windows与Linux重建291项一致，专项记录只接受这对精确DEX哈希，其余290项和完整Android门禁保留。原APK未重打/覆盖。

SunnyUI仓库D:/Documents/ChatGPT/Sunny-UI已推送e44fc88，安装包与Skill/组件源码全部回读通过。SunnyTV官网已同步正式1.0.4及SunnyUI2.1发行链接，35个引用HTTP及SHA通过；首次原子替换后的短暂404后复核通过。当前可用Git工作区是D:/Documents/ChatGPT/SunnyTV-release-1.0.4；原失联目录和D:/Documents/ChatGPT/SunnyTV旧覆盖层保留。今后开发从此Git入口/远端main增量继续，不能以旧覆盖层覆盖。完整记录见STATUS与RELEASE-1.0.4.md；实体电视与控制器长时表现仍待用户验证。

# 2026-10-23 GitHub 发布入口

用户已授权同步两个仓库并发布新版。SunnyTV 的 Git 工作区恢复在 D:/Documents/ChatGPT/SunnyTV-release-1.0.4，分支 codex/release-1.0.4，基线为 GitHub main 的 bf749e6。从此前最新目录逐文件核对复制代码与官网，不删除基线中缺失的 .github/.gitignore；补齐黑边/多音轨夹具的 CI 生成与忽略规则。旧开发覆盖层及失联目录不重置、不覆盖。发行选用既有 1.0.4 /77 原包及既有签名，经过稳定候选包流程核对；后续新增客户端代码必须递增版本，不能替换这份已交付 APK。以下“未推送/未发布”为历史状态。

# 2026-10-23 官网后续与目录状态

用户要求玻璃移入SunnyUI，再更新SunnyTV官网。已完成SunnyUI本地2.1（D:/Documents/ChatGPT/Sunny-UI）与现有SunnyTV官网部署，35个线上文件回读通过；没有发布SunnyUI网站或恢复完整模拟器。源码ZIP和第三方许可随官网提供，聚焦内外同心、阅读层磨砂。浏览器已切到https://sunnytv.dunn.fun:666/#appearance。官网记录见WEBSITE-DEPLOYMENT.md。

目录核对：D:/Documents/ChatGPT/SunnyTV仍是旧覆盖层，最新1.0.4文件与当前website在C:/Users/Sunny/.codex/worktrees/ugreen-lan-tls/SunnyTV。本轮后半核对时此最新目录的.git关联已不在，主仓库worktree列表也不列出该目录；来源不明，未重建Git或覆盖旧目录，现有文件完整保留。下一次Git操作先核实来源/恢复关联，不能从旧目录打包替代1.0.4。本轮SunnyUI改动尚未提交/推送，安装包和Skill已本地生成；没有新GitHubRelease。

# 2026-10-23 当前交付：1.0.4 /77 电视本地测试

最新实际工作区仍为 `C:/Users/Sunny/.codex/worktrees/ugreen-lan-tls/SunnyTV`。本轮含API30–32玻璃兼容、固定亮度/暗边扇区/防闪灯带、有限BLE重连与远端健康检查，以及原生等比裁切/独立拉伸/编码黑边识别。API33+玻璃及原签名/校准/媒体取流保持。新增核心文件LegacyGlass、VideoGeometry、VideoFrameCropper，详见[1.0.4本地记录](RELEASE-1.0.4-LOCAL.md)。

最终APK已仅投放 `Z:/V3 Download/tv apk/SunnyTV-v1.0.4-77.apk`，29,392,809字节，SHA256 `1131a9e6c08b306d6dfe81b070f1a0842238ee293d5c2255543142f15c684b99`，NAS回读一致。203单测、172契约、release/AndroidTest、完整lint（0错45/48警告），实际签名包API30 TV/API34各13项通过、无跳过。实体电视/灯带未连接，长时蓝牙及真实影片待反馈。GitHub仍暂缓，勿覆盖已分发同版本APK，下一客户端改动递增到1.0.5 /78。

以下为历史记录。

# 2026-10-22 SunnyTV 1.0.3 /76 多集音频入口与玻璃弹窗微调

默认音频入口仅保留Series/Season详情的纯图标，聚焦也不展开文字；电影/单集直接指定实际音轨，旧单集默认语言不再覆盖剧集。弹窗圆角上限24dp，玻璃底色16%→21%。材质区新增当前Android版本及玻璃不可用原因，仍要求Android13+、硬件加速、高开销主题授权、非低负载。电视版本尚未取得。

release/AndroidTest编译、196单测、172核心契约、最终原签名包API34定向11项及窄屏3项均通过。完整lint为0错误，debug44/release47警告（其中3项是测试夹具screenWidthDp的Compose建议）。无本轮手机/实体TV新安装或验收。GitHub仍暂缓，未提交/推送/发布。

只投放APK到`Z:/V3 Download/tv apk/SunnyTV-v1.0.3-76.apk`，29,376,425字节，SHA256 `8094860b55324dd686cd9a1ebaf7f4c2ac8a8466b61bd257b0ca3a47c3b8c181`，NAS回读一致；原版本保留。验证与实现边界见[1.0.3本地记录](RELEASE-1.0.3-LOCAL.md)。以下为历史记录。

# 2026-10-22 当前交付：1.0.2 /75（GitHub发布暂缓）

用户在1.0.1发布前要求改弹窗与默认音频，并纠正为每部媒体详情设置。已实现完整页面（含海报墙）玻璃取样、外侧不压暗、排序等宽等高，以及按电影/剧集/季保存的默认音频。每单集设置优先，连续播放按新集重配；不再有媒体库默认音频按钮。详情会显示继承的剧集偏好。

最终APK已覆盖安装手机保留配置，并只投放`Z:/V3 Download/tv apk/SunnyTV-v1.0.2-75.apk`，29,376,425字节，SHA256 `4808cd5443a36992f99764987deb634cbc67893bd917978a9e85dbf693327014`。196单测、172核心契约、release/AndroidTest编译、完整lint（0错/44既有警告）、API34实际签名原包26项及竖屏3项通过。没有新增TV实机验收。未提交/推送或创建新GitHubRelease，1.0.1原包保留。证据与已处理的测试问题见[1.0.2本地记录](RELEASE-1.0.2-LOCAL.md)、`.local-tests/102`。以下为历史记录。

# 2026-10-22 SunnyTV 1.0.1 /74 本地新图标测试包

用户已确认54个重绘图标，要求提供新版APK到原tv apk目录。新包包含三推杆设置、叠放媒体卡片、饱满播放控件及一体玻璃修订，保留1.0.0功能、安装ID和签名。release/AndroidTest编译、192单测、172核心契约、debug/release lint（均0错/44既有警告）通过；实际签名包从1.0.0 /73覆盖升级，11项API34原生回归通过。实体TV视觉及灯带效果待用户安装验收。

已仅复制APK到`Z:/V3 Download/tv apk/SunnyTV-v1.0.1-74.apk`并回读校验，29,376,511字节，SHA256 `9b300553d6f0acac84959b567bd114a4aeddb447222990b82828483b3a541344`。为保持音频行为，最终包保留公开1.0.0的四ABI原生库，DEX/资源/Manifest与本轮编译一致，重新对齐并用既有密钥签名。未发布GitHub新Release、覆盖旧APK或写NAS JSON。详见[1.0.1本地记录](RELEASE-1.0.1-LOCAL.md)。

以下为历史记录。

# 2026-10-22 图标重绘与官网图文修订（未新发APK）

本轮验证：192项单测零失败，Debug与AndroidTest编译通过，lint零错误/44项既有警告；54图标在20dp和40dp逐个渲染无裁切，实际播放器焦点与清晰度文字回归通过。最终API34原生截图测试通过，25个上线文件HTTP200且SHA256一致。

本轮开发代码重绘54个SunnyTV原创矢量图标，设置使用三根调节推杆、媒体库使用叠放媒体卡片，播放控制采用更饱满的实心图形。原生LineIcon与PlayerGlyph共用同一图形目录；SVG与Compose从 `design/icons/sunny-symbols.json` 生成。导航玻璃只保留外层折射，内部按钮扁平；背景色彩、折射与高光减轻。播放器清晰度/画面比例/倍速的实际文字与焦点逻辑保留。没有修改网络、取流或数据模型。

官网已先更新为图文配对的产品页，保持默认白色/黑白两色、恢复MediaIndex原彩图标，并关闭网页模拟器。新图标完成后替换对应原生截图；后续交接见 `docs/WEBSITE-DEPLOYMENT.md`。本轮没有提升版本号、发布新APK或覆盖v1.0.0 /73。实机电视尚未新增验证。

以下为既有发行记录。

# 2026-10-22 SunnyTV 1.0.0 / 73 已发布为 GitHub latest

[正式发行](https://github.com/xudong7587/SunnyTV/releases/tag/v1.0.0)已公开，非草稿、非预发行；官方 latest 下载与标签原包回读一致。标签源码 `3e622b742dbd773844b6ba6665983d36e0f4129a`，现有安装 ID 与签名保留。SunnyTV/MediaIndex 关于页已更新图标、标语、介绍及标题旁 GitHub 链接，灯带与液态玻璃仍在主项目中。

[稳定发行 CI](https://github.com/xudong7587/SunnyTV/actions/runs/39714628857)全部成功：四 ABI 原生解码构建、单测、完整 lint、签名/包核验及 API30 的50项 debug Android回归（零失败/跳过）。本地另在实际签名 release 包通过4项关于页和灯带定向检查；此前字幕回归中自动更新开关位于滚动边缘，测试增加先滚动到完整可见位置，原断言保留，重跑全部通过。实体 TV 灯带效果仍待用户测试。

正式 APK 为29,358,771字节，SHA256 `9e0eb07ad987b574ea36902284bdf4e646d92f7f89aa59380f5ec354383aaf05`。仅复制APK到 `Z:/V3 Download/tv apk/SunnyTV-v1.0.0-73.apk`，回读一致；历史本地1.0.0 /72原包保持不变。此前0.3.8仍为草稿，不覆盖历史标签/资产，不发布WebDAV或NAS JSON。以下为历史记录。

# 2026-10-22 SunnyTV 1.0.0 GitHub latest 发行准备

用户在本地包交付后明确要求将同一 1.0.0 发布为 GitHub latest，授权本批源码提交、推送与主线集成。发布保持原 APK SHA256 `2ef1cd7c10028fc3ec40dfd8c55103ea1082b4348379f657dd04e4acd86fd84c`，沿用现有签名；新增灯带合成视频与实际 release 回归门禁，保留原有完整检查。当前准备发行，尚未宣称发布成功；以下本地未提交/发布描述为历史记录。详见 [1.0.0 发行说明](RELEASE-1.0.0.md)。

# 2026-10-22 SunnyTV 1.0.0 / 72 本地整合包

用户最新要求改为直接打包 1.0.0，带入 NAS 0.3.9 灯带版的控制代码和校准数据，不做分支合并。本轮以现有 0.3.8 源码为基础，受控复制已核对的 0.3.9 灯带模块及必要接点；没有 Git 分支合并，也没有提交/推送这批 1.0.0 改动或发布 GitHub。此前 0.3.8 公开发行流程不再继续，v0.3.8 仍为草稿。SunnyTV 仓库按用户最终决定继续公开，液态玻璃仍是主项目的一部分。

release 与测试 APK 编译、192 项单测、172 项核心契约、debug/release lint（各 0 错误/44 警告）及 API30 原始签名发行包定向 11 项通过。已在隔离模拟器从 NAS 0.3.9 覆盖升级为 1.0.0，未卸载或清数据；实体 TV 灯带效果仍待用户测试。现有签名与 0.3.9 一致。APK 已仅复制到 `Z:/V3 Download/tv apk/SunnyTV-v1.0.0.apk` 并回读校验，29,219,955 字节，SHA256 `2ef1cd7c10028fc3ec40dfd8c55103ea1082b4348379f657dd04e4acd86fd84c`。详见 [1.0.0 本地记录](RELEASE-1.0.0-LOCAL.md)。以下保留历史状态。

# 2026-10-21 SunnyTV 0.3.8 / 70 正式发行准备

用户已确认详情闪退解决，并明确授权将本版本提交、推送并发布为 GitHub latest。发布保留已测试 APK 原始字节，SHA256 `b8e5b6bd026421b0b185aac79b21353727b193430fd8ff13afd7a25d6ce4d917`；既有签名、完整 CI 门禁、包身份和不可覆盖检查继续执行。TV 比例修订仍待实体电视反馈。此次授权取代本批本地记录中“未提交/推送/发布”的当前限制；下文保留当时状态。详见 [0.3.8 发行说明](RELEASE-0.3.8.md)。

# 2026-10-21 SunnyTV 0.3.8 / 70 本地修订

TV视频层改为Activity原生PlayerView/SurfaceView，Compose只叠OSD，修正缩放互操作兼容路径；按钮常显当前比例并保存重建状态。保留0.3.7的详情重复数据容错和三种玻璃修订。177单测、API34最终9项、API30播放器5项及签名/发行检查通过；用户真实TV缩放和三部剧仍待复测，未取得其异常堆栈，旧TV演员导航超时仍保留。APK已复制并回读校验到 `Z:/V3 Download/tv apk/SunnyTV-v0.3.8.apk`，未提交/推送/发布GitHub，未安装实体设备。详见 [0.3.8本地记录](RELEASE-0.3.8-LOCAL.md)。下文为历史记录。

# 2026-10-21 SunnyTV 0.3.7 / 69 本地修订

修复已复现的重复演员/季/版本/推荐数据导致详情Lazy键冲突退出；尚未取得用户手机堆栈，三部真实媒体仍待复测。液态玻璃中央覆盖降至未选中3%/选中10%、壳体4%；极光改内部淡彩，雾面改平面斜切棱边。177单测、172核心契约、API34定向20项及最终详情复测、API30定向9项通过；旧演员上下导航TV空闲超时仍记录，未宣称全套通过。签名/发行检查通过，APK已复制并回读校验到 `Z:/V3 Download/tv apk/SunnyTV-v0.3.7.apk`；未提交/推送/发布GitHub，未安装实体设备。详见 [0.3.7本地记录](RELEASE-0.3.7-LOCAL.md)。本节覆盖下文历史玻璃透明度与边缘样式。

# 2026-10-21 SunnyTV 0.3.6 / 68 本地修订

相似推荐评分角标改为浅色半透明白底黑字、深色黑底白字；材质选项参考MediaIndex Next的42dp清晰卡片式示意，避免缩小完整模糊渲染造成糊块，实际材质保持原渲染器。177单测、API34最终10项、API30兼容7项及签名/发行检查通过。APK已复制并回读校验到 `Z:/V3 Download/tv apk/SunnyTV-v0.3.6.apk`；未提交/推送/发布GitHub，未安装实体设备，未修改MediaIndex。详见 [0.3.6本地记录](RELEASE-0.3.6-LOCAL.md)。下文为历史记录。

# 2026-10-21 SunnyTV 0.3.5 / 67 本地修订

玻璃遵循深浅色设置，按钮黑白文字与透明度焦点；海报恢复原主题色。页面独立按钮接入真实背景折射，修复滚动离屏层的黑块/厚边；播放器视频仍不采样。177单测、API34最终20项、API30兼容13项及签名/发行检查通过。APK已复制并回读校验到 `Z:/V3 Download/tv apk/SunnyTV-v0.3.5.apk`；未提交/推送/发布GitHub，未安装实体设备。详见 [0.3.5本地记录](RELEASE-0.3.5-LOCAL.md)。本节取代下文历史版本的强制深色、白色主题色/焦点圈及仅顶部背景采样描述。

# 2026-10-21 SunnyTV 0.3.4 / 66 本地修订

材质列表只显示当前可用选项，关闭高开销开关/低负载/设备不支持时隐藏受限材质，勾选标记跟随实际回退。液态玻璃移植Streama首页底部对应的公开Miuix非色散折射和双峰高光shader，保留TV布局、白色焦点、内部扁平。177单测、API34定向18项、API30兼容9项及签名/发行检查通过。APK已复制并回读校验到 `Z:/V3 Download/tv apk/SunnyTV-v0.3.4.apk`；未提交/推送/发布GitHub，未安装实体设备。详见 [0.3.4本地记录](RELEASE-0.3.4-LOCAL.md)。下文为历史记录。

# 2026-10-21 SunnyTV 0.3.3 / 65 本地修订

液态玻璃改为柔和弧边、内部按钮扁平、白色重点色与焦点圈；新增Monet系统/主题色两种来源及9种色彩倾向。修复播放器右上角标签、显式跳过片尾切下一集和连续返回退出。177单测、172核心契约、API34主题/功能回归21项与最终定向6项、API30兼容17项与菜单最终3项通过。签名/包检查通过，APK已复制并回读校验到 `Z:/V3 Download/tv apk/SunnyTV-v0.3.3.apk`。未提交/推送/发布GitHub，未安装手机。详见 [0.3.3本地记录](RELEASE-0.3.3-LOCAL.md)。下文为历史记录。

# 2026-10-21 SunnyTV 0.3.2 / 64 玻璃细化

全局按钮/选项/输入控件统一玻璃表面，顶部液态玻璃更清透并强化边缘高光，材质选择改为紧凑两列。真实背景折射仍限顶部与样块，不采样视频。APK已投放并回读校验至 `Z:/V3 Download/tv apk/SunnyTV-v0.3.2.apk`。未提交/推送/发布GitHub，未安装手机。构建、167项单测、16项Android定向测试及签名/包检查通过；详情见 [0.3.2本地记录](RELEASE-0.3.2-LOCAL.md)。以下为历史记录。

# 2026-10-21 SunnyTV 0.3.1 / 63 本地主题修订

实际交付工作区：`C:/Users/Sunny/.codex/worktrees/ugreen-lan-tls/SunnyTV`。7单色+自定义色盘、同色深浅算法、顶部同心圆角、16材质预览与液态玻璃已实现。真实背景采样/折射限顶部悬浮导航和样块，需显式授权、非低负载、Android13+硬件加速；其它面板采用对应静态表面。最终APK已复制并回读校验到 `Z:/V3 Download/tv apk/SunnyTV-v0.3.1.apk`，未提交/推送/发布GitHub，未再安装手机。验证与限制见 [0.3.1本地记录](RELEASE-0.3.1-LOCAL.md)。下文为历史记录。

# 2026-10-21 SunnyTV 0.3.0 / 62 本地主题测试包

本轮交付代码位于 `C:/Users/Sunny/.codex/worktrees/ugreen-lan-tls/SunnyTV`，基于最新 0.2.8 / `dcc0940` 和既有绿联 LAN TLS 修复，迁入 Sunny UI 轻量主题增量；没有合入灯带版。release 编译、172项核心契约、166项单测、11项Android回归、签名和包检查通过；APK已复制并回读校验至 `Z:/V3 Download/tv apk/SunnyTV-v0.3.0.apk`。仅本地测试，不提交/推送/发布GitHub。详情见 [0.3.0本地记录](RELEASE-0.3.0-LOCAL.md)。以下内容为历史记录，不代表本次分发状态。

# 2026-10-21 绿联局域网 HTTPS 直连修复（本地未发行）

基于 Git 最新 `dcc0940`（0.2.8 / 60），独立分支 `codex/ugreen-lan-tls`。只修复绿联 `https://内网IP:9443` 的默认自签名证书兼容：首次无凭据读取设备证书，登录成功后随来源保存，API、图片和原画播放共用；证书仅对同一私有 IPv4 和端口有效，变更须重新添加。域名、普通 Emby/CD2/STRM、公共客户端保留原有 TLS 验证。没有修改 NAS、DNS 或反代，没有发行/同步 APK。

验证：核心契约172/172、Gradle单测161/161（含6项真实TLS传输测试）、debug/Android测试包构建通过；API30模拟器7/7（含真实Android TLS自签名登录/浏览/图片/Range播放与6项既有绿联UI/存储回归）。lint 0错误，app37警告、decoder4警告；新增2项 `CustomX509TrustManager` 提醒保留，已按证书精确绑定、跨主机/端口拒绝及系统校验回退核对，未关闭检查。未连接真实NAS或验证实体电视。Windows本地链接0.2.8发行附件中哈希核验通过的FFmpeg静态库，未重编FFmpeg；正式发行仍按原流程构建。

# 2026-10-16 SunnyTV 0.2.5 / 57

基于 0.2.4 增加播放器右上角实时信息开关，使用界面字体，左上角每秒刷新真实格式、下载字节速度、缓冲和解码器；标称码率与接收速度分开。软件升级页改为 GitHub 在前、WebDAV 默认关闭，开启后显示已加密保存的配置；WebDAV 直接下载 GitHub latest 同名 APK，不读取 DAV JSON，验证包名、两个版本字段及签名后交给系统确认。关于页 MediaIndex 使用文字 GitHub 详情链接。

用户已确认 0.2.4《功夫梦》云端 1080P / 4K 播放正常；本轮没有修改 AAC / PCE 原生解码逻辑。Windows 使用上一正式版相同 native 二进制，仅本机临时替代构建脚本，提交前已恢复；正式 Linux 流程仍从源码构建四 ABI。

本地 debug / Android 测试包编译、146 项 JUnit、172 项核心契约和 3 项发行检查通过，debug/release lint 0 错误、42 警告。定向 Android 共 40 项。复测曾暴露只有一条长字幕时，Media3 将外部字幕时长估为最后起点 + 10ms，缓冲释放后拖动会被判为超出结尾；云端字幕解析器现改用实际 cue 结束时间发布时长，保留全部原断言。字幕切档测试核对实际暂停位置，避免固定起点。修复后本机 40 / 40 项 Android 回归通过；正式 Linux 门禁结果以本版 Actions 及发行附件为准；原失败日志仍保留在本机 .local-tests/025。

WebDAV 发布增加与 GitHub 完全同名、相同字节的 APK，保留哈希 APK 和旧 JSON 入口让 0.2.4 客户端升级。仅写授权升级目录，未操作 NAS 媒体或服务、未连接手机。新增界面尚未经过实体设备用户验收。详见 [0.2.5 说明](RELEASE-0.2.5.md)。

以下为历史记录。

# 2026-10-15 发布已核验

GitHub 已保留 v0.2.1 原包，并发布 v0.2.2（源码 9c439cf，versionCode54）为正式 latest。公开 `/releases/latest` 实际跳转 v0.2.2；通过 latest 下载的 BUILD_INFO、SHA256SUMS 及 APK 完整字节与本地一致。APK 26340399 字节，SHA256 `db1003971dee7a48b9e3230088a34d0e8d7926d377d09fbf842a0d87d78cf32a`。WebDAV latest.json 同步选择0.2.2 /54，远端完整回读校验一致，0.2.0及0.2.1历史包保留。API30 发布后再次执行官方版本检查/下载校验2/2通过，无自动安装到实体设备。官网线上10文件、响应式与原生截图验证记录见 WEBSITE-DEPLOYMENT。

# 2026-10-15 官网与 GitHub latest 更新

0.2.1 / 53 已以原 WebDAV 字节发布 GitHub（标签 v0.2.1，源码 a02e493）。0.2.2 / 54 修正应用内网页下载固定到官方 `/releases/latest`，原生版本查询读取 `latest/download/BUILD_INFO.txt`，仍不使用 REST API；保留严格 TLS、重定向边界、哈希/包身份/签名验证。新增数字正式版发行工作流，默认 latest，拒绝覆盖标签/资产。

官网已在既有 myblog 发布，10 个线上文件 HTTP 200 且完整 SHA-256 匹配；320/390/768/1366 宽度无横向溢出，外观与绿联步骤展开已验证。原生页面截图由独立模拟器测试生成，原创媒体数据和海报；未读取真实媒体。README 已增加官网、绿联说明与截图。

0.2.2 debug/release、Android 测试包构建成功；141 项 Gradle 单测、172 项核心契约、3 项发行检查通过，lint debug/release 各 0 错误/40 警告。API30 实连 GitHub 检查与下载校验 2/2 通过（首次验证时 latest 为 0.2.1，安装版本 0.2.2，因此下载后正确拒绝降级安装）；原生演示截图 1 项通过。此前 0.2.1 的 31 项定向 Android 测试证据保留，本轮未重跑完整历史 TV 套件，未进行实体 TV 播放验收。

额外执行旧 bootstrap 工具测试在 Windows 下 16 项通过、1 失败、1 错误：ZIP 反斜线样例在 Windows 规范化后未触发预期拒绝，以及当前用户无创建符号链接权限；未禁用或跳过测试。该工具用于新仓库初始化，未参与本次既有项目发布；Linux CI 仍要求运行。源码/XML/Python sanity 检查通过。证据位于 `.local-tests/0.2.2-*`，截图与部署记录位于 `.local-tests/website-*`。

# 2026-10-15 SunnyTV 0.2.1 已同步 WebDAV

版本0.2.1 / 53，在当前整合入口 `D:/Documents/ChatGPT/SunnyTV-transcode-main` 继续开发，保留原0.2.0及旧目录覆盖层。媒体来源添加按钮常显完整名称，清除配置使用垃圾桶；连接表单移除内部类型切换和外链，首次连接在独立入口选择服务，说明可用遥控器上下阅读。

绿联新增名称升降序、评分、年份、时长、播放日期、大小、比特率和随机排序；名称升序沿用服务端分页，其它先读取完整目录再本地处理。总数/重复/不完整分页、10000项、8Mi字符、90秒和取消保护，缺少字段置后、全缺时提示，不写回NAS。工具说明保留在原工具行lazy item内，不改变海报网格索引。

MediaIndex云端功能复用条件已用HTTP测试核对：保留原始`/api/play/{assetToken}`时可通过sourceMediaUrl识别并查询档位，绿联token不发送到MediaIndex。真实绿联先前样本只有最终115 CDN直链，原始授权入口已丢失，当前样本的云端档位仍需入口映射/接口支持，不能宣称已完成真实联调。未读取NAS的STRM文件、提取凭据或修改MediaIndex。

核心172/172、Gradle单测139/139、发行检查脚本3/3、API30定向31/31通过；debug/release构建及lint通过（各0错误/40警告）。UI检查涵盖常显标签、首次类型选择、无内部切换/外链、说明/字段遥控、排序菜单及工具行到首海报导航，保留首页、云端/字幕/缓存/更新回归。真实绿联排序及TV播放仍待用户验收，完整历史TV失败清单保留。

沿用既有签名与安装ID，非debuggable发行包通过身份/内容/四ABI解码库及许可证检查；已用分发脚本更新WebDAV并全文回读校验哈希及大小，旧包保留。包`SunnyTV-v0.2.1-efae19468f82.apk`，26340403字节，SHA256 `efae19468f825df4698cf25f62056e8da6f1790499e3c017168b5cc7c2c4f249`。未发布GitHub、未向实体手机安装。详见[0.2.1说明](RELEASE-0.2.1.md)。以下为历史记录。

# 2026-10-15 SunnyTV 0.2.0 已同步 WebDAV

本次在已发布 dev36.4 / 51（基线 `480ccfa`）上整合原 SunnyTV 目录的绿联适配，发行整合入口为 `D:/Documents/ChatGPT/SunnyTV-transcode-main`，本地分支 `codex/ugreen-webdav-0.2.0`。版本 `0.2.0` / 52，从本版起使用纯数字版本号。原目录开发覆盖层保留，不能用旧 dev33 源码替代本版。

新增绿联连接引导及接口能力分支；首页仅“顶部按钮→向下”优先落在第一个继续播放，没有/隐藏续播时仍进入方块海报。海报左右轮换和媒体区返回路径不变。保留现有音频、115清晰度、字幕和原生GitHub更新功能；绿联不提供字幕搜索下载，UI及适配器均限制。

核心172/172、Gradle单测132/132、发行检查脚本3/3通过；debug/release编译、APK及测试APK构建、lint通过（各0错误/40警告）。API30模拟器定向30/30通过：首页入口4项、绿联5项、既有表单2项、原生解码器可用/声道混合3项，以及播放、清晰度、字幕连续性、缓存和WebDAV更新相关16项。本轮未重跑EAC3/DTS合成包解码两项；保留完整TV历史失败及真实设备待验范围，不宣称全套TV/真实绿联进度验收。

非debuggable发行包已核对既有签名 `5e8dcd5e1eee828e064682ba6f8dc7d54dfcf59d3182dd6b427a23d1214d6b00`、安装ID、四种ABI的FFmpeg及许可证、无私有字体/密钥/联调材料。使用 `scripts/publish-webdav-update.py` 发布，`Z:/V1 Tools/SunnyTV-updata/latest.json` 已指向0.2.0/52；远端APK全文回读的哈希/大小与本地一致，所有旧包保留。

升级包：`SunnyTV-v0.2.0-a6ea40209949.apk`，26340403字节，SHA256：`a6ea40209949a0b5cba9e5137302b0c653e5fad29083df7d447b55d8672bfd21`。用户通过“设置→实验升级→检查更新”手动安装；未向实体手机安装，未新建GitHub发行或tag，未修改MediaIndex/NAS服务。详见[0.2.0发布记录](RELEASE-0.2.0.md)。

以下为历史记录。

# 2026-10-14 dev36.4 已发布并同步 WebDAV

最终版本 `0.1.0-dev36.4` / 51，发行源码 `8f949ab184e7cae37a498acf1eeef146d70ce1d2`（PR #14；清晰度/原生更新 UI 为 PR #13）。[GitHub Release](https://github.com/xudong7587/SunnyTV/releases/tag/v0.1.0-dev36.4) 与 [签名发行工作流](https://github.com/xudong7587/SunnyTV/actions/runs/38509938911) 均成功，发行 CI 的 16 项 Android 测试通过。

APK 下载后重新验证官方清单哈希、包身份/非 debuggable 及既有签名。已用 `scripts/publish-webdav-update.py` 同步 `Z:/V1 Tools/SunnyTV-updata`，`latest.json` 选中版本 51；dev36.3 / 50 及全部旧包保留。SHA256：`0cfd22b1ed05d260d5057ba9a1a33b309cb4199a4a13a5498449a43fc4bc6da0`。

发布后再次验证：官方 Atom 订阅最新入口为 dev36.4，模拟器原生 GitHub 检查通过。dev36.3 发布后的 API 403/额度 0 问题已通过移除 REST API 依赖修复；新版读取官方订阅/BUILD_INFO/SHA256SUMS，并保留 APK 签名检查。旧客户端需先通过 WebDAV 或官方 APK 安装此版，才有新入口。未安装实体手机、未修改 MediaIndex/NAS 服务或媒体；TV 实机、真实字幕和 CD2 验收仍待完成。

以下为实施记录。

# 2026-10-14 dev36.4：GitHub 更新 API 限额修复

dev36.3 已发布并同步 WebDAV，但发布后复查发现未登录 GitHub REST API 已返回 HTTP 403，额度为 0；不是应用“已最新”。按用户原有更新修复授权继续追加 dev36.4 / 51，保留原版本。

更新检查使用官方 `releases.atom` 及 BUILD_INFO，包含开发测试版；下载使用官方 SHA256SUMS 校验，不再依赖受限 REST API。保留 TLS、重定向限制、取消及签名/身份检查，增加订阅 DTD/外部实体/深度/大小约束。在同一限额网络下 Android 模拟器实连通过。当前分支 `codex/github-update-feed`，基于 `c20b221`。本地 JUnit 116 项通过，debug/release 编译和 lint 通过（0 错误/40 警告）；更新回归 5 项及 GitHub 实连检查/下载校验 2 项通过。核心接口未改，延续本轮 172 项契约结果。最终发行与 WebDAV 回执在完成后追加。

# 2026-10-14 dev36.3 发布修订

用户已授权推送 GitHub、更新 WebDAV，并修复应用 GitHub 入口。当前分支 `codex/github-update-native`，基于 `44b96b4` / 已发布 dev36.2，版本 50。

清晰度面板仅保留档位。GitHub 入口使用原生页面和官方 Releases API，包含开发测试版，读取 BUILD_INFO 比较当前与最新版本，保留升级包内容及签名校验；网络错误不宣称最新。实连 GitHub 模拟器检查首次网络失败、重试通过。本地核心 172/172、JUnit 116/116、Android 定向 10/10 通过；debug/release 编译和 lint 通过（各 0 错误/40 警告）。GitHub 实连用例不纳入依赖网络可达性的发行 CI，纯筛选/校验测试仍执行。最终签名发行和 WebDAV 回执在完成后追加。详见 [dev36.3](RELEASE-dev36.3.md)。

# 2026-10-14 dev36.2 已发布并同步 WebDAV

发行源码 `04806dd42b360cda87873751af6a156cd38af10e`（PR #12），版本 `0.1.0-dev36.2` / 49。[GitHub Release](https://github.com/xudong7587/SunnyTV/releases/tag/v0.1.0-dev36.2) 与 [发行工作流](https://github.com/xudong7587/SunnyTV/actions/runs/38506001866) 均成功。

发行 CI 的 16 项 Android 测试通过，含原生音频解码及本轮播放/字幕/缓存回归；沿用现有签名密钥，APK 非 debuggable。下载后重新校验清单哈希、签名及发行内容，并通过 `scripts/publish-webdav-update.py` 同步 `Z:/V1 Tools/SunnyTV-updata`，`latest.json` 已指向版本 49，旧包保留。

APK SHA256：`baf4397e1d523453c1c1f0fa3c8dad46abcbf8fb9b4eb874e97cc427b8a8eda2`。本轮未向实体手机安装，也未修改 MediaIndex/NAS 服务；真实字幕服务、CD2 与 TV 实机验收仍待完成。新增主题质感已按用户要求撤回。以下为实施时记录。

# 2026-10-14 dev36.2 发布准备

当前入口 `D:/Documents/ChatGPT/SunnyTV-transcode-main`，分支 `codex/playback-theme-polish`，基于已发布 dev36.1（`ec49f82`）。用户已取消新增质感/配色，授权完成其余修订、发布新版并同步 WebDAV；本轮没有修改 MediaIndex 或 NAS 服务。dev36.1 已于 2026-10-14 发布并完成此前授权的分发/部署，下方未发布描述为历史状态。

本轮实现首帧后 115 自动查询、第三方 STRM 清晰度说明、字幕语义选择跨播放器恢复、首页轮播缓存及手机设置分类宽度。共享字幕模型只归一化 Media3 的数字源索引前缀，保持精确匹配及不擅自选择同语言其它字幕。网络策略和 CD2 接口未变。详见 [dev36.2 发布说明](RELEASE-dev36.2.md)。

本地已编译 debug/release，纯 Kotlin 172/172，JUnit 114/114，debug/release lint 各 0 错误/35 警告。Android API 30 定向 11/11 通过；最终签名发行、WebDAV 回执将在完成后追加。未宣称真实字幕服务、CD2 或 TV 实机验收；保留历史完整 TV 测试失败。

# 2026-10-13 当前开发入口

继续 `D:/Documents/ChatGPT/SunnyTV-transcode-main` 的 `codex/115-cloud-playback`；配套网关在 `D:/Documents/MediaIndex前端/.worktrees/115-cloud-playback`，不要覆盖旧工作区。当前基线为 SunnyTV main `4546c74` 与 MediaIndex main `3409aa6`。详见 [实现、测试和待验收](115_CLOUD_PLAYBACK.md)。

用户先要求完成代码和模拟测试、暂不登录，随后授权优先直连并发布两个项目 Git 版和 SunnyTV WebDAV 更新。当前版本为 dev36 / MediaIndex 0.8.4，最终发行状态查对应 GitHub Release。真实 115 验收仍需要有效登录态；不要擅自登录或部署 MediaIndex NAS 服务。已知限制包括真实响应尚未核实、会话进程内保存、签名流失效需手动重新选档；默认只有清单/密钥过 NAS，视频 CDN 直连，代理需要手动选择。旧发行/实机遗留问题仍保留，以下是历史记录。

# 2026-09-30 dev32接续入口

已基于公开dev29增量加入方向锁定与WebDAV实验升级，取消dev28车联和手动旋转。源码、功能、验证结果见 [RELEASE-dev32](RELEASE-dev32.md)，变更列表仅放 [CHANGELOG](../CHANGELOG.md)。本地核心172/172、JUnit85/85、Android定向5/5，debug/release lint各0错误/31警告；旧完整TV失败仍保留，实体在线升级待反馈。

## 2026-09-27 dev29 审查修复入口

本版修复 dev27 审查的五项问题，不含 Carlink。实现与本地验证见 [SEC-01](iterations/SEC-01-dev29.md)，用户可见变化见 [发布说明](RELEASE-dev29.md)。核心172项、Gradle单测79项、Android定向9项通过，debug/release lint各0错误/28警告。使用非调试release变体，安装ID与既有签名不变；签名发布状态以SEC-01追加记录和GitHub Release为准。下方为历史记录。

# 网页优先开发的补充

当前先按 `docs/WEB_SETUP.md` 建立 GitHub CI。仅当真机/本地工具链需要时转 Codex；不要要求用户现在重开项目。新增计时和初始化器代码详见 CHANGELOG，所有未编译/未联调状态仍有效。

# Codex 接续开发入口

你正在接手 **SunnyTV 0.1.0-dev2**，不是从零开始规划，也不要重新命名为 MediaIndexTV。

## 最短开工顺序

先读根目录 `AGENTS.md`、`docs/STATUS.md`、`docs/DEVELOPMENT_PLAN.md`、`docs/DESIGN_SYSTEM.md`、`docs/BUILD.md`，再查看源码与 `preview/index.html`。`docs/reference/previous-spec.md` 仅存档，新规划优先。

本轮已交付 Android 工程代码、原型、核心测试和构建配置；**没有**通过 Android 编译或真机验收。第一步是建立可信构建，不要直接宣布功能完成。

## 用户已确认的产品选择

名称 SunnyTV；电视原生 Kotlin/Compose/Media3；总首页与媒体库首页分开；原截图只对应单库的沉浸顶部；所有媒体库使用 Emby 原生图片；海报、Backdrop、Logo 和观看进度优先复用 Emby；首要来源 Emby + CloudDrive2；MediaIndex 的 STRM 起播兼容是硬要求。

不使用 WebView，不整体 Fork Moonfin，不复制其尚未审计的兼容补丁，不修改用户 MediaIndex 仓库。本轮没有向 GitHub 创建仓库、提交代码或发起 Actions；源码包就是本地基线。

## 第一轮任务：BUILD-01

1. 检查本机 JDK、Android SDK 和 Gradle 网络，使用 JDK 17 / SDK35 / Build Tools35.0.0 / Gradle8.11.1。当前依赖表在 app/build.gradle.kts，先尝试现有固定版本。
2. 执行 `scripts/build.ps1` 或 `scripts/build.sh`。生成标准 Wrapper；不要伪造 wrapper jar，不要把 SDK/密钥/缓存提交到仓库。
3. 修复 Kotlin、Compose TV、Media3 API 不匹配和 Manifest/R8/lint 问题。保留功能和测试；单项升级必须说明原因。
4. 跑 `testDebugUnitTest lintDebug assembleDebug`。纯核心测试还可单独用 `scripts/test-core.sh` 运行；文档中的 114 项不是 Android 整包测试。
5. 输出 APK、SHA-256、编译日志摘要、测试失败列表、补丁说明。不用模拟器结果声称电视已优化。

## 第二轮任务：PLAY-01

重点读 `source/emby/EmbySource.kt` 与 `feature/player/PlayerActivity.kt`。目前基础直放链路有源码，但完整 DeviceProfile 尚未实现。不要伪造支持全部视频/音频编码；根据实际 MediaCodec/音频输出和媒体字段做协商。服务端禁止直放或需要额外打开会话时要明确处理。

验证 `ErrorCode: null`、多 MediaSources、容器为空、`RequiredHttpHeaders`、外部文本字幕和根相对返回地址。已有 `.strm` 解析器不等于所有服务器 STRM 都已能播放。不要在 Android 直接读取 `/volume...`。

回报开始/进度/停止必须有序，不能把预取元数据当作播放开始，失败不阻塞 UI。已有 SessionEvents 不允许被改回会丢开始/停止的 DROP_OLDEST 队列。

## 第三轮任务：SOURCE-01

使用用户本地配置依次测试：普通 Emby 媒体，MediaIndex /api/play 的 302 与 proxy，CD2 WebDAV 的目录、视频与 STRM。无测试服务时补 MockWebServer，不编造联调结果。

CD2 目前仅 WebDAV；不能在 README 写“原生 CD2 API 已支持”。后续 gRPC 需从真实 Core 版本读取 proto 并单独开发。

## 第四轮任务：TV-01

对照 `preview/` 校准原生页面，尤其总首页和单库首页的层次差异。dev2 已改为胶囊导航，但单库重点海报、过渡与整体细节仍需对齐视觉稿，并经 Android 编译和真机验收。

逐页检查焦点、返回、输入法、面板隔离和屏幕安全区。修复需带 UI/仪器测试，不能只增大动画时长。保留原生媒体库完整图片；库图文字不裁切。

## 本地配置与隐私

在 APK 设置里填用户服务器；不要求用户把密码、完整 STRM Token 发给聊天。测试日志只记录匿名来源类别、状态码和耗时。

真机信息可由 `scripts/device-report.sh` 获取（只读）；型号/Android/ABI仍需核对。不要根据处理器是64位就只构建arm64或改变系统分辨率。

## 每轮汇报格式

- 已修改：模块、文件、行为、兼容性影响。
- 已执行：命令、真实结果、日志/报告位置。
- 已验证：Android编译、Mock测试、模拟器、服务、电视分别说明。
- 仍未完成：具体项与阻塞，不以一个“大部分完成”百分比替代。
- 下一步：一个明确任务，不能擅自扩展成网盘整理或手机平台。

## 可以直接作为新对话第一条消息

```text
请接手当前 SunnyTV 工程，继续开发，而不是重新生成一个新项目。
先阅读 AGENTS.md、docs/STATUS.md、docs/CODEX_HANDOFF.md、
docs/DEVELOPMENT_PLAN.md、docs/DESIGN_SYSTEM.md、docs/BUILD.md，
并打开 preview/index.html 理解整个 TV 界面。

用户已确认：独立 Kotlin/Compose TV/Media3 客户端；Emby 原生库图片与
元数据；总首页、单库沉浸首页、海报墙、详情、云盘、设置风格统一；
Emby + CD2 WebDAV + MediaIndex STRM/302/proxy 为核心。
不要更改 MediaIndex 仓库，不用 WebView，不随意升级全项目依赖。

已有源码尚未完成 Android 整包编译。请先完成 BUILD-01：核对工具链、
生成官方 Gradle Wrapper、编译并修复、执行测试和 lint，输出真实 APK、
SHA-256 和报告。之后推进 PLAY-01 的设备能力与 Emby 协商，再做
SOURCE-01 和 TV-01。不得删除未编译通过的功能来伪造完成。
不要把浏览器原型测试、102 项纯 JVM 测试或模拟器结果说成 TV 验收。
```
