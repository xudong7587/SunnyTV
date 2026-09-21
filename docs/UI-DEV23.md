# SunnyTV dev23 · 改动与验证记录

基线：dev22 源码快照（`SunnyTV_dev22`）。本轮只处理用户指出的两处细节：
聚焦外框的圆角包裹，以及首页选中阴影被行分区裁掉。

## 1. 聚焦外框的圆角包裹不完全

`FocusTile` 的描边原本这样画：把 `shape.createOutline(size - stroke)` 缩小一个描边宽度，
再平移半个描边，然后用 `Stroke(stroke)` 描这个轮廓。这样**圆角半径没有跟着缩小**，
于是圆角弧心比卡片自己的弧心向内偏了 `stroke/2`，而弧的半径不变：

- 角上的描边比直边略薄（2dp → 约 1.7dp）；
- 沿角平分线，描边外沿落在卡片轮廓**内侧约 0.4dp**，那一条缝隙里露出的是海报本身
  （浅色海报上就是一道"角没包住"的亮缝）。

改为同心内缩：`concentricInsetOutline()` 在平移轮廓的同时把每个圆角半径减去同样的量，
描边就正好落在卡片自己的轮廓上，四个角与四条边一圈同宽。圆角矩形、圆（`CircleShape`）
与普通矩形都走同一条路径；纯 Kotlin 断言在 `FocusFrameVisualTest` 里。

## 2. 首页选中阴影被分区裁掉

柔和阴影是画在卡片**外面**的（横向约 13.4dp、下方多偏 3.6dp），而承载卡片的行
（`LazyRow` / 折叠行视口）会裁剪自己的边界。之前只有上下留了 18dp，横向是 0：

- 首页「我的媒体库」那一行：最左媒体库卡片左侧**完全没有阴影**（实测紧邻边框就是纯背景色）；
- 首页每个媒体库的折叠推荐行（`TvAccordionCards` 的 `clipToBounds` 视口）：最左媒体块同样被切平；
- 媒体库工具栏按钮、详情页「演员表」圆头像、云盘目录列表、首页「接着看下一集」也有同类裁剪。

统一做法（数值集中在 `FocusElevation.kt`）：

| 常量 | 值 | 含义 |
| --- | --- | --- |
| `FocusShadowGutter` | 14dp | 行内左右留白（略大于阴影横向 13.4dp） |
| `FocusShadowTopGutter` | 12dp | 上方留白（阴影整体下移，上方只需 13.4−3.6≈9.8dp） |
| `FocusShadowBottomGutter` | 18dp | 下方留白（13.4+3.6≈17dp） |

改动点：

1. `TvAccordionCards` 视口高度 183dp → `12+165+18=195dp`，卡片左边与顶部各让出一个 gutter；
2. 首页「我的媒体库」行、`LibraryLatestRow`、`MediaShelf("接着看下一集")`：外层左右内边距各减 14dp，
   行内容内边距各加 14dp，标题再补 14dp —— 标题与第一张卡仍然落在 30dp 的页边距上，只是阴影有了落脚处；
3. 媒体库工具栏那一排按钮：`contentPadding` 由 `2dp/4dp` 改为 14dp/12dp/18dp；
4. 详情页「演员表」圆形头像行补上下留白；`detailRowGutter` 并入同一个常量；
5. 云盘目录列表（`FolderScreen`）内缩同样的 gutter，标题与列表保持同一左边缘。

## 3. 验证

### 视觉证据（API 30 TV 模拟器，1920×1080，浅色主题 + 柔光阴影开启）

`FocusFrameVisualTest` 用虚构数据渲染真实首页/媒体库页面并导出 PNG，另外对最左媒体块
做像素断言（边框左侧 2–13px 内必须比背景暗 6 以上）。

| 检查 | 修复前 | 修复后 |
| --- | --- | --- |
| 首页最左媒体库卡片左侧像素 | `(41,72,61) → (249,251,249)` 立即变成纯背景，无阴影 | `(41,72,61) → 204 → 221 → 232 → 241 → 246 → 249` 平滑阴影 |
| 首页最左折叠媒体块左侧像素 | 同上，无阴影 | 同上，平滑阴影 |
| 聚焦卡片顶左角 | 描边圆角弧心内偏，角上留 0.4dp 亮缝 | 描边与卡片轮廓同心，整圈同宽 |
| 卡片绝对位置 | — | 最左卡片的左边缘仍在 60px（30dp），未因留白而漂移 |

