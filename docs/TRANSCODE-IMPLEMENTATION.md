# 独立转码与清晰度选择：本地候选

日期：2026-09-30。基于当前dev32工作区增量开发，不更改版本号、不发布、不同步NAS升级目录。
MediaIndex从v0.7.34建立独立分支，保留主工作区模拟器及用户已有改动。用户在本次对话中明确要求跨仓开发，构成本功能对原先禁止修改MediaIndex规则的授权例外。

## 已写入代码

- 菜单：原画、4K / 2160P、2K / 1440P、1K / 1080P、0.75K / 720P。
- 原画保持已有播放路径；选择其余选项才请求MediaIndex。分辨率上限为3840×2160、2560×1440、1920×1080、1280×720。后端保持宽高比且不放大，不生成媒体库副本。
- `source/transcode/TranscodeSource.kt`负责接口地址、能力查询、会话创建、暂停、续租、停止；界面不拼接接口URL。只有明确匹配 `/api/play/{token}` 的无query稳定地址才识别为候选；先核对协议标识，不把CDN地址猜成MediaIndex服务。
- 返回的播放列表必须是同一协议/主机/端口、精确会话路径且只有一个会话参数；不接受跨站、userinfo或额外query。转码API与视频客户端不附加Emby/CD2凭据；原画沿用原有凭据范围和TLS策略。
- 播放器切换时保留绝对位置及播放/暂停状态，先释放旧转码会话，再申请新会话。原画可回退；loading及错误页均可打开清晰度选项。Emby保持同一观看会话，不将独立FFmpeg转码伪装成Emby原生转码API。
- 明确申请`delivery:vod`，返回完整点播时间轴；进度条、快进快退、章节、片段跳过和触摸拖动使用播放器原生HLS跳转，worker仅生成目标附近最多16秒范围。暂停时跳转同步更新记录位置。旧滚动HLS模式仍兼容，使用窗口偏移与会话重建。
- VOD暂停保留会话，当前有限批次允许完成，继续时沿用同一会话；闲置一小时回收。旧live模式冻结worker，继续时重建。进入后台释放；容器端超时作为异常退出的回收保障。
- `PlaybackRequest.sourceMediaUrl`是可选字段，保存Emby提供的远程来源。当用户偏好Emby服务端播放时，仍可识别MediaIndex转码能力。现有CD2/STRM调用保持默认兼容。

## 通用播放器

标准HLS播放链接可交给其他HLS播放器。MediaIndex另支持 `profile:adaptive`：VOD按实际请求档位生成对齐分片；旧live模式一次解码四路同时编码。SunnyTV只申请用户选择的一路。

显示“1080P”还是完整标签由播放器决定；服务端用实际 `RESOLUTION` 与 `BANDWIDTH` 描述，不依赖显示名称匹配。
绿联影视中心、Moonfin是否将此主列表映射到现有画质菜单尚未实测；此阶段没有自动接管它们原有的码率或画质接口。

VOD提供完整时长，普通HLS播放器可原生全片拖动；创建会话和读取列表不会立即启动编码。每批4片、每片4秒，统一30 FPS，不保存整片副本。闲置超过一小时需重新取得链接；稳定STRM转码入口尚未实现。单会话只编码一个批次，同时请求不同档位/批次可能取消旧请求，实际播放器的预取与自适应行为尚需验证。

## 验证和剩余验收

已运行Kotlin契约测试172项通过、JUnit89项通过（转码适配器新增4项）、Android debug编译/构建通过，lint为0错误/31项既有警告。测试覆盖候选URL识别、固定菜单名称、稳定profile与位置传递、无Emby凭据请求、跨站播放列表拒绝、Emby服务端播放偏好保留远程来源。

VOD改动后重新运行JUnit89项、debug构建及lint（0错误/31警告）。MediaIndex后端1350项通过、2项既有跳过、58个subtests通过；真实软件FFmpeg用24/29.97/30/60 FPS合成源验证跨批次时间戳、尾片时长与音频时长。软件参考验证不代表VAAPI硬件或电视通过。

