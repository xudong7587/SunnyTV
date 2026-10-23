# SunnyTV 液态玻璃

液态玻璃随 SunnyTV 主项目开源，无单独仓库。本说明对应 1.0.4 /77。网页移植已进入 [SunnyUI 2.1.0](https://github.com/xudong7587/sunny-ui-design-system/releases/tag/v2.1.0)，包含安装包与设计 Skill；下面说明 Android 原生实现。

| 文件 | 作用 |
| --- | --- |
| [LiquidGlass.kt](../app/src/main/java/io/github/xudong7587/sunnytv/feature/ui/LiquidGlass.kt) | GraphicsLayer 背景录制、局部裁剪、坐标对齐、折射和模糊缓存；雾面/极光 shader 分支 |
| [GlassShaders.kt](../app/src/main/java/io/github/xudong7587/sunnytv/feature/ui/GlassShaders.kt) | 液态非色散边缘透镜和双峰高光 AGSL |
| [LegacyGlass.kt](../app/src/main/java/io/github/xudong7587/sunnytv/feature/ui/LegacyGlass.kt) | Android 11–12L 共享页面纹理柔化与 GPU 网格折射 |
| [NativeAppearance.kt](../app/src/main/java/io/github/xudong7587/sunnytv/feature/ui/NativeAppearance.kt) | 表面、按钮焦点、透明度、嵌套扁平控制及材质回退 |
| [Theme.kt](../app/src/main/java/io/github/xudong7587/sunnytv/feature/ui/Theme.kt) | 深浅色、颜色和材质能力选择 |
| [Components.kt](../app/src/main/java/io/github/xudong7587/sunnytv/feature/ui/Components.kt) | FocusTile 与浮动面板中的实际接入 |

## 接入方式

在原应用主题上下文中，通过 `rememberGlassBackdrop(enabled)` 创建背景层；给消费者后方的页面元素加 `captureGlassBackdrop(source)`，然后让上层面板调用 `liquidGlass(source, shape, radius, "glass")`。文字、图标和子按钮绘制在玻璃之后，不进入自己的采样源。页面按钮和选择弹窗通过 `GlassButtonScene` 与 `captureButtonSceneBackdrop()` 复用完整页面，包括海报墙。

不要在包住背景和玻璃控件的同一个父容器上采样，否则消费者可能进入自己的输入。导航内部的子按钮保持扁平，仅外层面板计算折射。播放器 SurfaceView 是独立视频层，不在 Compose 的背景采样范围内。

Android 13 及以上（API33+）使用 AGSL；Android 11–12L（API30–32）使用共享页面纹理和 GPU 网格的兼容路径。两种路径均要求硬件加速、允许高开销主题和非低负载模式。API29 以下或能力不足时使用原生回退表面；`rememberGlassBackdrop` 可能返回 null，调用方必须保留该分支。独立提取时还需要为 LocalSunnyPalette、LocalNativeAppearance 和 materialSurface 提供宿主适配，不能只复制 shader 字符串。

## 当前参数与验证

液态壳体表面覆盖默认 4%；独立按钮未选中为 3%，选中/聚焦为 10%。选择弹窗底色 21%、外框圆角最多 24dp，内层磨砂聚焦，不压暗窗口外部。固定光照，无传感器或空闲时间循环。API33+ 背景使用 GraphicsLayer display list，不做 CPU 截屏；采样裁剪预留 40dp，RenderEffect 按几何和主题缓存。

API30–32 共享降采样纹理，最长边不超过 640 像素。按实际绘制变化合并更新，最多 10 次/秒；工作线程柔化，再用 GPU 纹理网格绘制折射。前景不参与柔化，页面空闲时不持续取图。

原应用的 [GlassCompositingTest](../app/src/androidTest/java/io/github/xudong7587/sunnytv/GlassCompositingTest.kt)、[GlassMaterialRevisionTest](../app/src/androidTest/java/io/github/xudong7587/sunnytv/GlassMaterialRevisionTest.kt)、[LegacyGlassTest](../app/src/androidTest/java/io/github/xudong7587/sunnytv/LegacyGlassTest.kt) 和 [LibraryChoiceTest](../app/src/androidTest/java/io/github/xudong7587/sunnytv/LibraryChoiceTest.kt) 检查合成、真实页面取样、前景清晰度及弹窗外部亮度。本地最终签名包通过 API30 TV 与 API34 定向回归；具体范围见 [1.0.4 记录](RELEASE-1.0.4-LOCAL.md)。实体电视的 GPU 效果和流畅度仍待确认。

此前 0.3.8 的分享 ZIP 包含最小主题适配与可单独编译的 Compose Library 示例，`:glass:assembleDebug` 已通过；该历史快照未包含 1.0.4 兼容路径。当前 Android 实现以主仓库为准，网页接入见 SunnyUI 的 [玻璃说明](https://github.com/xudong7587/sunny-ui-design-system/blob/v2.1.0/docs/LIQUID-GLASS.md)。

## 来源与许可

SunnyTV 实现沿用仓库 GPL-3.0；使用时保留许可证、版权及修改声明，并遵守对应源码分发要求。上游摘录来自 compose-miuix-ui/miuix，固定提交为 `153920c7e196de1f1c8188e16162f3ce4864b016`；其 lens 来源另注明 Kyant0/AndroidLiquidGlass。上游部分保留 Apache-2.0 许可。

原始路径、SHA256、审阅和修改说明在 [UPSTREAM.json](../third_party/miuix-glass/UPSTREAM.json)，另附 [LICENSE](../third_party/miuix-glass/LICENSE) 与 [NOTICE](../third_party/miuix-glass/NOTICE)。本项目没有把上游代码宣称为 SunnyTV 原创，也没有更改其许可证。
