> 0.2.1更新：已移除连接表单类型切换/外链，添加按钮常显文字；绿联本地完整目录排序和MediaIndex原始入口条件复用已完成测试。真实115 CDN样本仍缺原始MediaIndex授权映射，不能据直链启用云端档位。版本已同步WebDAV，验证和边界见[0.2.1](RELEASE-0.2.1.md)。以下为实施历史。

> 最新状态（2026-10-15）：绿联适配已整合到dev36.4基线并以 **0.2.0 / 52** 同步WebDAV。保留新版音频/115/字幕功能，新增绿联字幕搜索下载能力限制；核心172、单测132、Android定向30通过。详见[0.2.0交付](RELEASE-0.2.0.md)。下文“未发布”等为此前实施记录。

# 绿联影视中心 Emby 兼容接口核对

日期：2026-10-15。依据绿联 2026-10-14 邮件附件《Emby 兼容接口第三方播放器对接指南》，对照当前工作区源码并只读检查用户 NAS。已通过独立账号登录、媒体目录及真实视频 Range 联调；TV 播放验收仍未完成。下表中的“当前工作区”描述首次静态审查时的状态，后续实现见末尾记录。

附件在 `.local-tests/ugreen-emby-integration-guide.md`，属于本地参考资料，未纳入官网或发行包。附件内别的联调设备信息不是用户设备地址，不直接探测或使用。

## 结论

可以复用现有 Emby 架构和多数基础解析，但不能把当前版本不改动就当作完整兼容。建议增加明确的绿联兼容来源配置或能力集，保留标准 Emby 行为；优先修正相对播放 Path 和首页查询，再按支持范围调整功能入口，最后用授权测试环境验收。

## 可复用的部分

| 能力 | 当前实现 | 判断 |
|---|---|---|
| 账号登录 | `EmbySource.login()` 使用 JSON Username/Pw，保存 User.Id 与 AccessToken | 与指南一致。地址需手动带实际 HTTPS 入口及 `/emby/`；不能使用 NAS 管理员账号代替开放接口账号 |
| 鉴权与图片 | API 和播放作用域使用 X-Emby-Token；图片按现有作用域携带凭据 | 基础合同相符。公网图片不能依赖匿名访问，需实测重定向和缓存 |
| 媒体库及详情 | Views、Users/{user}/Items、用户级详情入口 | 路径一致，ID 以字符串传递 |
| 最新列表解析 | 同时接受数组与对象 | 能接受指南规定的 Latest 顶层数组 |
| 季与单集 | Seasons、按 SeasonId 请求 Episodes、选择真实 EpisodeId | 核心结构相符。季详情本身不保证可用，需验证 UI 是否额外请求 |
| 播放会话 | POST PlaybackInfo，保留 MediaSourceId / PlaySessionId | 结构可复用，URL 选择仍须修正 |
| 进度事件 | POST Playing / Progress / Stopped，tick 毫秒换算，空 204 不解析 JSON；PlaybackReporter 串行提交 | 基础事件相符；总时长字段缺失，见下表 |
| 收藏结果 | 顶层 UserData 解析 | 电影/剧集可复用，但按钮支持范围须限制 |
| 安全传输 | 有界重定向、来源作用域凭据、严格 TLS | 保留，不为兼容关闭证书验证 |

## 必须修正或验证的差异

