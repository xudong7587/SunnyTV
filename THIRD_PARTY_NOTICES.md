# 第三方依赖与素材记录

本快照为原创实现，未复制Moonfin或LumiPlayer源码。下表为直接依赖识别，不代替正式发布前的传递依赖许可证清单。

| 依赖 | 固定版本来源 | 许可证核对入口 |
|---|---|---|
| AndroidX / Compose / Media3 / Activity / Lifecycle | app/build.gradle.kts | https://android.googlesource.com/platform/frameworks/support/ |
| Kotlin / kotlinx.coroutines | build.gradle.kts / app/build.gradle.kts | https://github.com/JetBrains/kotlin 、 https://github.com/Kotlin/kotlinx.coroutines |
| OkHttp / MockWebServer | 4.12.0 | https://github.com/square/okhttp |
| Coil | 2.7.0 | https://github.com/coil-kt/coil |
| JUnit | 4.13.2 | https://github.com/junit-team/junit4 |
| org.json（仅JVM测试） | 20240303 | https://github.com/stleary/JSON-java |
| Playwright（原型测试开发工具） | 使用本机安装，不打进APK | https://github.com/microsoft/playwright |

公开发布前由Codex生成实际resolved dependency清单与许可证报告，检查所用版本的LICENSE/NOTICE；不要仅保留这张表就声称完成发行审计。

## 视觉素材

`docs/reference/user-lumiplayer-reference.jpg`来自用户提供的截图，`preview/assets/*.jpg`来自该截图局部。它们只用于本次私有设计评审，不作为随APK发布的内置影视海报。示例片名、评分、简介和进度是布局示意，不是影视资料数据库。

这些图片和原型不进入Android `res/` 或 `assets/`。将工程公开发布前，移除或替换为有权使用的占位素材；用户没有因提交截图而自动授予全部第三方电影素材的再发布权。

本包不包含字体文件。Android依赖系统字体，浏览器使用系统字体回退。原生图标为简单原创矢量占位，不使用LumiPlayer或Moonfin品牌标识。
