# 2026-10-24 官网功能特色版已上线

用户认为上一版过度精简，明确要求补回精致设计、多主题/自定义字体、Emby与绿联、STRM大文件优化、MediaIndex原生云端清晰度和遥控优化。官网现按九个展示区组织，短文案配原生截图、主题配色示意、STRM按需读取示意及可键盘操作的遥控焦点演示；灯带与下载保留。没有恢复开发记录、兼容性长文或完整网页模拟器。

使用内置image_gen新绘云端播放概念图，1672×941、WebP116,352字节，概念图与原生截图明确区分，提示词及来源见website/assets/features/README.md。功能描述已核对现有代码：七种主题色/自定义色盘、界面与字幕字体分别导入；STRM按需读取与设备内存缓冲预算；115已生成云端档位/直连优先/有效MediaIndex入口条件。没有宣称所有片源支持、NAS重新编码或虚构性能数字。

仅向授权站点修改index.html并新增features.css、features.js、assets/features/cloud-native.webp四路径，原首页在.local-tests/website-features/before-live备份，历史资源保留。25项当前引用文件最终HTTP200且全文SHA256与现网一致；既有支持文件的仓库LF/现网CRLF差异已单独核对。资源索引刷新时初查曾404，最终整套复核与正式浏览器验收通过。320/390/768/1366宽度无横向溢出，配色、玻璃、遥控点击/左右键/首尾边界及焦点通过，页面无控制台错误或坏图。未改Android客户端、发行版本或唤醒电视。

证据：.local-tests/website-features/layout-checks.json、http-verified.json、deploy-verified.json、live-remote.png。

# 2026-10-24 官网精简已上线

用户要求“主要功能和特色展示”，本轮保留首屏、媒体库、液态玻璃交互、原生播放、灯带与下载六区，合并媒体来源标签，移除兼容长文、FAQ、重复原生截图、素材展区和独立MediaIndex长篇介绍；相关项目保留页脚入口。可见正文2496→459字符，约减少82%；版本展示1.0.6，下载统一GitHub Latest。手机应用材质源码未动。

从现网文件逐项修改，白名单仅index.html、glass-demo.js和新增compact.css，旧文件本地备份 `.local-tests/website-simple/before/`，未整树覆盖或删除历史素材。原网页玻璃仍使用同一SunnyUI2.1组件；移除说明dialog后解耦初始化，背景切换继续工作。

浏览器320/390/768/1366实际视口检查均无横向溢出；黑白切换与刷新记忆、三背景选择和组件增强检查通过，未发现控制台错误。发布后曾短暂出现路由404/暂停预览页，文件服务更新后复核：19项当前引用文件HTTP200、完整SHA256匹配，正式页面浏览器复验成功。证据 `.local-tests/website-simple/http-verified.json`、`deploy-verified.json`、`live-home.png`。没有改反代、容器或其它NAS目录。

# 2026-10-24 官网只读审阅

用户指定现网目录 `/Volumes/4900/docker/myblog/sites/sunnytv/`，本轮只读取，未修改或部署。首页72项src/href引用的本地文件及锚点均存在；这不等于外部HTTP与视觉验收。

页面仍展示1.0.4 /77，SEO、首屏版本、下载说明、FAQ与发行记录链接需跟随当前公开1.0.6 /79更新；下载按钮已正确使用GitHub `/releases/latest`。玻璃说明应补充1.0.5电视专用路径和1.0.6系统/主题聚焦色，同时保留手机与API33+材质边界。播放说明应反映电视解码器几何修复、慢视频层停止额外黑边扫描的限制。灯带说明应补充白色负载限制、完整4K缓冲取样及读回性能保护，不能将待完成的1.0.7自适应精度写成已发布。SunnyUI2.1和完整网页模拟器暂停的说明仍保留。

现网树与仓库website共有62项差异（包含现网独有历史素材），不得将仓库旧目录整树覆盖现网；若后续授权更新，应以现网逐文件核对后的内容为基准，保留无关素材与既有视觉。

# 2026-10-23 正式发行同步

