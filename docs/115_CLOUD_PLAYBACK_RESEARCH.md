# 115 云端清晰度接入研究

日期：2026-10-13。状态：已完成代码与模拟联调；真实 115 播放待有效登录态验证，未部署、未发布。实现与验收见 [115_CLOUD_PLAYBACK.md](115_CLOUD_PLAYBACK.md)。下文保留初始研究依据，现状以实现文档为准。

## 基线与实测来源

- 当前接入评估基线是本目录 dev35.7（versionCode 46），HEAD `44e342d`。原 SunnyTV 目录仍是 dev33 开发覆盖层，不能用于后续实现。NAS latest.json 也标为 dev35.7，但这不证明用户设备已升级。
- 用户反馈：115 官方 App 的播放菜单可选 4K、1080P，选择后直接流畅播放。这支持验证云端已有流的方向，但不证明当前第三方接口返回同一编码、码率或权限。
- 用户提供的 base.apk：115生活 38.4.0，包名 com.ylmf.androidclient，versionCode 1409，140426070 字节，SHA256 B771DE1ABEAED1831C32AC9FD8E6CB4D18F8DB19EF108C886CB6CA8AE5D57B92。
- JADX 1.5.6 静态分析只得到 s.h.e.l.l 加载外壳；APK 包含 ijiami.dat、ijiami.ajm、IJMDal.Data，classes.dex 仅13904字节。未还原清晰度请求代码。REA Android 分析基于 JADX，不能将换工具等同于取得业务代码。

## 可核对的接口路线

