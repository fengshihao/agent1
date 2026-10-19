# 22 — 工作区 tmp 生命周期与文件台账（设计记录）

> 背景：2026-10-18 真机会话实测，一次「调研+生成 16 页 PPT」的会话目录 18MB，其中 **12.4MB
> 是两次 renderHtmlDeck 留下的 `tmp/html2pptx-render-*` 组包目录**（每次 6.2MB，含 16 张整页
> PNG），Android 上无任何回收路径、产物库不可见、用户无法手动清。
> 用户提议：coach 环节要求 AI 任务结束删 tmp + 从任务开始记录生成文件清单/总空间、结束时反馈给 AI。
> 本文档记录提议与评估结论，**后续单开 session 设计实现**。

关联：[10-运行时钩子与Coach提示.md](./10-运行时钩子与Coach提示.md)、`doc/集成/HTML2PPTX.md`、
`java_agent/doc/runtime-data-layout.md`（workspace/tmp 约定）。

## 状态图例

`未开始` / `进行中(谁)` / `完成(commit)` / `搁置(原因)`

## 1. 事实基础（为什么必须宿主管）

- QuickJS `fs` 模块只有 `readFileSync/writeFileSync/existsSync/unlinkSync/mkdirSync` +
  promises 版，**没有 rmdir/readdirSync**（weizhi `src/node_api.c`）；平台 caps
  `files.*` 也只有 list/read/write/mkdir/rename/move/undo/zipExtract/zipCreate——
  **JS 侧删不了目录树**，组包目录清理只能宿主做。
- 现有约定：`tmp/html2pptx-*` 中间文件（input html、shots 回执）已由包内 `finally` 自清；
  **组包目录有意保留**（与 pptx.js 一致，当时为桌面调试方便）——该决策在 Android 上不成立。
- 同类残留：`tmp/docx-render-*`（docx@1.0.0）、`tmp/webview_exec/*.b64`、
  `tmp/html2pptx-native-*`（native 模式组包）。

## 2. 用户提议与评估

**提议**：① coach 要求 AI 做完任务删 tmp；② 任务开始起记录生成文件清单；③ 结束时把
清单与总空间反馈给 AI。

**评估**：方向对，但锚点建议从「AI」挪到「宿主」——AI 的清理行为不可持续约束（忘删、
误删、多一轮模型开销），而 tmp 的产生方与生命周期宿主完全知情：

| 层 | 手段 | 评价 |
|----|------|------|
| L1 工具自清 | 包内 `finally` 删自己的中间文件 | ✅ 已有（shots/input）；组包目录 JS 删不了，转 L2 |
| **L2 宿主清理（推荐主路径）** | run 结束/会话切换时扫 `workspace/tmp/`，按前缀+年龄清；配额（如单会话 tmp 200MB）超限 LRU；会话删除时 tmp 随会话删 | 确定性 100%，零模型开销；实现点：`ProductivityAgentHost` run 收尾或 `SessionWorkspacePaths` 层 |
| L3 文件台账（给用户/诊断） | run 起止各一次 workspace 快照（文件+大小），events.jsonl 记 `workspace_snapshot`；产物库显示「本会话占用 X MB，可清理 Y MB」 | 价值在**用户可见与可清理**，不在喂给 AI |
| L4 coach 提醒（兜底） | run 结束 tmp 超阈值 → 下一轮 user 后缀提示 AI 调宿主清理工具 | 可选；作为 L2 的补充而非主路径 |

**结论**：L2 为主 + L3 轻量台账（面向 UI）+ L4 兜底提示；**不建议**把清理责任放在 AI
（提议 ①③ 的"反馈给 AI"改为"反馈给用户 + 宿主自动处理"）。

## 3. 任务拆分