SunnyTV 1.0.4 /77 与 SunnyUI 2.1.0 已公开为各自 GitHub latest。官网首页、下载区、Android11玻璃兼容、灯带/裁切和FAQ统一改为正式版说明；下载仍指向SunnyTV `/releases/latest`，网页玻璃说明新增SunnyUI安装包与设计Skill的发行链接。保留黑白主题、默认白色、同心圆角与内层磨砂、原生截图，完整模拟器继续暂停。

发布只作用于原授权 `Z:/docker/myblog/sites/sunnytv`。12路径白名单中资源与前版一致，仅首页变化；原首页本地备份。线上35个当前引用文件HTTP200、SHA256全匹配，源码ZIP和第三方许可保留。替换后首次首页404，后续复核全部通过。320/390/768/1366无溢出、黑白保存、背景切换、键盘、弹窗外侧亮度及同心圆角检查通过。APK从GitHub完整回读后才执行网站发布。证据位于 `.local-tests/release-104/`。以下是历史记录。

# 2026-10-23 SunnyUI 2.1 玻璃接入 SunnyTV 官网

本轮目标是现有SunnyTV官网 `https://sunnytv.dunn.fun:666/`，不是发布SunnyUI站点。使用最新1.0.4所在目录的website；`D:/Documents/ChatGPT/SunnyTV`仍是较早覆盖层。用户纠正后已将可见浏览器切回SunnyTV，并打开已部署的正式地址。

复用 `D:/Documents/ChatGPT/Sunny-UI` 的2.1无React构建，JS逐字节相同；新增真实网页背景折射演示、原SunnyTV SVG图标、动画/星空/山海背景切换。工具栏四边统一内距，实际外框34.5px、间距8px、内层26.5px；内层焦点与外框同心。说明dialog外框液态玻璃21%、内部磨砂阅读层65%，不压暗外侧；原生截图保持独立标注，完整网页模拟器仍暂停。官网继续默认白色/黑白两主题，MediaIndex保留原彩图标。

Android11兼容、灯带稳亮/防闪/重连、等比裁切及编码黑边处理明确标为1.0.4本地电视测试；未宣称通过实体TV/SP658E验收。正式下载仍指向GithubLatest，已核验为v1.0.0 /73。本轮不重发APK或修改正式Release。

类型检查/构建、SunnyUI2496组基础样式比较、真实背景像素位移/前景清晰/无空闲帧循环/跨标签/键盘与窄屏检查通过；SunnyTV本地320/390/768/1366宽度、无溢出、背景切换、同心圆角、黑白刷新、dialog/Esc和外侧不压暗检查通过。最终磨砂阅读层在可见浏览器人工复核。

仅向既有 `Z:/docker/myblog/sites/sunnytv` 写白名单12文件（约1.07MB），资源先行、首页最后原子替换。原文件备份在`.local-tests/website-glass-21/before-deploy`，没有清理其它NAS目录或重启服务。首次资源回读出现短暂404，后续35个当前引用/许可/源码文件全部HTTP200，完整SHA256与本地一致。新组件的完整可构建源码及两套许可随ZIP公开下载；SunnyUI的2.1安装包/Skill已在本地生成，未创建GithubRelease。

证据：`.local-tests/website-glass-21/browser-checks.json`、`deploy-manifest.json`、`http-verified.json`；SunnyUI的`docs/2.1.0-validation.md`。以下为历史部署记录。

# 2026-10-22 图文官网与 SunnyTV Symbols

本轮验证：192项单测零失败，Debug与AndroidTest编译通过，lint零错误/44项既有警告；54图标在20dp和40dp逐个渲染无裁切，实际播放器焦点与清晰度文字回归通过。最终API34原生截图测试通过，25个上线文件HTTP200且SHA256一致。

官网已经先发布一版图文重排：黑白主题默认白色，逐段配原生截图；MediaIndex恢复原彩图标，关闭嵌入模拟器和旧直达入口。白名单25文件 HTTP200且完整SHA256与本地一致。原文件在 `.local-tests/website-refine/before-deploy`。320 /390 /768 /1366宽度无横向溢出。

