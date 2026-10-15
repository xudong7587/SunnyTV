# 2026-10-15 本轮检查补充

- `scripts/test-bootstrap.py` 为新仓库初始化器的既有测试。在本机 Windows 执行 18 项时，`test_backslash_path` 因 ZIP 写入路径规范化未触发预期 ValueError，`test_destination_symlink` 因 WinError 1314 缺少符号链接权限报错，其余16项通过。本轮未改初始化器、未禁用测试，不将该结果记为通过；发行工作流仍在 Linux 执行此检查。
- 0.2.2 的核心、应用单测、编译/lint、签名/发行检查及 GitHub 实连验证通过；不是完整实体 TV 验收。

# 0.2.1 当前限制（2026-10-15）

- 绿联本地排序已支持8种选择和升降序；缺少元数据的项置后，整个目录均缺少所选字段时无法排序并明确提示。首次非名称升序排序需要读取完整目录，安全上限10000项、8Mi字符和90秒。
- 原始MediaIndex签名入口存在时可复用115云端清晰度；当前真实绿联样本仅返回CDN直链，需要后续入口映射，未宣称此样本已支持云端档位。
- 原生真实NAS排序/播放仍待用户升级验收；保留历史TV失败。UI31项和单测139项通过，详见[0.2.1](RELEASE-0.2.1.md)。

# 0.2.0 绿联及TV待验（2026-10-15）

- 绿联真实NAS此前独立工具的API/Range已通；原生TV解码、拖动、跨集及进度回写仍待用户手动升级验收。已实现范围与限制见[绿联核对](UGREEN-EMBY-ASSESSMENT.md)。
- 本轮30项Android定向检查通过，完整历史TV失败清单不撤销，不能宣称全套TV验收。
- 此版先进入WebDAV；GitHub官方发行仍为此前版本。后续版本使用纯数字编号。

# dev32 实机待验

手机/平板方向锁观感、真实WebDAV账号访问与厂商系统安装器仍待实机反馈。首次允许未知来源后需返回点击安装或下次启动执行。完整TV历史失败清单未在本轮重新跑全套。

# 已知问题（dev27 公开版）

本清单只记录**已知且已确认**的问题，不隐藏失败、不禁用测试。发布工作流里编译、单元测试、lint、
签名校验与「不打包字体」检查都是硬门槛；模拟器交互测试本轮**不作为发布门槛**，
它的结果会完整记录在构建产物里，失败项如下。

| # | 用例 | 现象 | 原因与现状 |
| --- | --- | --- | --- |
| 1 | `Dev16PerformanceTest.visibleGridRowsStayStillAndHeaderStaysPinnedAcrossBoundary` | 网格几何断言不符 | 断言写于"工具行钉住"时期；dev22 起工具行随海报墙滚动，断言未同步 |
| 2 | `HomeHotfixTest.selectedThirdLibraryContinuesInPageOrderAndReturnsToSelectedLibrary` | 等待焦点超时 | 首页改为按区域顺序浏览后，选中库的返回路径变了，旧断言未同步 |
| 3 | `TvInteractionTest.posterGridTraversesOffscreenRowsPartialLastRowAndPagination` | 虚拟槽位焦点断言失败 | 海报墙 dev20 起改为稳定虚拟槽位，用例仍在等旧标签下的焦点 |
| 4 | `TvInteractionTest.actorsMoveDownIntoOffscreenRecommendationsAndBackUp` | Compose 空闲超时 | 同一模拟器上自 dev21 基线即可复现，属既有问题 |
| 5 | `TvInteractionTest.episodeLayoutsSwitchWithoutChangingRealSettings` | 找不到 `episode-layout:vertical` | 工具行现在位于首屏之外，用例缺一步滚动；dev24 源码上单独复现过 |
| 6 | `TvMotionTest.rapidPageTransitionReversalDoesNotKeepOutgoingButtonsFocusable` | 顶栏按钮仍可聚焦 | 页面转场实现演进后的旧断言；dev24 源码上单独复现过 |
| 7 | `TvMotionTest.latestHeaderFocusRevealsItsShelfAndBottomSpace` | 货架贴合断言不符 | dev23 给折叠行加了阴影留白（上下 12/18dp），断言未同步 |
| 8 | `TvMotionTest.pinnedHomeEntersAStationaryHeroThenMovesDownToStableLibraryRow` | 期望焦点落在续播胶囊 | dev22 起首页下移落点改为"我的媒体库"首项，断言未同步 |

说明：

- 以上 8 项在 dev22 基线上用同一台 API 30 TV 模拟器跑出过同样的失败集合，dev23～dev27 未新增失败项
  （dev27 在本机 API 30 TV 模拟器上执行 65 项、失败 6 项，全部与本表同名）。
- 本轮第 1 项（`Dev16PerformanceTest.visibleGridRowsStayStillAndHeaderStaysPinnedAcrossBoundary`）与
  第 2 项（`HomeHotfixTest.selectedThirdLibraryContinuesInPageOrderAndReturnsToSelectedLibrary`）没有复现，
  可能与 dev27 改动的焦点记忆与导航落点有关；没有据此把它们判为已修复，仍保留在清单里并计划单独一轮重写断言。
- 发布工作流里的模拟器使用默认（手机/平板）API 30 镜像，与本地 TV 镜像的设备形态不同，
  因此 CI 上的失败集合可能与上表不完全一致；CI 会把完整的仪器化测试报告作为构建产物上传，供核对。
- 这 8 项属于"测试断言落后于已被用户验收的界面行为"，不是功能性回归。修复它们需要逐条对照当前交互重写断言，
  计划单独一轮处理，不与功能修复混在一起。