| 项目 | 指南约定 | 当前工作区与影响 | 建议 |
|---|---|---|---|
| **相对播放 URL** | MediaSources.Path 可为 `/emby/videos/{id}/stream.mp4`，保留 query，按 Origin 解析 | `playback()` 只把绝对 HTTP(S) Path 视为可用 directPath；相对 Path 会退回 DirectStreamUrl 或自行生成 `Videos/{id}/stream`，与指南给出的流路由不同，存在无法播放风险 | 将服务提供的 HTTP(S) 或安全相对 Path 解析为实际流地址，保留 query 和 token 作用域，不把 NAS 文件系统路径当 URL |
| Latest 父级 | ParentId 必填 | `home()` 调用 `latest()` 不带父级；单库 latest(parent) 有父级 | 首页先取 Views，再按授权库获取并有界汇总；不能把首页候选或 Latest 当完整目录 |
| 首页 NextUp | 必须传 SeriesId，且最多一条候选 | `nextUp()` 无 SeriesId，可能固定为空 | 按具体剧集调用或关闭不支持的全局 NextUp 行；下一集继续使用 Episodes 实际列表 |
| RunTimeTicks | 已知正时长时持续上报，以计算完成度；95% 标记已看 | `report()` 没有 RunTimeTicks | 从真实播放时长携带字段，不编造时长；实测进度及已看阈值 |
| 合集 | Folder 是合集；需不传 IncludeItemTypes 或显式 Folder；点击以其 ID 查成员 | 普通库请求 Movie,Series,Video；文件夹模式使用 IsFolder 而未明确 Folder；`children()` 只处理 Series/Season | 绿联模式按指南分支请求和处理 Folder，不能当可播放条目 |
| 季详情与图片 | 季详情未承诺，季封面建议父剧 | 通用 item(id) 会请求详情，parseItem 对父图回退依赖字段 | 绿联季页直接基于 Seasons 返回数据与父剧信息，验证无季详情仍可选集 |
| 片头片尾 | GET MediaSegments/{真实EpisodeId} | 当前从详情 Chapters / MarkerType 生成分段 | 增加绿联 MediaSegments 适配，空集合不阻止播放 |
| 人物详情 | Users/{user}/Items/{PersonId} | person(name) 走 Persons/{name} | 使用已返回 PersonId；不从名字猜 ID |
| 搜索与排序 | SearchTerm、普通 IsPlayed、大多数排序与 SortOrder 未承诺 | 通用 UI 会发送这些参数，200 可能被误认为已生效 | 关闭对应入口或明确客户端已加载范围筛选，不伪装为全库搜索/排序 |
| 收藏与手动已看 | 收藏限数字电影/剧集；手动已看支持数字电影/剧集和 SeasonId，单集未承诺 | setFavorite 无类型限制；setPlayed 要求 isPlayable，可能对单集开放 | 依据能力与资源类型限制 UI，不用父剧标记代替单集操作 |
| 外挂字幕 | 元数据不等于提供字幕下载、转换、烧录 | externalSubtitles 会在缺 DeliveryUrl 时构造通用字幕路由 | 绿联模式只使用明确交付并可用的地址；外挂字幕需专项确认 |
| 相似推荐、刷新等 | 未列为基础合同 | 通用适配器有 Similar、Refresh 与删除等请求 | 绿联能力集默认关闭未承诺功能；本次不执行任何媒体管理操作 |
| ServerId | 建议保存 ServerId 以隔离状态缓存 | 登录结果未保存 ServerId | 当前 config.id 可区分本地来源；如扩展入口合并或跨地址识别，需补存真实 ServerId |

## 下一步联调所需

绿联专项评估后确认：用户设备上的开放接口账号、授权媒体库、实际 HTTPS 端口、内外网入口、证书和最低系统/影视中心版本。设备签名不是影视 API 授权完成的证据。测试顺序：登录 → Views → 按库浏览 → 电影详情 → 原始 Path Range 播放/拖动 → 多季选集 → 真实播放进度 → 支持范围内收藏/已看 → 内外网各自验收。未授权的写入不测试。

## 验证边界

首次审查仅完成附件下载和源码静态核对；本日后续增量见下文。官网不宣称已支持绿联。

## 2026-10-15 设备检查与首批适配

### 设备入口与签名用途

