# SunnyTV 0.3.6 本地测试包 · 2026-10-21

版本0.3.6 / versionCode68。实际工作区 `C:/Users/Sunny/.codex/worktrees/ugreen-lan-tls/SunnyTV`；沿用0.3.5和既有LAN TLS修复，未提交/推送/发布GitHub，未安装实体设备。

## 用户可见修正

- 相似推荐使用的MediaCard评分角标原先固定65%黑底，却取主题文字色，导致浅色黑字落在深底上。现在角标独立配对：浅色82%白底+黑字，深色65%黑底+白字；不受主题强调色和玻璃按钮文字覆盖影响。所有共用MediaCard的列表一并修正。
- 材质选择改为MediaIndex Next的清晰卡片式示意：42dp方形、内层25×12小卡片、短线，以及各材质自己的边线、方向明暗、纸面颗粒或瓷面高光。保持两列紧凑选项，遥控焦点、即时保存、选中勾选及不可用材质过滤保留。
- 小示意不再缩小完整面板并套用完整模糊半径，避免玻璃预览糊成一块。16种示意均采用缓存原生路径/渐变，不创建背景捕获或RenderEffect；页面实际玻璃和大尺寸材质审查样块继续使用真实渲染器。

参考只读查看了 `D:/Documents/MediaIndex前端/MediaIndex-next/apps/web/src/appearance/AppearancePicker.tsx` 与 `apps/web/src/style.css` 的选择器结构/视觉数值，并独立实现为Compose绘制；未修改MediaIndex、复制其工程文件或引入网页运行时。SunnyTV新增 `MaterialOptionSwatch.kt`，评分使用 `PosterRatingBadge` 配对颜色。

## 验证

- debug/release及Android测试APK编译通过，177项Gradle单测0失败，release lint 0错误/app37个既有警告。
- API34最终10/10：可读性2、外观5、Monet3。新增测试通过实际MediaShelf/MediaCard截取评分角标，验证深浅底色与4.5:1的实际字形像素对比；16种材质均以实际42dp尺寸渲染两种模式并截图审查。初轮用接近纯黑/白像素计数对抗锯齿小字过严，改为实际亮度对比，保留底色、文字方向及足量像素断言后最终通过。
- API30兼容7/7：可读性2、外观5。材质保存、遥控网格、紧凑布局和高开销列表过滤保持通过。
- 3项发行脚本测试、0.3.6/68身份/非debuggable/包内容检查与既有签名证书比对通过。没有改动网络、来源或播放业务；本轮未重复历史172项核心契约或完整TV套件。
- 两套材质选择器及评分截图已查看，日志和无私有媒体图片在 `.local-tests/036`。未完成实体电视视觉、帧率/功耗验收。

## 分发

仅APK复制到 `Z:/V3 Download/tv apk/SunnyTV-v0.3.6.apk`，29,170,599字节，回读SHA256：

`b7496c75ae39827399f0d27520689f141d11e2738961a089a40d67a062782ee8`

历史包保留；没有NAS JSON、WebDAV或同版本覆盖。未更换签名。此前高开销材质条件及播放器不采样视频的边界继续生效。
