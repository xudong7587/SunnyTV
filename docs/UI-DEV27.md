# dev27 界面与播放修复（2026-09-22）

基线为 dev26 公开版（提交 1d455cd）。本轮的每一项都来自用户实机反馈，逐条记录现象、根因、改动与验证。

## 1 详情页默认焦点与返回顶部

**现象**：进入媒体详情后焦点停在顶部（钉住导航栏）而不是「继续播放 / 播放」；从顶部按下跳到下排按钮后，
再按上回不到顶部。

**根因**（两条独立问题）：

- 焦点记忆把导航栏也记成了「这一页上次待过的地方」：`FocusTile` 对任何获得焦点的 tile 都写
  `focusMemory[page]`，导航栏是 `nav:` 前缀的 tile，因此用户按上到过顶部一次之后，
  再次进入同一个详情页时恢复焦点就落在顶部导航上，而不会走「进入页面默认聚焦」那条路径。
- 详情页动作行的 `向上` 被行自己的预览处理器消费掉（原来是「按上把标题滚回来」），
  预览阶段先于根节点的冒泡处理器执行，所以根节点负责的「上移到顶部导航」永远收不到这个按键。

**改动**：

- `feature/ui/Components.kt`：`nav:` 前缀的 tile 不再写入页面焦点记忆（导航栏由每页自己的
  `autoFocus` 决定，内容 tile 继续记忆）。
- `feature/ui/MediaDetailContent.kt`：动作行按上改为把焦点交给顶部导航；页面注册
  `revealTop`，导航获得焦点时会把详情页滚回顶部；剧集把「继续播放」排在「从头播放」之前并作为默认焦点；
  动作行第一个按钮持有 `entryActionFocus`，动作行下方的第一段内容按上会回到它（即使该行已滚出屏幕）。
- `feature/ui/NavigationBridge.kt`：新增 `contentEntryId`（导航按下要落到哪一颗按钮）与
  `navEntryId`（按上要落到哪一个栏目），`enterContent()` / `enterNavigation()` 优先使用它们；
  `MainActivity.kt` 的顶部导航按当前页面设置 `navEntryId`。

**验证**：`Dev27UiTest.detailPageFocusesThePlayButtonAndUpStillReachesThePinnedBar`（进入详情 → 焦点在
`detail-play` → 按上 → `nav:媒体库` → 按下 → 回到 `detail-play`）与
`Dev27UiTest.seriesDetailFocusesResumeBeforeStart`（剧集默认焦点与顺序）。

## 2 排序按钮、排序窗口与比特率

**现象**：①选了列表里靠下的排序键（例如「官方评级」以下）后，再次打开排序窗口时焦点落在「取消」上；
②「比特率」排序无效；③排序窗口要翻页才能选完；④排序按钮表意不清（箭头叠在图标右下角）。

**根因**：①窗口内容超过 440dp 后变成滚动列表，选中的那一项没被组合出来，`FocusRequester` 请求落空，
焦点就留在了第一个可聚焦的「取消」上；②**比特率排序**：见下方实测——Emby 的 item 查询接受这个参数、
但不会按它排序（既不报错也不重排），所以客户端必须自己排；③④属于设计问题。

**比特率实测（2026-09-22，用户自己的 Emby，电视剧库 65 条）**：

- 先按原来的写法把排序键直接交给服务端、客户端不排队（临时探针构建）。选「名称」→ 列表按名称重排；
  选「比特率」→ 返回的顺序与「加入日期」逐条相同。说明参数被接受、`Bitrate` 这个键被服务端忽略
  （若被拒绝会走错误路径）。Emby 自己的网页端有「比特率 / 文件尺寸」排序，用的是它自己的另一条查询路径，
  与 `GET /Users/{id}/Items` 这个接口不一致。
- 因此：`SortBy=Bitrate|Size` 不再发给服务端（发也无效），改为请求 `DateCreated` + `Fields=…,MediaSources`，
  在设备上按每个条目最大版本的 `Bitrate` / `Size` 排序（降序默认，再点一次升序）。
- 这种排序请求一次取 200 条（普通媒体库一页即可覆盖，`电视剧` 的 65 条一次排完）；
  超过 200 条的库在继续翻页时按同样规则排每一页，因此超大库的全局顺序以 200 条为一个窗口。

**改动**：

- `feature/ui/ChoiceDialog.kt`：新增 `columns`，大于 1 时按固定网格铺开（排序用 3 列），
  所有选项同屏、无需滚动，选中项总能第一时间拿到焦点。
- `core/model/MediaLogic.kt`：新增 `isLocalSort()` 与 `localSort()`：Bitrate / Size 在本地排序。
- `source/emby/EmbySource.kt`：这两种排序改为取回带 `MediaSources` 的一页（一次 200 条）并在设备上按
  最大版本的码率 / 体积排序，不再出现"选了没反应"。
- `feature/ui/Components.kt` + `LineIcon.kt`：`Action` 支持自定义左侧图形；
  `SortDirectionArrows` 画一对等高的上下箭头，当前方向实色、另一个淡显，「随机」显示随机图标。

**验证**：`Dev27UiTest.sortChooserShowsEveryKeyWithoutScrollingAndFocusesTheCurrentOne`（12 个键全部
`assertIsDisplayed`，选 Bitrate 后重开窗口焦点仍在 Bitrate 且不在取消上）；纯 Kotlin 契约新增 8 项
（`isLocalSort` 与 `localSort` 的升降序、缺失媒体数据的稳定性）；用户手机上实测：电视剧库选「比特率」后
首屏由 `人生复本 / 铁拳教育 / 风骚律师`（加入日期序）变为 `边水往事 / 不眠日 / 超凡女仆`（码率序）。

