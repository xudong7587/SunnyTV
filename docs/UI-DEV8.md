# dev8：实际字幕、演员详情与手机海报背景

2026-09-17，在 dev7 上完成本轮反馈。

- 媒体详情的字幕列表只列当前版本的实际轨道（外挂文件名 / 内嵌轨道），以及自动选择、关闭字幕。移除固定语言种类选项。选择实际轨道时绑定该媒体版本，外挂字幕使用独立流 ID，避免同语言不同文件被混淆。
- 全局与库级字幕设置均作为优先级，库级选择独立保存，并沿媒体库、文件夹、季集上下文传递。偏好未匹配时保留播放器的默认选择；明确选择字幕可覆盖“默认关闭”。
- 播放器面板实时读取 Media3 的实际轨道，显示当前选中项，手动选择后不会再被自动偏好覆盖。字幕偏好不再因未匹配而直接禁用字幕。
- 外挂字幕依据当前媒体源的 Emby MediaStreams 获取；优先使用服务端 DeliveryUrl，缺失时对已识别的文本外挂构建官方字幕流端点。保留 SRT / VTT / ASS / SSA 支持，不扫描或修改 NAS 文件。
- 演员头像可点击，读取 Emby 人物介绍，并按 PersonIds 分页查询当前服务器内关联的影视作品；支持返回、重试和加载更多。不请求外部演员网站或爬取图片。
- 手机竖屏轮播与媒体背景优先使用 Primary 海报；细条固定可读宽度并在右侧横向滚动，不挤压所有细条。TV 横屏仍采用原背景与六细条布局。

## 验证

assembleDebug、testDebugUnitTest、lintDebug、assembleDebugAndroidTest 全部通过（dev8-final-build.log）。
28 个 JUnit 方法通过，核心套件含 114 项检查；新增六项覆盖字幕优先级、精确文件身份、缺失/不支持轨道、媒体源外挂端点、演员名称编码及只读作品分页。
lint 0 错误、26 警告。11 项设备交互测试编译，未运行仪器化手机测试。

APK：dist/SunnyTV-0.1.0-dev8-debug.apk，versionCode 8，沿用本机 debug 签名。
SHA-256：127f58796932d688b700af54b3c51bc6e3a668c2162762bf56c408b29ede1c5f。
本轮未自动安装到手机。实际字幕显示、演员数据完整性及手机布局仍待用户设备验收。

字幕文件须已被 Emby 识别并关联到媒体；尚未被 Emby 扫描的文件不会凭空出现在列表中。外部位图字幕与自动烧录不在当前能力范围内。演员没有介绍或关联作品时明确显示空状态。

参考：[Emby 字幕服务](https://dev.emby.media/reference/RestAPI/SubtitleService.html)、[人物介绍](https://dev.emby.media/reference/RestAPI/PersonsService/getPersonsByName.html)、[人物作品查询](https://dev.emby.media/reference/RestAPI/ItemsService/getItems.html)。
