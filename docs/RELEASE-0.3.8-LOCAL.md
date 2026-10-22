# SunnyTV 0.3.8 本地测试包 · 2026-10-21

版本0.3.8 / versionCode70。实际工作区 `C:/Users/Sunny/.codex/worktrees/ugreen-lan-tls/SunnyTV`。包含0.3.7的详情去重容错、液态中央3%/10%覆盖、极光内部淡彩和雾面斜切棱台；本轮增加TV画面比例兼容修订。未提交/推送/发布GitHub，未安装实体设备。

## TV画面比例

用户报告手机切换正常，TV按“适应/裁切/拉伸”仍有黑边、画面没有明显变化。普通API30 TV模拟器的旧路径可以切换布局及实际视频，但不代表用户电视的硬件合成路径正常。[Media3官方Surface说明](https://developer.android.com/media/media3/ui/surface)指出AndroidView中的PlayerView可能有Surface与父容器缩放不同步，尤其API34；1.9.4的[PlayerView源码](https://github.com/androidx/media/blob/1.9.4/libraries/ui/src/main/java/androidx/media3/ui/PlayerView.java)保留对应兼容机制。此为修订依据，不是已取得用户电视日志的根因证明。

- TV将既有PlayerView/SurfaceView直接放在Activity的原生FrameLayout里，透明ComposeView只负责OSD。画面比例直接改变原生视频层的布局，避免AndroidView互操作层；手机继续原有AndroidView路径。
- 保留Media3 FIT/ZOOM/FILL的正确含义，保持SurfaceView、原生解码、HDR/安全输出路径及首帧回调，没有换TextureView、复制播放器或增加额外固定放大。比例切换不重建播放器/视频视图；停止时先解绑TV视图再释放播放器。
- 把视频视图绑定与字幕样式更新收拢为共用更新函数，按实际player、resizeMode及字幕样式变化应用；OSD显示/隐藏与视频Surface生命周期分离。
- 按钮常显“适应/裁切/拉伸”，点击后当前模式明确可见，焦点返回该按钮。修复Activity/显示配置重建后比例重置的问题，合法模式保存到实例状态并校验恢复。

**边界：**没有连接用户实体TV，OEM上的实际效果仍待复测；不宣称已证明所有电视兼容。已经编码在视频像素里的黑边，不会被Media3按容器宽高自动识别；若新包仍无变化，需要实际编码比例、TV型号/系统及设备日志继续区分。

## 验证

- debug/release、Android测试APK及release lint通过，177项Gradle单测0失败；app37个既有警告/0错误。核心网络、来源、解码依赖和签名未改，本轮不重复0.3.7已通过的172项纯Kotlin契约。
- 新PlayerResizeTest使用已有无私有媒体的320×192合成H.264，真实Media3解码。遥控确定循环三种模式，验证实际原生content frame几何、播放器实例不变，以及包含SurfaceView的整窗截图：FIT侧边黑，ZOOM/FILL侧边为视频内容。之后重建Activity验证ZOOM保留。旧包在重建断言中恢复成FIT而失败，新实现通过；旧API30常规缩放本身通过，未把它误写成模拟器上的缩放失败。
- API34最终9项通过：比例/恢复1、播放器面板1、弹层不中断/不改视频边界1、外字幕跨清晰度1、字幕时序跨切换1、片尾下一集1、详情重复数据1、三种玻璃2。首轮详情夹具复用媒体key时恢复了之前的底部滚动位置，首屏断言失败；改为每个打开案例独立身份，保留其余断言后最终通过。没有改变产品的详情位置恢复行为。
- API30播放器5/5：比例/恢复、面板焦点、弹层、外字幕清晰度连续性、字幕时序连续性。
- 原先通过Compose `player:video`标签量外层尺寸的字幕/弹层用例，改为直接量真实PlayerView在窗口的位置，覆盖TV原生宿主和手机AndroidView；没有用透明占位元素代替视频测试。实际缩放截图在 `.local-tests/038/images/resize-0.png`、`resize-3.png`、`resize-4.png`，已查看FIT/ZOOM，日志全部保留。
- 既有签名证书匹配，0.3.8/70、非debuggable、APK内容检查及发行脚本测试3/3通过。没有声称完整TV套件或实体电视/真实NAS验收通过；0.3.7记录的旧TV演员上下导航空闲超时仍保留。

## 用户复测补充

用户随后确认“闪退解决了”，并授权将本版发布为 GitHub latest；原始 APK 字节不变。以下保留排查阶段的结论与范围。

## 详情退出的排查记录

用户补充旧版TV也在相同剧集退出，支持“条目详情数据触发既有问题”的排查方向；点击详情时尚未开始读取播放文件，不能据此认定视频资源损坏。0.3.7已复现并修复重复演员Lazy键异常，也防护季、版本和推荐的重复条目。本版保留该修复；尚无用户手机/电视的具体异常堆栈，三部真实条目仍需复测。

## 分发

仅APK复制至 `Z:/V3 Download/tv apk/SunnyTV-v0.3.8.apk`，29,170,595字节，回读SHA256一致：

`b8e5b6bd026421b0b185aac79b21353727b193430fd8ff13afd7a25d6ce4d917`

保留历史包，仅本地投放，没有NAS JSON、WebDAV、同版本覆盖或GitHub发布。两个本轮模拟器已关闭。