### 仪器化测试

同一台模拟器上，dev22 基线跑 `am instrument`（53 项）失败 8 项；dev23（57 项，含新增 4 项视觉/几何用例）
失败 8 项，且**集合一致**——即没有新增失败：

```
visibleGridRowsStayStillAndHeaderStaysPinnedAcrossBoundary
selectedThirdLibraryContinuesInPageOrderAndReturnsToSelectedLibrary
posterGridTraversesOffscreenRowsPartialLastRowAndPagination
actorsMoveDownIntoOffscreenRecommendationsAndBackUp
episodeLayoutsSwitchWithoutChangingRealSettings
rapidPageTransitionReversalDoesNotKeepOutgoingButtonsFocusable
latestHeaderFocusRevealsItsShelfAndBottomSpace
pinnedHomeEntersAStationaryHeroThenMovesDownToStableLibraryRow
```

其中 `episodeLayoutsSwitchWithoutChangingRealSettings` 与 `rapidPageTransitionReversalDoesNotKeepOutgoingButtonsFocusable`
已用 dev22 源码单独复现，属既有失败（本轮未改动其相关逻辑）。

另外三处旧断言按新布局更新，并保留原有意图（"边缘卡位不移动""第一张块与视口对齐"）：
`Dev16PerformanceTest.portraitFirstPosterIsNarrowAndLeftAlignedEvenWhenFocused`、
`TvMotionTest.edgeSlotStaysFixedAndViewAllPreservesTheFinalWideCardInBothDirections`、
`TvMotionTest.idleShelfCollapsesThenReturnsToFirstCardOnVerticalReentry`
——三处断言的期望值由 0dp 改为 `FocusShadowGutter`（14dp）。

### 汇总

| 项目 | dev23 结果 | dev22 基线（同一台模拟器） |
| --- | --- | --- |
| 纯 Kotlin / JUnit 单元测试 | 64 项方法全部通过（含 `ContractSuiteTest` 核心契约聚合） | — |
| 仪器化 UI 测试（API 30 TV 模拟器） | 57 项执行，8 项失败 | 53 项执行，8 项失败（失败集合与 dev23 完全相同） |
| `scripts/check-project.py` | 通过（含禁止打包字体的检查） | 通过 |
| `lintDebug` | 14 error / 28 warning | 14 error / 28 warning（逐条一致，均为既有项，如 `PinyinIndex` 的 `Transliterator` NewApi） |
| 发布 APK | `dist/SunnyTV-v0.1.0-dev23-self.apk`，16,256,708 字节，SHA256 `01BE4DD6969381DA867232D9CA2F0DE6AD4AB26F5C76C9DDB40F547BCDBD071B` | dev22 同尺寸 |
| 签名 | 与仓库既有密钥一致（证书 SHA-256 `5e8dcd5e1eee828e064682ba6f8dc7d54dfcf59d3182dd6b427a23d1214d6b00`），未新建密钥 | 同 |

### 未验证 / 已知限制

- 仅 API 30 TV 模拟器验证；实体电视与手机真机的手感仍需用户确认。
- 手机 `10AF4B15MG001YJ` 在 13:44 覆盖安装并启动通过；随后清理未使用 import 重新构建的最终 APK
  再次安装时被手机端拒绝（`INSTALL_FAILED_ABORTED: User rejected permissions`），最终包未重装，
  需要用户在手机空闲时自行安装 `dist/SunnyTV-v0.1.0-dev23-self.apk`。
- 光晕阴影在低负载（`lean`）模式下分层数更少，本次视觉证据均为默认模式。
- 设置页面的整行卡片（`ToggleRow` 等）仍在 `contentPadding=0` 的 `LazyColumn` 里，
  左右阴影被裁 14dp 的情况未改动，以免把设置内容整体缩进（用户本轮只反馈首页）。
