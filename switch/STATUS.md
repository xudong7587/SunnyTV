# Switch 初始开发状态

基线：SunnyTV `v0.2.8` / `dcc09409c98f0819b63f763913e57cc822fdaec6`。

- 已写入：C++ 独立客户端、Emby 适配、STRM/HLS 网络策略、云端会话、SDL/libmpv 接入和独立 NRO 工作流。
- 宿主机：52 项核心检查通过，涵盖 JSON、UTF-8、URL/作用域、STRM、HLS、Emby 直放、后备选择和进度。
- Switch 交叉编译：待运行。
- 服务联调：未进行，无用户服务器凭据。
- Switch 真机：未进行。

当前工作区没有终端网络访问，不能直接下载 SDK/portlibs。通过已授权 GitHub 接口读取 v0.2.8；构建将由独立 Actions 分支执行。安卓工程未修改。