主要源码依据：[p115client/client.py，固定提交856e4f8](https://github.com/ChenyangGao/p115client/blob/856e4f8f09ce034534bd458f06d3dee4474a1698/p115client/client.py)。这是第三方客户端的实现证据，不是38.4.0官方App流量证据，也不是接口稳定性承诺。本轮未复制其实现或加密代码进入应用。

| 路线 | 接口及输入 | 已知条件与限制 |
| --- | --- | --- |
| 开放平台 | GET https://proapi.115.com/open/video/play；pick_code，可选share_id | P115OpenClient.fs_video；使用开放平台授权体系。客户端代码涉及access_token、refresh_token、app_id。需核实应用准入、视频接口权限及返回的实际档位，不能借用其他应用身份。 |
| 网页视频信息 | GET https://webapi.115.com/files/video；pickcode或pick_code | P115Client.fs_video；返回视频信息及m3u8列表；源码注明未转码文件可能自动推送转码，queue_url可查询状态。因此不能把它当成绝对无副作用的只读探针。 |
| 网页多档清单 | GET https://115.com/api/video/m3u8/{pickcode}.m3u8?definition=0 | fs_video_m3u8注明必须Web Cookie；其他设备Cookie可能返回空内容；后续m3u8链接绑定取流UA。definition=0列出档位。 |
| Android客户端 | POST https://proapi.115.com/android/2.0/video/play；pickcode、user_id，local=0 | fs_video_app将JSON做RSA封装，以data字段提交，并对响应data解密；不能当作普通JSON POST。此封装尚未与用户APK运行行为核对。 |

网页方法注释的档位：1约480P、3约720P、4约1080P、5为4K、100为与原文件同尺寸。实际菜单必须以响应/清单为准；100不能据此解释成原文件直链或无损。当前NAS转码菜单的20/10Mbps等码率不可套到115流上。

开放平台方法还提到multitrack_list与audio_track参数，但不同路线的音轨语义和签名规则不可混用。字幕、HDR/DV、帧率、声道、码率及有效期均待实测。不能任意修改签名URL，也不能先承诺保留原片音轨或烧录字幕。

官方文档入口：https://www.yuque.com/115yun/open/hqglxv3cedi3p9dz 。本轮工具无法读取该页，开放平台权限与响应字段仍需官方文档或真实授权响应核实。

## dev35.7 的实际缺口

- SourceKind目前仅EMBY、CLOUDDRIVE，没有115授权和文件来源适配器。
- PlaybackRequest有stableUrl/sourceMediaUrl，但没有115账号引用、pickcode等稳定文件身份。MediaIndex /api/play/{token} 中的token不是pickcode；不得通过解码令牌、猜CDN路径或按文件名猜测来绑定文件。
- TranscodeSource仅识别MediaIndex稳定入口，能力、创建、暂停、续租、停止均使用NAS转码会话协议。
- PlayerActivity把所有非original选项视为MediaIndex会话，涉及URL、时长、时间轴、暂停、心跳、字幕、错误重试。因此增加115不能仅向固定菜单添加字符串。
- SafeHttp统一覆写UA，并按HeaderScope约束凭据。115取流和媒体读取必须使用一致UA；如果网关取流UA与SunnyTV不同，直接发回链接可能失效。

## 推荐接入方式：保留现有 Emby / MediaIndex / SunnyTV 链路

推荐将“115云端流解析”和“NAS FFmpeg转码”作为两个独立提供方。MediaIndex若已有正确的账号与文件映射，可负责把自己的asset token解析成115文件身份，获取真实可用档位；SunnyTV复用清晰度面板播放云端HLS。115流无需NAS重新编码。

用户随后明确授权两个项目的增量实现。本轮已在独立分支扩展 MediaIndex 网关并接入 SunnyTV；未改变既有 STRM 签名契约。

网关扩展应新增独立能力协商，不改写现有STRM/签名契约，不把115冒充mediaindex-transcode-v1的FFmpeg会话：

1. 以现有受授权asset身份查询可用提供方与档位，返回稳定但不含云盘凭据的variant ID、显示名称、实际尺寸、码率（未知则省略）和状态。
2. 用户选定后解析短期播放资源，明确delivery、时间轴类型、过期/刷新策略、是否需要心跳/停止、字幕/音轨能力。这里是拟议字段，不是已存在接口。
3. 优先评估客户端直接读115 CDN。须证明固定UA可贯穿链路，清单、子清单、分片及密钥请求不需要转发账号Cookie/Authorization，且重定向符合现有策略。
4. 如果必须服务器取流，采用受限HLS代理，包括清单中所有URI类别及Range，不运行FFmpeg。该方案仍占NAS带宽，不能宣称绕过NAS网络瓶颈。代理不得成为任意URL开放代理。
5. 无可用云端档位时提供明确原因，由用户选择原片或NAS转码，不静默切回高码率原片，不自动付费加速或反复提交转码任务。

客户端可在原清晰度面板中区分“原片”“115云端 · 4K/1080P”“NAS转码 · 分辨率/码率”；只列出实际可用项。保留默认原片偏好，是否自动优先云端另行确认产品行为。

## 仅修改 SunnyTV 的备选

新增原生115来源适配层、授权存储和扫码流程，来源层持有账号引用及pickcode，播放器只获得播放资源。优先验证开放平台，Web路线作为受控实验；不把Cookie交给播放器或写进Intent/日志。

这可以支持直接浏览115来源，但不能自动解决既有Emby/STRM条目到115文件的映射。要覆盖用户当前的选片路径，仍需一个明确可信的映射协议或用户选择对应文件，不能宣传为只登录115就能为任意Emby影片提供云端清晰度。

## 客户端实现分解

1. 在来源层抽象清晰度提供方及播放资源，将MediaIndex会话生命周期与普通云端VOD分开；沿用现有API以保持兼容，不先做大范围播放器重构。
2. 菜单异步发现需取消旧请求并校验当前影片身份，防止换集后旧结果回填；标签来自实际variant，不使用NAS预设码率推算115码率。
3. 切换前保留绝对位置、暂停状态及当前播放资源；先解析并校验新资源，再替换播放器媒体。失败保留/恢复原选择，不能把quality改成original却没有可用播放器。
4. 云端完整VOD使用原生全片seek，无MediaIndex窗口偏移、暂停API或租约心跳。过期刷新限制次数，保留播放位置；认证失败要求重新授权。
5. 保持同一Emby观看会话和进度回报，云端转码不冒充Emby服务端转码。重新获取实际音轨/字幕，不沿用原文件的轨道序号，不默认隐藏所有外部字幕。
6. 所有网络请求可取消、严格TLS、有界响应和重定向；API鉴权不扩散至CDN。签名URL不落日志、不写文档、不长期缓存。清单可含多级相对URL、密钥、初始化片段，不能只检查顶层地址。

## 下一步验证与验收

先在本机受控授权环境取得同一影片的脱敏响应与清单结构，不要求用户把Cookie贴到聊天。记录实际宽高、视频/音频编码、HDR、帧率、码率、音轨字幕及过期表现；播放链接本身不进入记录。对比官方App、云端流和NAS转码的首帧、缓冲、拖动与连续播放，不以同为“4K”推断同等画质。

验证矩阵：1080P/4K实际可用和缺档；未转码/排队/权限不足；非Web登录态；链接失效；主/子清单与分片UA一致；多级重定向、跨域鉴权剥离、TLS降级拒绝；取消发现、连续切换、换集及后台恢复；暂停切档、长距离seek；音轨/字幕变化；原片、Emby、CD2、STRM回归。

后续已完成实现、Android 构建与模拟器测试。只读账号探测返回登录过期（990001）；用户选择“先完成代码与模拟测试，暂不登录”。没有 NAS 部署或 APK 发行，不能保证官方 App 所有清晰度在第三方登录态下可用。
