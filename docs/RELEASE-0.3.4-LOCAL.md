# SunnyTV 0.3.4 本地测试包 · 2026-10-21

版本0.3.4 / versionCode66；实际工作区 `C:/Users/Sunny/.codex/worktrees/ugreen-lan-tls/SunnyTV`。沿用0.3.3与既有LAN TLS修复，未提交/推送/发布GitHub，未安装实体设备。

## 可用材质列表

- “允许高开销主题”关闭时隐藏液态玻璃、雾面玻璃、极光玻璃和霓虹暗夜；普通材质保留。低负载、API不足或缺少硬件加速时，同样过滤不可用选项。渲染端原有回退保护继续生效。
- 保留用户保存的材质偏好；暂时不可用时标题、说明和勾选标记都显示实际生效的清晰默认，并说明回退原因。再次满足条件时恢复原偏好。
- 两列网格按过滤后的数量导航，修正上下键滚动索引及末行边界。开关操作保留焦点。Monet开关带来的行偏移仍被计算。

## Streama首页底部玻璃

重新追踪用户提供的Streama APK首页底部实际调用，确认普通Pill的25dp模糊不等于LiquidGlass：后者使用4dp轻模糊、1.5饱和度、24dp圆弧映射折射带与负24dp偏移、单次采样且关闭色散。采用40%表面色覆盖和1dp双峰白色高光（alpha0.75、内侧2dp）。

从Apache-2.0公开Miuix源码固定提交 `153920c7e196de1f1c8188e16162f3ce4864b016` 审计提取非色散Lens与双峰Bloom shader至 `GlassShaders.kt`；没有复制APK私有代码、引入整个UI库或升级依赖。来源、逐文件SHA及变更见 `third_party/miuix-glass/UPSTREAM.json`；许可证与署名随APK打包。

SunnyTV保留现有原生布局、同心圆角、白色重点色/焦点圈、内部扁平按钮。真实背景折射仍用于顶部容器及材质样块；其它独立按钮使用同源高光与40%表面色，不采样视频。固定光源适配电视，不引入手机倾斜传感器、拖拽、按压七采样色散或额外一套隐藏图标。不是完整手机交互逐帧复刻。

背景使用现有共享GraphicsLayer及局部裁剪，不做CPU截图；shader和RenderEffect随几何/主题变化缓存。平台分离模糊按上游sigma=4dp×0.45映射Android半径，避免直接套4dp造成过度模糊。关闭效果时不创建背景特效图层。未声称与原APK帧率/功耗或逐像素一致。

## 验证

- 最终debug/release构建与release lint通过；177项Gradle单测0失败，lint app37个既有警告/0错误。未改核心网络/播放模型；本轮未重复172项纯Kotlin历史契约。
- API34：最终玻璃渲染5/5（真实背景变化、文字清晰、不同圆角、关效果保持焦点、全局控件）；外观5/5、Monet3/3、播放器1/1、主页4/4，共18项。组合回归初轮遇模拟器退出，重启分组完整验证；Monet旧测试仍引用已隐藏材质，已按新列表更新后3/3通过，未删断言。
- API30：外观5/5、Monet3/3、播放器1/1，共9/9；确认旧系统隐藏不可用玻璃且保留正常遥控导航。
- 3项发行脚本测试、APK签名与既有0.2.8公开证书比对、包名/版本/非debuggable及内容检查通过。未替换签名或解码库。
- 无真实媒体的截图已查看，日志和图片在 `.local-tests/034`。原Streama ARM APK不能在本机x86模拟器安装，未修改/重签它；参考采用静态调用证据及公开源码，未完成原APK运行像素对比或实体电视性能验收。

## 分发

仅APK复制至 `Z:/V3 Download/tv apk/SunnyTV-v0.3.4.apk`，29,154,211字节，回读SHA256一致：

`a6eb05c4f5b6aef3f4a733f1453d2871dfe373eaf5d131ad8b1fc07968aa347c`

历史版本保留；未写NAS JSON、未执行WebDAV、未覆盖同版本不同内容。首次脚本在成功复制与校验后，打印映射盘规范路径时遇WinError1005；再次直接回读同文件已确认哈希一致，不影响APK内容。

公开参考：[Miuix底栏](https://github.com/compose-miuix-ui/miuix/blob/153920c7e196de1f1c8188e16162f3ce4864b016/example/shared/src/commonMain/kotlin/component/liquid/LiquidGlassNavigationBar.kt)、[Lens](https://github.com/compose-miuix-ui/miuix/blob/153920c7e196de1f1c8188e16162f3ce4864b016/example/shared/src/commonMain/kotlin/component/liquid/Lens.kt)、[Android RenderEffect](https://android.googlesource.com/platform/frameworks/base/+/7d59d89f035a/libs/hwui/jni/RenderEffect.cpp)。本机静态证据报告位于 `D:/Documents/ChatGPT/analysis/streama-glass-20261021/HOME-BOTTOM-LIQUID-GLASS.md`。
