# SunnyTV dev24 · 改动与验证记录

基线：dev23 源码快照（`SunnyTV_dev23`）。本轮只修用户反馈的一种情况：
**媒体库（含文件夹内部）从媒体区按「上」回到 banner 时，中间会卡一下**。
首页两侧切换用户已确认正常，未改动。

## 1. 现象与量测

用 `BannerReturnMotionTest` 把时钟停住，按「上」后在每个 32ms 帧上采样工具行的位置，
得到逐帧位移（px）：

```
修复前 dev23： 99, 62, 35, 18, 10, 6, 2, 1, 1, 0, 0, 173, 296, −997 …
修复后 dev24： 398, 286, −1073 …
```

修复前是**两段动画**：第一段把页面往上挪了一屏内可见行的距离，速度衰减到完全停住（连续两帧位移为 0），
随后第二段动画重新加速把剩下的距离走完——用户看到的"卡一下"就是这段停顿加重新起步。

## 2. 根因

工具行按「上」原本走 `GridFocusNavigator.move(0)`，其 `beforeMove(0)` 调用
`LazyGridState.revealItem(0, alignTop=true)`。而 `revealItem` 在目标项**不在可见项列表**时
只能按"当前可见首行的高度"逐次滚动：

```kotlin
val row = if (direction > 0) visible.lastOrNull() else visible.firstOrNull()
direction * ((row?.size?.height ?: 160) + info.mainAxisItemSpacing)
```

banner 是一个占满视口的整项（约 458dp），回到它时它整个在视口上方，于是先按工具行高度滚一次，
再按剩余距离滚第二次——两次 `animateScrollBy` 各自带缓出曲线，中间必然停下再起步。

## 3. 修复

与上一个方向（banner → 媒体区）保持同一套写法，是用户已经验收过的那种"一次受控滚动"：

1. 新增 `leaveToolsForBanner()`：`gridState.animateScrollToItem(0)` 一次动画回到 banner 自己的偏移，
   焦点请求在并行的协程里逐帧重试；整个过程中把视口 hold 住（`toolsSettling`），
   结束后再保持两帧，避免焦点自己的 bring-into-view 动画结束后再补一次滚动。
2. 工具行的「上」改为调用 `leaveToolsForBanner()`；`GridFocusNavigator.beforeMove(0)` 也改成
   单次 `animateScrollToItem(0)`，任何残留路径都不会再走逐行滚动。
3. 文件夹模式：从文件夹行按「上」回到工具行原本只请求焦点、不滚动（视口被 hold 住时不会滚，
   hold 释放后才补一次滚动，同样是一次"延迟跳一下"）。现在改为与 `enterTools()` 相同的落位：
   一次 `animateScrollToItem(1, pageTopPadding)`，工具行整行贴顶后再交焦点。

## 4. 验证

| 项目 | 结果 |
| --- | --- |
| `BannerReturnMotionTest`（新增） | dev24 通过；把同一份测试拿到 dev23 源码上运行**失败**，报错即 `return scroll stops and restarts halfway: [99,62,35,…,0,0,173,296,…]`，证明该用例确实能抓住这个回归 |
| 逐帧位移 | 单段动画、单调减速：`398, 286` 之后工具行离开视口，无停顿、无二次加速 |
| 返回后位置 | `library-carousel:featured` 顶部与进入媒体区之前一致（±1dp），焦点回到 banner 区（`library-hero-play`），工具行不再显示 |
| 整包仪器化测试（API 30 TV 模拟器） | 58 项执行，8 项失败；与 dev22 基线（53 项 / 8 项失败）失败集合完全一致，无新增 |
| 单元测试 | 64 项全部通过 |
| `scripts/check-project.py` / `lintDebug` | 通过 / 14 error 28 warning（与 dev22 逐条一致，均为既有项） |
| 本地自用验证包 | `dist/SunnyTV-v0.1.0-dev24-self.apk`（仅本机验证用，未发布），证书 SHA-256 `5e8dcd5e…` |

### 未验证 / 已知限制

- 文件夹模式的「上」只做了逻辑与既有 `folderAccordionMovesBetweenToolsHeadingsAndOffscreenRows`
  用例的覆盖，没有单独录帧比对；用户可在真机上确认手感。
- 实体电视与手机真机手感仍需用户验收。