## 3 搜索页布局

**改动**：`feature/ui/BrowseScreens.kt` 的 `SearchScreen` 把键盘移到搜索栏上方，
键盘整体居中；退格与清除移到字母与数字两栏之间；触摸端键盘可横向滚动，窄屏不会挤出屏幕。
搜索键与图标键补上 `testTag`，可被无障碍与测试定位。

**验证**：`Dev27UiTest.searchKeyboardSitsAboveTheBarWithCorrectionsInTheMiddle`（键盘在搜索栏之上、
退格在字母右侧且清除在数字左侧）。

## 4 视图（展现方式）按钮

**改动**：媒体库工具栏的「视图」按钮单击即按 海报 → 背景 → 横幅 → 海报 循环，并写入该媒体库的
`libraryArtworkModes`；不再弹出子菜单（设置页里的全局默认仍保留选择窗口）。

**验证**：`Dev27UiTest.artworkButtonCyclesThroughTheThreeModes`。

## 5 字体：公开版覆盖自用版后仍可选

**背景**：开发期的自用版本把第三方字体打进 APK 资源；公开版按版权要求不带字体文件，
覆盖安装后这些字体从应用里消失，用户此前能用的字体也就选不到了。

**改动**：

- 新增 `core/storage/FontLibrary.kt`：字体一律以「设备上的文件」为准——导入的文档、放在
  `files/fonts`（以及外部存储对应目录）里的字体文件、以及构建时打包进资源的字体。
  应用首次启动会把随包字体**复制一次**到 `files/fonts` 并记录标记，之后即使换装不带字体的公开版，
  文件仍在、仍会列在「外观 · 字体」里可选。
- `core/storage/FontTypefaces.kt`、`ConfigStore.kt`、`feature/ui/SettingsScreen.kt`、
  `feature/ui/Theme.kt`、`SunnyApp.kt`：字体选择改为读取上述目录；存储的字体不存在时回退到系统字体
  （不会因为缺字体导致启动或界面异常）。
- `app/build.gradle.kts` + `scripts/build-selfuse.ps1`：自用版通过 `-PsunnytvSelfUse=true` 才会把
  `app/src/selfUse/assets` 加入资源；该目录已被 `.gitignore` 忽略，公开构建与仓库都不含字体文件。

**验证**：`Dev27UiTest.fontFilesStaySelectableAndAFontFolderReloadKeepsTheChoice`（字体文件出现在目录里
即可选、保存后仍是该字体、文件被删除后回退到系统字体）。

**未验证**：自用版 → 公开版的真实覆盖安装与字体选择需要用户在手机上确认（安装需用户在设备上同意）。

## 6 画面「裁切」裁得过多

**根因**：`PlayerActivity` 在 Media3 的 `RESIZE_MODE_ZOOM` 之上又叠了 `scaleX/scaleY=1.12`，
即在「按屏幕比例挤掉黑边」之后又额外放大 12%，每条边都会被切掉一截。

**改动**：去掉额外的 1.12 放大，只保留 `RESIZE_MODE_ZOOM` 本身的比例填充——18:9 的片子裁两侧、
16:10 的片子裁上下，正好占满屏幕且不再多裁。

**验证**：代码变更明确；实机画面效果待用户在手机上确认（本机无实体电视，模拟器不能代表片源比例）。

## 7 播放器图标

**改动**：`feature/ui/LineIcon.kt` 的 `rewind` / `forward` 改为两个实心三角，去掉圆形箭头与
`PlayerActivity` 里 10 秒的数字角标；`sleep` 重绘为新月（两条同向弧线），按用户提供的手绘样式。

**验证**：图标为矢量路径，随 `assembleDebug` 编译通过；观感由用户在实机确认。

## 8 STRM 经 Emby 服务端读取（沿用上一轮未提交的改动）

工作区里原本还留着一份未提交的 dev27 改动：直连 STRM 失败（超时 / 拒绝 / 409 等）时，
改用 Emby 的服务端取流入口再试一次，设置页增加「STRM 经 Emby 服务端读取」开关；
同时把 HTTP 409 的文案指向失效直链或令牌。本轮保留并一并验证（`PlaybackRecovery.shouldUseServerFallback`
与 `statusReason(409)` 均有契约测试），没有新增开关之外的网络边界变化。

## 验证汇总

| 项目 | 结果 |
| --- | --- |
| 纯 Kotlin 契约测试 | 165 / 165（dev26 公开版 144 + 上一轮未提交的 13 + 本轮 8） |
| Gradle 单元测试 | 72 项方法，0 失败 |
| `scripts/check-project.py` | 通过 |
| `lintDebug` | 0 错误 / 28 警告（与 dev25、dev26 逐条一致） |
| Android 构建 | `assembleDebug`、`assembleDebugAndroidTest`、`assembleRelease` 通过 |
| 仪器化交互测试（API 30 TV 模拟器） | 65 项执行、失败 6 项，全部落在 `docs/KNOWN-ISSUES.md` 既有清单内，无新增 |
| 本机自用版 | 通过 `scripts/build-selfuse.ps1` 构建，APK 内含两份字体，用户手测 |

未验证项：实体电视的遥控器手感与画面裁切观感、真实 MP / Alist 链路下的服务端兜底效果、
自用版覆盖安装时的字体保留（安装需要用户在手机上确认）。这些不写成已完成。
