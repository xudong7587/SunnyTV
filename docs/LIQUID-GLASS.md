# SunnyTV 液态玻璃

液态玻璃随 SunnyTV 主项目开源，无单独仓库。本说明对应 0.3.8 / 70。

| 文件 | 作用 |
| --- | --- |
| [LiquidGlass.kt](../app/src/main/java/io/github/xudong7587/sunnytv/feature/ui/LiquidGlass.kt) | GraphicsLayer 背景录制、局部裁剪、坐标对齐、折射和模糊缓存；雾面/极光 shader 分支 |
| [GlassShaders.kt](../app/src/main/java/io/github/xudong7587/sunnytv/feature/ui/GlassShaders.kt) | 液态非色散边缘透镜和双峰高光 AGSL |
| [NativeAppearance.kt](../app/src/main/java/io/github/xudong7587/sunnytv/feature/ui/NativeAppearance.kt) | 表面、按钮焦点、透明度、嵌套扁平控制及材质回退 |
| [Theme.kt](../app/src/main/java/io/github/xudong7587/sunnytv/feature/ui/Theme.kt) | 深浅色、颜色和材质能力选择 |
| [Components.kt](../app/src/main/java/io/github/xudong7587/sunnytv/feature/ui/Components.kt) | FocusTile 与浮动面板中的实际接入 |

## 接入方式

在原应用主题上下文中，通过 `rememberGlassBackdrop(enabled)` 创建背景层；只给背景图或背景绘制元素加 `captureGlassBackdrop(source)`，然后让上层面板调用 `liquidGlass(source, shape, radius, "glass")`。文字、图标和子按钮绘制在玻璃之后，不进入自己的采样源。页面按钮也可通过 `GlassButtonScene` 与 `captureButtonSceneBackdrop()` 注册当前页真实背景。

不要在包住背景和玻璃控件的同一个父容器上采样，否则消费者可能进入自己的输入。导航内部的子按钮保持扁平，仅外层面板计算折射。播放器 SurfaceView 是独立视频层，不在 Compose 的背景采样范围内。

真正的折射需要 API33+、硬件加速和可用材质能力。低版本或低负载模式使用原生回退表面；`rememberGlassBackdrop` 可能返回 null，调用方必须保留该分支。独立提取时还需要为 LocalSunnyPalette、LocalNativeAppearance 和 materialSurface 提供宿主适配，不能只复制 shader 字符串。

## 当前参数与验证

液态壳体表面覆盖默认 4%；独立按钮未选中为 3%，选中/聚焦为 10%。固定光照，无传感器或时间循环。背景使用 GraphicsLayer display list，不做 CPU 截屏；采样裁剪预留 40dp，RenderEffect 按几何和主题缓存。

原应用的 [GlassCompositingTest](../app/src/androidTest/java/io/github/xudong7587/sunnytv/GlassCompositingTest.kt) 和 [GlassMaterialRevisionTest](../app/src/androidTest/java/io/github/xudong7587/sunnytv/GlassMaterialRevisionTest.kt) 检查透明层合成、背景采样及中心透明度。已有模拟器验证不等于所有实体 GPU 均已验收，具体范围见 0.3.8 本地测试记录。

另有用于分享的 ZIP，包含原始文件、最小主题适配与可单独编译的 Compose Library 示例。独立工程的 `:glass:assembleDebug` 已通过；其示例主题和低版本回退经过简化，不代表迁移了整个 SunnyTV 主题系统。

## 来源与许可

SunnyTV 实现沿用仓库 GPL-3.0；使用时保留许可证、版权及修改声明，并遵守对应源码分发要求。上游摘录来自 compose-miuix-ui/miuix，固定提交为 `153920c7e196de1f1c8188e16162f3ce4864b016`；其 lens 来源另注明 Kyant0/AndroidLiquidGlass。上游部分保留 Apache-2.0 许可。

原始路径、SHA256、审阅和修改说明在 [UPSTREAM.json](../third_party/miuix-glass/UPSTREAM.json)，另附 [LICENSE](../third_party/miuix-glass/LICENSE) 与 [NOTICE](../third_party/miuix-glass/NOTICE)。本项目没有把上游代码宣称为 SunnyTV 原创，也没有更改其许可证。