随后按用户要求重绘原生图标，并以同一组公开演示素材重拍对应界面。三推杆设置、叠放媒体卡片、实心播放/暂停/上下集和其它工具图标共54个，源数据与SVG位于 `design/icons`。网站本身不载入新依赖或模拟器脚本。玻璃特写使用原生组件更高绘制密度裁切，不做栅格放大；普通截图来自API34原生界面，完整素材与复现说明见 `website/README.md`。

原生玻璃与图标改动尚未形成新正式APK，现有v1.0.0 /73与标签/附件不变。网站已明确标注本轮开发外观预览。本轮只向授权站点发布实际引用的静态文件，不修改MediaIndex仓库、NAS媒体或服务。

以下为历史部署记录。

# 2026-10-22 新海报、黑白官网与原生玻璃展示

用户提供的新素材包替换此前界面演示素材。官网只有黑白两色，首次默认白色；嵌入网页模拟器固定柔软浮雕。保留 1.0.0 /73 内容与 GitHub Latest 下载，不更新应用版本或替换发行包。

公开资产包括七套海报 / 场景 / 透明片名标志，以及 API34 模拟器拍摄的原生首页、网格、玻璃首页和玻璃材质局部。特写使用生产 TopNav / RuntimeShader，中央定位仅用于材质演示，未给截图伪造效果。页面说明了 Android13 /API33、硬件加速、高开销主题及低负载限制；不符合时回退清晰默认。完整素材与复现说明见 `website/README.md`。

部署到既有 `Z:/docker/myblog/sites/sunnytv`。白名单 37 文件，约 12.9 MiB；资产先行、首页最后原子替换。旧版本文件备份在 `.local-tests/website-seven/before-deploy/`，不删除 NAS 其它文件，不重启服务。

检查：Debug /AndroidTest 编译成功；截图 instrumentation 测试通过。网页 320 /390 /768 /1366 宽度无横向溢出；搜索、详情、模拟播放暂停、黑白切换和父页遥控器可用。线上 37 文件 HTTP200 且完整 SHA256 一致，证据 `.local-tests/website-seven/http-verified.json`。初次并发检查出现短暂404，随后整套37文件复验全部通过。实体电视未新增验证，应用发行行为未变。

以下为历史部署记录。

# 2026-10-22 官网 1.0.0 内容更新已上线

用户最新要求先改官网内容，并认为本轮海报不合意。本次只发布内容与对应排版：1.0.0 /73、标语「精致入眼，流畅入戏。」、外观与播放修订、SP658E控制及适用范围、MediaIndex图标和GitHub链接、继续GPL开源说明、FAQ。图像沿用既有页面展示素材，本轮生成的7张海报、7张横版背景及新截图不发布。

仅部署首页实际引用的10个文件到既有 `Z:/docker/myblog/sites/sunnytv`，先写资源、最后替换首页；原文件备份于 `.local-tests/website-100/content-before`。每个线上文件HTTP200且完整SHA256与本地一致，320/390/768/1366实际iframe视口无横向溢出，下载链接保留GitHub Latest。证据 `.local-tests/website-100/content-http-verified.json`，线上截图 `live-content.png`。不修改应用发行包、NAS媒体或MediaIndex仓库，不重启服务。

以下为历史部署说明。

# SunnyTV 官网部署

线上入口：https://sunnytv.dunn.fun:666/ 。源码 `website/`，现有 myblog 发布器的 `sites/sunnytv` 静态目录，不新增托管服务。

本次采用更明确的首屏、大幅原生截图与分段产品说明，沿用 Sunny UI 外观令牌和许可。首页、海报网格、浅色主题截图来自 API30 模拟器内真实 Compose 页面，媒体名与海报为原创测试素材，截图方法见 `website/README.md`；不发布私有媒体、邮件附件或设备签名。

部署通过用户授权的现有挂载目录 `Z:/docker/myblog/sites/sunnytv`，先写资产再原子替换首页，旧文件在本地 `.local-tests/website-before-redesign` 备份，不清理 NAS 其它目录。线上文件需要逐一 HTTP 200 及完整 SHA-256 比对；网页不需要重启容器。反代沿用既有规则。

所有下载入口固定使用 GitHub `/releases/latest`，实际版本由发行的 latest 标记决定。
