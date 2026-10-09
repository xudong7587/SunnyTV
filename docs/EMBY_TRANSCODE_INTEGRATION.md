# 同一清晰度菜单接入 Emby 转码

2026-10-08：用户确认保留 MediaIndex + SunnyTV 架构，并提出为已有 Emby 转码能力的用户复用清晰度按钮。本文为后续实现约定；dev35.2 尚未接入 Emby 转码。

## 用户行为

- 默认原画，继续既有直放、STRM / 302 路径。
- 复用原画、4K / 2160P 20 / 10 Mbps、2K / 1440P 10 / 6 Mbps、1K / 1080P 8 / 4 Mbps、0.75K / 720P 4 / 2 Mbps 菜单。
- Emby 来源允许选择「自动 / MediaIndex / Emby」转码服务。自动优先使用可用的 MediaIndex 插件，未配置时尝试 Emby；播放中不静默切换服务或回到原片。
- 原片尺寸低于目标时不放大，仍可降低码率或转换不兼容编码。按钮根据实际输出视频尺寸显示。
- Emby 本地文件可由 Emby 读取；网盘 / STRM 必须确认 Emby 自己能访问媒体源。转码流由服务器中转，手机不获取网盘 Cookie。

## 服务协议

现有 EmbySource.playback 显式 EnableTranscoding=false，不能只放开 UI。增加独立的转码协商入口，保持原画分支行为。

向 POST /Items/{Id}/PlaybackInfo 传入 UserId、MediaSourceId、StartTimeTicks、音轨和字幕选择、MaxStreamingBitrate，以及 EnableTranscoding=true。明确申请降码率时禁用 DirectPlay / DirectStream 和视频流拷贝；DeviceProfile 的 HLS H.264 / AAC TranscodingProfiles 限制 MaxWidth / MaxHeight，不伪报设备支持全部编码。视频码率与音频开销分开，总流量上限需计入音频。

以服务端 SupportsTranscoding、ErrorCode 和 TranscodingUrl 为协商结果，使用返回 URL，不自行拼接原片流地址或通过会员标志猜测成功。根相对 URL 通过现有源适配器解析；继续复用严格 TLS、来源范围鉴权、有限重定向和不记录签名 URL 的策略。

切换保留当前进度、暂停状态、音轨和字幕选择；发送旧会话停止、新会话开始及 PlayMethod=Transcode 回报。退出和再次切换释放本次会话；拖动遵守 Emby 时间轴及会话语义，不能照搬 MediaIndex VOD 的偏移规则。

特效 / 位图字幕请求服务端 Encode，已经烧录的字幕不在客户端重复绘制。服务器拒绝转码时保留当前播放并给出可理解错误。

## 部署和验证

302 插件 / 反代必须放行 Emby 转码清单、分片及相关协商请求；具体规则取决于用户插件，不能假设请求参数天然绕过重定向。验证实际返回为 HLS、分片来自服务器、后台有对应转码会话，防止名为低码率实际仍获取原片。

验证覆盖：本地文件、可访问网盘 STRM、无转码权限、无 TranscodingUrl、反代错误重定向、服务端失败、切换恢复、拖动、字幕烧录及内外网。先执行 HTTP 协商与状态回归，再以真实 Emby 服务和终端验收。

## 官方依据

- https://dev.emby.media/reference/RestAPI/MediaInfoService/postItemsByIdPlaybackinfo.html
- https://emby.media/support/articles/Premiere-Feature-Matrix.html

硬件加速转码属于 Premiere 功能；软件转码、用户转码权限及服务端配置分别判断。不能把没有会员直接等同于所有转码均不可用，也不能承诺有会员就必定能读取任意网盘来源。