- 邮件明确将设备签名和影视中心专项对接分开。官方[开发准备](https://developer.ugnas.com/doc/backend/quick-start/prepare.html)将 `ugdev.sig` 用于 NAS 应用安装测试授权；本次未上传、安装或激活签名。
- 用户现有 DXP4900 的影视中心为 1.20.0.0036。只读检查确认 `video_serv` 服务与官方 nginx `/emby/` 路由；无 token 请求 `System/Info` 返回 401 / Unauthorized。本地 Unix socket 的结果仅证明路由及鉴权存在，不是实际 TLS 客户端联调。
- 通过 NAS 已登录界面进入“影视中心 → 设置 → 服务管理 → 媒体服务器”，初始开关关闭；用户自行开启并设置了独立媒体库账号。页面确认实际内网地址 `192.168.11.239`、端口 `9443`。没有从数据库或配置文件提取密码。
- 现有域名 `https://4900.dunn.fun:666/emby/System/Info` 也以正常证书校验完成 HTTPS 请求并返回 401。未新增公网代理或修改影视服务配置。这个结果还不能证明域名入口登录、Range 和播放全部可用。
- 官方[连接 VidHub 说明](https://support.ugnas.com/detail/article/zh-CN/908)公开介绍此功能，标注固件 1.18.0.0075 及以上适用，要求在媒体服务器设置中创建专用账号。由此可知它是固件已有能力；不能据本次开发申请认定功能是临时下发，亦不能据此推断所有版本的灰度开放情况。

### 首批代码变更

- `EmbySource.playback()` 在没有 `DirectStreamUrl` 时使用服务返回的相对 `Path`：要求 `Protocol=Http`、解析后仍处于 API 来源及路径作用域，并排除网络路径、文件协议和路径穿越。保持原始 query；不因 `stream.mp4` 后缀覆盖实际 Container。不改变标准 Emby 远程 HTTP 源与服务端兜底的优先关系。
- `PlaybackRequest` 新增默认 0 的 `durationMs`，取所选 MediaSource 的正时长，否则使用条目已知时长。播放事件在时长有效且 tick 换算不会溢出时携带 `RunTimeTicks`；不改变事件队列顺序、重试或远端用户状态写入策略。
- 新增 `UgreenEmbyContractTest` 5 项 MockWebServer 测试，覆盖原始相对流地址/重复 query/容器、拒绝文件和越界引用、显式流优先、真实 EpisodeId/会话 ID/204 与时长、未知时长省略及回退。

### 本地验证与剩余事项

- 纯 Kotlin 核心契约：172 通过、0 失败。
- Gradle debug 单测：95 通过、0 失败，包含新增 5 项及现有 Emby/CD2/STRM 测试。
- Android debug APK、lint：`testDebugUnitTest assembleDebug lintDebug` 完成，BUILD SUCCESSFUL；lint 0 错误 / 31 警告。未做电视仪器化或真实播放器验收。
- 真实设备已通过独立账号登录、媒体库、选集、PlaybackInfo 和视频 Range，详见下文。未进行 TV 解码、拖动及进度回写验收。本地临时验证页只保存不含密码/token 的检查结论，不调用播放事件、收藏、已看或媒体管理写接口。
- 仍需实现或确认按库 Latest、绿联能力集（全局 NextUp/搜索/排序/管理操作）、MediaSegments、季/人物分支、外挂字幕范围，不能将首批两处修正当成完整绿联支持。
- 未更改版本号、签名、发布 tag，未发布或同步升级 APK。

### 专用远程入口

- 用户要求独立反代，发现 `media.dunn.fun:666` 现有目标为 MediaIndex 的 38000；经用户选择，新增域名改为 `ugmedia.dunn.fun:666`，保留 MediaIndex 规则。
- Lucky 主 HTTPS 666 服务子规则 No.118“绿联影视中心 API”，当前后端 `https://192.168.11.239:9443`，原样保留 `/emby/` 路径。用户已确认使用“9443 + HTTPS”，随后单独批准仅此内网后端忽略自签名证书验证，已保存生效；客户端侧仍严格验证公网域名证书，未修改 Android TLS 策略。
- 旧管理域名的 HTTP 9999 后端未保留服务要求的原始 HTTPS 识别，真实登录返回 403，不能以此前 GET 的 401 推断登录可用。只读空账号探测：HTTP 9999 未带代理 HTTPS 标识为 403，带 `X-Forwarded-Ssl: on` 则为 400（空账号校验）；这是排查证据，不是通过有效账号的登录。
- 中途 HTTP/9443 实际返回 `The plain HTTP request was sent to HTTPS port`；协议修正后因后端自签名证书校验失败返回 502。在用户批准单条证书例外后，新入口无凭据 `System/Info` 返回 JSON 401，独立账号登录则返回 200。
- 临时本地验证工具页面 `http://127.0.0.1:18764/` 的目标固定为 `https://ugmedia.dunn.fun:666/emby/`。localhost 是工具所在电脑，不是 NAS 地址。密码由用户在工具表单输入，只通过严格 TLS 发送至其自己的专用域名；不落盘、不打印。登录及 API 不跟随跳转；视频 Range 测试遵循有界 HTTPS 跳转，绿联 token 仅发往当前 `/emby/` 作用域，其他来源只带统一 UA 和 Range。临时会话只保留于测试进程内存，完成后停止进程清除。

### 真实联调结果（2026-10-15 10:09，本机时间）

- 独立账号登录、System/Info、Views：200，读取到 8 个授权媒体库。
- 抽查前三个媒体库的 Items 和带 ParentId 的 Latest：200；Latest 为顶层数组。
- Resume、Seasons、按季 Episodes、用户级条目详情、PlaybackInfo、MediaSegments：200。
- 选中单集的实际 MediaSource 没有 Container 字段；Protocol=Http，Path 与 DirectStreamUrl 均为同一个 115 CDN HTTPS MKV 直链，DirectPlay/DirectStream=true，无 RequiredHttpHeaders。不能将这个样本直接等同于指南的相对 Path 示例，也不能把所有库都推断为这一形态。
- 提供的 CDN 播放地址：Range 返回 206，读取 65536 字节，MKV/EBML 文件头 `1a45dfa3`。
- 指南约定的 NAS `videos/{实际单集ID}/stream.mp4` 路由：Range 返回 206，读取 65536 字节，相同 MKV/EBML 文件头。未记录签名 URL、token、密码或私有媒体正文。
- 结论：现有 Emby 认证及目录模型可复用，元数据与视频字节链路已通。以上是 Python 按契约做的真实 API 联调，**不是 Android EmbySource 端到端执行、完整文件解码、TV 运行或进度写回验收**；完整绿联能力集、首页等仍须实现和验证。
- 联调结论保存在忽略目录 `.local-tests/ugreen-live-probe-result.json`，界面证据 `.local-tests/ugreen-api-live-result.png`；不进入发布包。

## 2026-10-15 原生连接引导与兼容能力（本地未发布）

此节更新前文“首批适配”的待实现状态；真实设备证据仍以此前独立工具的联调为准。

- 设置 → 媒体来源新增“＋绿联影视中心”；首次连接也可选择该类型。原生表单说明“影视中心 → 设置 → 服务管理 → 媒体服务器”的开启路径、媒体库专用账号与 NAS 管理员账号的区别、版本升级检查，并提供官方图文说明链接。不会替用户开启 NAS 授权或创建账号。
- 绿联地址要求 HTTPS，只接受服务根地址或 `/emby/`，自动补齐一次路径；支持外部反代端口，不接受 NAS 桌面页面、凭据或 query。证书校验保持严格。界面不硬编码个人域名、设备地址或账号。
- `SourceConfig` 保存 STANDARD / UGREEN 类型和实际 ServerId；来源 JSON 缺少或不认识类型时回落 STANDARD，已有 Emby 不自动改成绿联。密钥继续经 Android Keystore 加密；曾按普通 Emby 添加的绿联来源需重新选择绿联类型连接。
- 首页按授权 Views 的媒体库逐库获取 Latest（最多24库、每库6项、并发3、去重后最多48项），保留库顺序，不伪装成全局时间排序；单库货架异常不抹掉其他库。全局 Resume 保留；库内 Resume、全局 NextUp 和相似推荐不发送未承诺请求。
- 合集 Folder 进入目录，以其真实 ID 为 ParentId；列表固定名称升序。搜索页明确说明只筛选当前已加载内容（包括已有拼音匹配），不发送假全库 SearchTerm。推荐使用一个有界列表的本地打散，不声称服务端全库随机。
- 季沿用 Seasons 结果及父剧图像，不请求未承诺的季详情；选集请求流字段。人物详情使用服务返回的 PersonId；片头片尾使用真实 ItemId 的 MediaSegments，过滤无效区间，缺失或失败不阻止播放。
- CDN 直链优先保留；绿联独立 NAS `videos/{真实ItemId}/stream.mp4` 作为回退或“经服务端读取”入口，不把站外 DirectStreamUrl 误当 NAS 回退。保留指南相对 HTTP Path 和 query；容器缺失时交给 Media3 识别真实格式。外挂字幕只接受明确 DeliveryUrl，不猜测通用字幕接口。
- 收藏只对数字电影/剧集 ID 开放；手动已看仅开放电影，保持 SunnyTV 不提供整剧/整季批量已看操作的既有约束。单集仍可通过真实播放事件更新进度。刷新、删除等未承诺操作在 UI 和适配器双层禁止。
- 播放事件保留串行 start/progress/stop 顺序；播放器读到真实时长后补充 RunTimeTicks，未知/无效时长不编造，不在预取时上报 Playing。新增 HTTP 单测验证真实单集 ID、会话、204、顺序及后续时长。

验证结果与实体电视待验范围见 STATUS；本轮不更新版本号、签名、发行 tag 或 NAS 升级目录。私有指南、签名、测试截图和临时联调结论不进入发布包。

### 本轮执行验证

- 纯Kotlin核心172/172、Gradle debug单测106/106（原5项绿联合同、10项profile API、1项串行播放事件）；标准Emby/CD2/STRM回归包含在现有单测内。
- Android debug与androidTest APK构建、lint通过（0错误/31警告）。新增遥控器测试曾暴露焦点请求早于弹窗窗口创建，现将请求放到弹窗自身组合中并验证修复；未禁用检查。
- API30模拟器定向8/8：绿联入口和说明、首次表单切换、遥控器说明→帮助→所有字段→保存、加密来源往返/旧配置兼容、绿联能力与合集导航5项，以及既有Emby表单IME/遥控器2项和播放器面板焦点1项。截图已视觉检查。没有在用户连接的手机上安装或运行测试。
- 仅本地debug构建；未做本次真实NAS原生登录/播放/用户状态写入，未宣称完整TV验收，完整既有失败清单未撤销。