| 任务 | 内容 | 状态 |
|------|------|------|
| T1 | 宿主 tmp 清扫：run 收尾清 `tmp/` 下 N 小时前的 `*-render-*`/`html2pptx-*`/`webview_exec` 等已知前缀；桌面 CLI 与 Android 同一实现（agent_core 层） | 完成（2026-10-19，落地结论见下） |
| T2 | 配额：单会话 workspace tmp 上限（建议 200MB）+ LRU 淘汰；超限记 audit 事件 | 完成（2026-10-19，与 T1 同一实现） |
| T3 | 台账：run_started/run_completed 时 workspace 快照事件（文件数/总字节，不含内容）；产物库占用展示与「清理临时文件」入口 | 完成（2026-10-19，落地结论见下） |
| T4 | coach 兜底（可选）：tmp 超阈值提示（复用 ProductivityCoach 机制） | 搁置（可选层；L2 确定性清扫 + L3 用户入口已覆盖，真机观察后再定） |
| T5 | html2pptx/pptx.js 组包目录保留策略反转评估（宿主清扫落地后，包内是否还需要保留） | 完成（评估结论：**不反转**，见下） |

### T1/T2 落地结论（2026-10-19）

- **`agent_core/.../workspace/WorkspaceTmpCleaner`**（纯 IO，桌面 CLI 与 Android 同一实现）：
  - 代管前缀 `MANAGED_PREFIXES = [html2pptx-, docx-, pptx-render-, webview_exec]`
    （从包源码实际 tmp 写点逐一枚举：html2pptx 9 种、docx 3 种、pptx 1 种 + webview_exec）。
  - `clean(workspace, minAge, quota)`：先删「树内最新 mtime ≥ 6h 前」的代管条目
    （**树内最新**而非目录 mtime——AI 刚迭代过的组包目录即使整体很老也保留）；
    再对幸存者做配额 LRU（默认 200MB，mtime 升序删到限内，`quotaTrimmed` 标记）。
    IO 失败跳过单条目不整体失败；返回 `{removedCount, removedBytes, quotaTrimmed}`。
  - 接线 `ProductivityAgentHost.runUserMessage`：finally 里 `scheduleTmpClean`
    （**后台 daemon 单线程**，不拖慢回话收口——待定问题①的答案）；删了东西写
    `tmp_cleaned` audit 事件（removedCount/removedBytes/quotaTrimmed）。
- 测试 `WorkspaceTmpCleanerTest`（7 用例：前缀面/递归删/树内新文件保护/配额 LRU/
  无 tmp no-op/快照统计/缺失容错）。**坑**：agent_core 是 JUnit5（jupiter）工程，
  JUnit4 风格测试（@Rule/@org.junit.Test）会被 gradle **静默忽略**（无 vintage
  engine，BUILD 还绿）——新测试务必 `org.junit.jupiter` + `@TempDir`。

### T3 落地结论（2026-10-19）

- **快照事件**：run 开始前/结束后各写一条 `workspace_snapshot`
  `{phase: start|end, fileCount, totalBytes, tmpBytes}`（聚合数字不逐文件——待定
  问题②的答案；`WorkspaceTmpCleaner.snapshot()` 递归统计）。
- **Android 产物库入口**：`ArtifactLibraryStore.tmpCleanableBytes`（各会话 tmp 代管
  字节合计）+ `cleanAllSessionTmp`（minAge=0、配额 0 全清，前缀与判定复用
  agent_core `WorkspaceTmpCleaner`，两端单一真源）；`ArtifactLibraryUiState
  .tmpCleanableBytes > 0` 时顶栏出「清理临时」按钮 → `ViewModel.cleanTmp()`
  → Toast 已清理量并刷新。测试 2 用例进 `ArtifactLibraryStoreTest`。
- Android 依赖 published core：改 core 后须 `./publish-java-agent-core.sh` 再编 Android。

### T5 评估结论（2026-10-19）

**不反转**。QuickJS fs 没有 rmdir/readdirSync（weizhi `src/node_api.c`），包内 JS
**永远删不了目录树**——「组包目录有意保留」不是可选项而是技术必然；宿主清扫
（T1）是唯一回收路径且已落地。包内现状（finally 自清单文件中间件 + 组包目录留给
宿主）即为终态，html2pptx/pptx.js/docx 无需改动。

## 4. 待定问题（已决策）

- 清理时机 → **后台线程**（daemon 单线程 executor，run 收尾投递）。
- 台账快照体积 → **纯聚合**（fileCount/totalBytes/tmpBytes，不逐文件不 topN）。
- 跨会话全局 tmp 视图 → 不做聚合页；产物库全局「清理临时」按钮 + 各会话
  workspace_snapshot 事件已覆盖可见性。
