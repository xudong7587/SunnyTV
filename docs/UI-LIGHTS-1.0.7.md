# 1.0.7 /80 UI与灯带反馈修复（2026-10-24，本地未发行）

用户提供1.0.6电视实拍：海报墙焦点不明显、详情玻璃按钮细黑线与粗边、播放器聚焦有矩形灰底、灯色过淡；《凡人修仙传》流畅但灯色不再变化。继续现有未发行1.0.7源码，不替换已分发1.0.6。

## 修改与原因

- `PosterWallSlot`补`artwork=true`，避免海报被当成透明玻璃按钮。`FocusTile`不再对液态玻璃禁用外框，海报3dp、按钮2.5dp，颜色为当前主题/Monet重点色，描边向内且圆角同心，不改变布局或海报几何。
- API30–32电视`LegacyTvGlass`去掉中心与矩形边缘两份背景的拼接，连续绘制单份轻微放大背景；细高光描边内收，避免半笔宽被外层裁切。保持实时图层及子层滚动，不增加CPU截图/模糊。手机旧玻璃与API33+shader不改。
- `PlayerControl`的液态模式不再应用无视频背景来源的玻璃面，聚焦/选中图标使用重点色，并显示圆形重点色外框。播放器选项和字幕时间按钮同样统一焦点。关闭三处默认`clickable` indication，避免自定义焦点上再叠矩形灰层。视觉测试实测发现默认反馈将整块RGB乘约0.9，关闭后背景像素保持一致。
- `EdgeColors`鲜艳模式饱和度增益1.6→2.4，对明确有色的区域设置70%饱和度下限；黑白灰保持中性，色相不变。增强在暗边扇区回退之后应用，避免回退颜色漏掉鲜艳处理；用户关闭鲜艳模式时保留自然算法。既有RGB负载限制、硬件亮度及定位协议保留。
- `VideoReadbackQuality`保留160×90/32×18自适应。小网格250–1999ms时不再永久暂停，而是低频四边更新，成功回读的颜色仍交给灯带，间隔至少5秒并遵守原10%时间预算。300ms示例等待5000ms；950ms示例等待8550ms，均为回读完成后计时。>=2000ms仍停止当前播放取色，重新开关同步或新播放可重试。黑边扫描仍使用原250ms停止阈值。
- `StableLightColors`对首帧及间隔>=1秒的采样即时应用四边颜色，黑色继续保留先前色；正常频率仍用三帧中值和时间平滑。避免低频下等三帧以及每次只向目标移动很小一截。采样代数跨线程可见，过期回调不发布颜色。

PixelCopy仍需从视频Surface取得缓冲并缩放，缩小目标位图不能保证厂商驱动读回变快，参见[Android官方PixelCopy文档](https://developer.android.com/reference/android/view/PixelCopy)。本轮低频策略解决自动停色与延迟积累，不宣称消除单次GPU读回的顿挫。此次没有第二解码器、整屏录制、播放器Surface改型或网络/来源协议变更。

## 已执行的本地验证

JDK17、SDK35、Gradle Wrapper8.11.1及既有缓存。所有Android验证均在这台Mac执行，没有推送或触发云端CI。

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew --offline --console=plain testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug lintRelease
```

结果：debug APK与测试APK构建、debug/release源码编译成功；221项应用单元测试零失败/跳过，包括HTTP来源与网络回归；lint零错误，debug34/release31警告。纯Kotlin契约使用Gradle缓存中的Kotlin2.1.20编译器执行`scripts/test-core.sh`相同源文件，再运行`io.github.xudong7587.sunnytv.contract.CoreContractTestKt`，172通过/0失败。

本地新建隔离模拟器`sunnytv-ui-107-qa`：API30 Google APIs ARM64系统镜像、1920×1080 TV硬件配置。它不是Android TV系统镜像，玻璃电视路径由`LegacyGlassTest`显式覆盖。执行：

```bash
adb -s emulator-5554 shell am instrument -w -r \
  -e class io.github.xudong7587.sunnytv.GlassFocusRepairTest,io.github.xudong7587.sunnytv.LegacyGlassTest,io.github.xudong7587.sunnytv.VideoReadbackSafetyTest,io.github.xudong7587.sunnytv.FullBufferSamplingTest,io.github.xudong7587.sunnytv.PlayerQualityBadgeTest,io.github.xudong7587.sunnytv.PlayerPanelFocusTest \
  io.github.xudong7587.sunnytv.debug.test/androidx.test.runner.AndroidJUnitRunner
```

最终8项全部通过，无失败/跳过：深浅主题真实海报/按钮重点色像素与播放器面透明度、实时玻璃背景/子层滚动、真实Handler的2秒阻塞停止及300ms低频继续取色、4K缓冲两种网格、清晰度字形与面板返回焦点。逐项截图已检查，证据在忽略目录`.local-tests/ui-lights-1.0.7/`。

首轮视觉用例因Touch模式下普通clickable不接受遥控焦点而失败，测试显式请求Keyboard模式；随后真实暴露默认矩形反馈压暗RGB的问题，修复实现后整组8项重跑通过，原失败日志保留。未移除失败断言。

## 真机与发行边界

用户授权读取电视ADB日志及后台。两次设备枚举无电视，mDNS无发现；从Mac ADB历史日志找回`192.168.11.117:5555`，直接连接超时，ping亦无响应，当前局域网5555发现未找到设备。没有取得用户本次《凡人修仙传》的日志，不能认定停色的唯一原因或声称蓝牙已正常。未发送电视遥控键、修改电视设置或安装新包。

本轮仍待验证：详情原画面的黑线是否全部消失、实际电视遥控可见性、蓝牙连接/发送、反射墙面饱和度，以及低频读回对《凡人修仙传》的稳态流畅度。2秒保护触发时仍会保持灯色；低频成功更新也可能落后画面数秒。

1.0.7未签名发行、未提交/推送/发布GitHub、未复制APK到NAS；本地debug包只供隔离模拟器验证，不能作为正式升级包。
