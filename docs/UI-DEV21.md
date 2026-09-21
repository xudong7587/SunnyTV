# SunnyTV dev21 — Home transition / shelf focus / player icon refinement

## 基线

本轮以用户已在真机验证的 `0.1.0-dev20` 完整源码为唯一代码基线；不升级 Gradle、Compose、Media3，不改 Emby / STRM / 播放协商协议。

## 首页 Hero → 我的媒体库

TV 横屏不再通过 LazyColumn 从 Hero 滚到下一项。Hero 与首页内容改成两个始终完成布局的全屏层：Hero 位于当前视口，首页内容位于正下方一个完整视口。进入首页后，“我的媒体库”首屏已经组合/测量，媒体库封面正常发起图片请求，同时最多预取 6 个媒体库的 latest 10 条数据。

按 Down 时只改变两个 layer 的 `translationY`；动画结束后再交接到第一张媒体库卡片。Hero 高度等于当前视口高度，因此静止状态下不会再露出“我的媒体库”标题。向上返回 Hero 使用相反的同层平移，内容完全隐藏后再把下页列表静默归零，为下一次向下切换做准备。

手机/紧凑布局继续沿用 dev20 的原 LazyColumn 结构，避免扩大修改面。

## 首页媒体行切换

此前 `HomeFocusNavigator` 先完成纵向滚动，再请求下一行焦点；因此下一行卡片宽度动画天然晚一拍。dev21 对已组合的相邻区域先请求目标焦点，然后同步开始纵向滚动；`StableVerticalViewport` 在导航过程中继续阻止系统额外 bring-into-view。

`TvAccordionCards` 增加本地视觉选中索引。同一窗口内的左右移动会在同一个按键帧更新展开状态并请求目标 FocusRequester；只有发生窗口换页时才等待下一帧重新映射 requester。进入/退出展开动画缩短，Artwork 切换也降低到更短的交叉淡入。

## 播放器

- 快退 / 快进：改为逆时针 / 顺时针环形箭头，中间直接显示当前 seek 秒数（默认 10）。
- 倍速：按钮内部只显示 `1.0x / 1.25x / 1.5x / 2.0x`，移除色块与仪表装饰。
- `PlayerSheet` 的关闭图标仍保留 `contentDescription=关闭`，但不再显示聚焦文字浮层。

## 验证

- `scripts/test-core.sh`：114 / 114 passed。
- `scripts/check-project.py`：Android XML、Python 脚本、原生资源检查通过；基线中 `run-dev15-emulator.sh`、`generate-wrapper.sh`、`build.sh` 仍有原有 Bash CRLF/语法检查失败，本轮未修改这些辅助脚本。
- 当前执行容器无 Android SDK 且无法联网下载 Gradle 分发包，因此没有把静态检查冒充 Android assemble。请继续使用用户 dev20 已验证的 Windows JDK17 + SDK35 环境执行 release 编译。