MediaIndex独立worker的本地API测试使用模拟云盘/FFmpeg进程，验证真实网关与worker的HTTP契约；不能用它宣称硬转或端到端视频播放成功。
本机无连接的Android设备、Docker CLI或LinuxGPU节点；未执行TV界面/遥控焦点实测，2026-09-30已按用户授权在NAS构建并运行独立测试网关和转码worker。NAS单路硬件转码和服务端分片跳转已通过下方测试；HLS音画同步、客户端切换/拖动/暂停和实体电视起播仍待验收。识别出的PQ/HLG来源暂拒绝，尚不支持HDR色调映射；首期转码只使用第一条音轨，暂不保留字幕。

相关：[开发需求](TRANSCODE-REQUIREMENTS.md)、[原始排查证据](PLAYBACK-8K-AV1-2026-09-29.md)。

## NAS 测试版 dev33（2026-09-30）

- 使用现有签名证书，versionCode 33；JUnit 90项通过，release构建通过，lint 0错误/31项既有警告。
- 编译属性 mediaindexTranscodeTestOrigin=http://192.0.2.10:38021/（文档示例地址），仅将清晰度转码API指向同一NAS的测试网关。正常构建默认属性为空，原画仍使用原始STRM/302路径。测试转码要求电视能够访问该局域网地址。
- 升级包 SunnyTV-v0.1.0-dev33-1782d73fc03f.apk 已通过分发脚本同步升级目录，latest.json选择dev33；SHA256：1782d73fc03fd9442b01cd3a4a9f44b5d1304331f658a34201c16b00082cc9b0。
- NAS真实8K AV1样本已通过1080P/4K首片、160秒跳转、全时长VOD列表及会话停止清理；另通过4K前36秒跨三批连续分片读取及160秒跳转，实际输出H.264 + AAC；仍待实体电视起播、音画同步、暂停及遥控验收。建议先选1K / 1080P测试，再测试4K。
- MediaIndex运行时枚举GPU并实际探测VAAPI能力；Intel与AMD有选择逻辑，目前只有这台Intel NAS实测，NVIDIA后端未实现。测试部署独立于现有生产MediaIndex，未发布Git RC。
# 2026-10-08 对齐 main 后继续转码开发（未发布）

开发入口为 `D:/Documents/ChatGPT/SunnyTV-transcode-main`，分支 `codex/transcode-main-alignment`，基线 `origin/main` @ `5f7ca4c`（dev34.3，versionCode 37）。原 SunnyTV 工作区的未提交开发覆盖层未修改。

在最新 main 上整合原 dev33 的清晰度菜单、MediaIndex API 客户端和 HLS VOD 时间轴；保留 Media3 1.9.4 官方 FFmpeg 本地音频解码优先渲染器、原画精确音轨选择、字幕搜索下载和片尾修复。替换播放请求时释放转码会话并重新识别当前来源，避免沿用旧请求的播放令牌。转码后的音轨不套用原片的音轨序号。

正常构建不设内网测试 origin；仅明确设置 `mediaindexTranscodeTestOrigin` 时覆盖转码 API 地址。此次未改 NAS 部署、升级目录、发行版本或 GitHub。MediaIndex 转码工作区同步至 `github/main` @ `dbc4f2c`（v0.7.37），保留全部转码改动，无数据库变更。

验证完成：Gradle testDebugUnitTest、lintDebug、assembleDebug通过；JUnit93项无失败，核心契约172项通过；lint为0错误/32警告。调试APK核验四种ABI的FFmpeg JNI及两份许可证。实体设备播放、NAS新版本联调和发布未执行。 MediaIndex转码定向28项通过。


本轮边界：播放器、source/transcode，以及最小的Emby适配器和PlaybackRequest可选字段契约。sourceMediaUrl仅保留服务端返回的远程入口，不改变原画路由，也不向转码服务附加Emby凭据；既有调用默认值为null。转码字幕仍未实现，字幕搜索入口保留在原画播放中，避免字幕操作静默切回原片。

本地工具链：安装NDK 26.1.10909125，复用已发布dev34.3的FFmpeg音频静态库和固定源码归档，先核对SHA256，再按上游脚本选项生成构建头文件并重编JNI。上述工具、源码和静态库均在忽略目录中，不进入Git。
