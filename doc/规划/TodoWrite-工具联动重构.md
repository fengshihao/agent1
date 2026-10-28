# TodoWrite 重构：从「记录工具」到工具循环的一部分

> 状态：**设计稿（2026-10-28）** · 范围：`agent_core` todo 模块、`AgentRuntime` 工具循环联动、系统提示词、消费端（Web UI / Android）

## 诊断：为什么现在的 todo_write 基本没用

现状盘点（代码位置）：

| 能力 | 位置 |
|------|------|
| `todo_write` 整表替换，≤8 项，pending / in_progress / completed | `agent_core/.../tool/agent/TodoWriteTool.java`、`todo/TodoList.java` |
| 持久化 `sessions/<id>/todos.json`（原子写，空清单删文件，坏文件不覆盖） | `todo/TodoStore.java` |
| open 项回注系统提示「## 当前任务清单」，写入后即时 `applySystemPrompt` | `TodoList.promptSection()`、`session/ProductivityAgentHost.java` |
| 审计事件 `todo_updated`（todos + open_count） | `log/AgentAuditEvents.todoUpdated` |
| 脚本桥 `$tools.todo_write` | `script/AgentToolsScriptBridge` |
| 能力文案一行 | `ProductivityToolCapabilities`（CLI 与 Android 各一份） |

四个结构性弱点：

1. **模型没有触发点**。`ProductivitySystemPromptBuilder` 的工作流段（Skill 目录 → find_caps → 一段 run_js 做完）里完全没有 todo 的位置；唯一的引导是 `find_caps` 能力清单里的一行「会话短清单，跨轮记住计划」。模型在长任务里自然不会建清单。
2. **整表替换既贵又易错**。每次更新要重发全部条目，长清单下容易漏项、id 漂移、状态回退；completed 项被 `promptSection` 刻意隐藏，模型没有低成本方式回顾「已完成什么」（只能整表重写）；没有 `todo_read`。
3. **与工具循环零联动**。`AgentRuntime.executeToolCall` / `runAgentLoop` 对 todo 一无所知：工具失败后没有 `blocked` 状态、没有 `note` 字段记录卡点；连续几十次工具调用不更新清单也没有任何提醒；run 交付点（对比 html smoke 的交付冒烟机制）不检查清单，`in_progress` 项可以无限滞留。
4. **没有消费端**。`ToolExecutionResult.details.kind=todo_list` 没有任何 UI 消费者（Web UI、Android 都不渲染），事件只是审计快照。

## 设计目标与非目标

**目标**

- 让「建清单 / 更状态」成为模型在长任务中的**默认动作**（有明确触发点，而不是靠模型自觉）
- 清单与工具循环**互相可见**：增量更新降低成本；工具失败可结构化记录卡点；长时间不更新有提醒；run 收尾与清单对齐
- 事件与 UI 可消费（JSONL 字段不回退）

**非目标**

- 不做跨会话 / 全局任务管理（仍会话级）
- 不做依赖图、嵌套子任务（保持 ≤8 项短清单；清单膨胀本身是任务拆分不当的信号）
- 不动摇「一段 run_js 串行完成整件事」的编排主脊：todo 是**跨轮长任务的进度外显**，不是碎步化工具循环的理由

## 方案总览

三条主轴，按三个 PR 落地：

| 阶段 | PR | 内容 | 回应的弱点 |
|------|----|------|-----------|
| P0 写路径 | #1 | `todo_write` 增量 ops、`note` / `blocked` 字段、`todo_read`、事件字段扩展 | 弱点 2 |
| P1 运行时联动 | #2 | TodoNudge 提醒注入、交付点收尾对齐、系统提示接线 | 弱点 1、3 |
| P2 消费端 | #3 | Web UI 清单卡渲染、Android 文案核对 | 弱点 4 |

### 一、写路径：增量 ops（P0）

`todo_write` 参数二选一，两种模式互斥：

```jsonc
// 增量模式（新）：只发变化，长清单下省 token 且不会漏项
{ "ops": [
  { "op": "add", "id": "outline", "content": "写出大纲" },                      // status 默认 pending
  { "op": "set_status", "id": "outline", "status": "completed",
    "note": "已存为 大纲.md" },                                                // note 可选
  { "op": "update", "id": "outline", "content": "写出大纲并配图" },
  { "op": "remove", "id": "draft" },
  { "op": "clear" }
]}
// 整表模式（旧，保留）：脚本桥 $tools.todo_write 语义不变
{ "todos": [ { "id": "1", "content": "…", "status": "pending" } ] }
```

校验规则（失败不落盘，回执附当前清单，沿用现有错误文案风格）：

- `ops` 与 `todos` 同时出现 → 报错
- `add`：id 不得已存在；content 非空、超长截断（沿用 80 字符）
- `set_status` / `update` / `remove`：id 必须已存在（防止模型凭空调用导致状态漂移）
- 沿用不变量：id 唯一、无空白、≤32 字符；总数 ≤8；至多一个 `in_progress`；状态无变化不写事件、不触发回调
- 增量模式同样返回整表正文回执（模型下一次更新的依据）

数据模型 v2（`TodoList.Item` 增加字段）：

- `status` 新增 `blocked`：表示卡在等外部输入 / 上游（配合 `ask_user`、工具失败），**不占用**唯一的 `in_progress` 槽
- `note`：结果 / 卡点注记，≤120 字符截断；渲染为 `- [blocked] outline 核对格式（卡点：清单里没有源数据）`
- `todos.json` 向后兼容：读 v1（缺 `note` 视为空）；只写新格式

新增 `todo_read`（零参数）：

- 返回完整清单正文（含 completed 项）+ `details(kind=todo_list)`
- 动机：`promptSection` 刻意只回注 open 项（全部完成则不占提示词，这个行为保留）；模型回顾历史、脚本桥 `$tools.todo_read`、子智能体只读访问都走它

### 二、运行时联动（P1）—— 直接回答「对工具没用」

**1. 提醒注入（TodoNudge）**

- 接线点：`AgentRuntime.executeToolCall` 中 Coach 注入位旁（`coach.maybeAugment` 同一位置）。
- 形式：**把提醒追加进 tool result 文本**（Coach 先例，零结构风险：不打断循环、不插 user 消息、不消耗轮次）。
- 触发条件（全部满足才注入）：
  - 本 run 非 `todo_write` 工具调用计数达到 K（默认 8；环境变量 `AGENT1_TODO_NUDGE_EVERY`，0 关闭）
  - 每 run 最多 2 次；剩余轮次 ≥ 2（预算边缘不打扰）
- 文案按清单状态二选一（host 注入 `Supplier<TodoList>`，避免 runtime 直接持有 store）：
  - 无清单：「这个任务步骤较多，建议先用 todo_write 建一个短清单再继续。」
  - 有 open 项：「距上次更新清单已执行 N 次工具调用，若计划有推进或变化，请用 todo_write 更新状态。」
- `todo_write` 调用本身重置计数；runtime 通过 setter 注入 nudge 组件，core 不绑定具体 host。

**2. 交付点收尾对齐**

- 接线点：`runAgentLoop` 交付点（assistant 无工具调用、html smoke 检查旁）。
- 清单仍有 `in_progress` / `blocked` 项 → 以 `appendSmokeNote` 同款机制追加 user 注记进 transcript（不触发模型调用）：
  「本轮结束，任务清单仍有未完成项：…。下一轮继续前先对齐状态。」
- `run_completed` 审计事件携带清单终态快照字段（只加不改，JSONL 旧字段不回退）。

**3. 系统提示接线**

- `ProductivitySystemPromptBuilder` 工作流段补一条（两个变体分别措辞，避免与主脊冲突）：
  - `WORKFLOW_DIRECT`：「多步骤任务先用 todo_write 建短清单（≤8 项），完成一项更新一项。」
  - `WORKFLOW_WITH_SCRIPT`：「跨多轮的长任务先用 todo_write 建短清单；单轮能做完的不必建。」
- `ProductivityToolCapabilities`（CLI 与 Android 两份副本）文案同步增量语义与 `todo_read`。

### 三、消费端（P2）

- 事件：`todo_updated` 新增 `mode`（`replace` | `ops`）与 `changed_ids`；旧字段 `todos`、`open_count` 全保留。
- Web UI / Android：`TOOL_EXECUTION_END` 的 `details.kind=todo_list` 渲染为清单卡（对齐聊天壳既有工具卡样式）；Android 共用 core，除能力文案外无代码改动。
- provenance（条目 ↔ 工具调用关联统计）：**待决**，见下。

## 兼容性约束

- `$tools.todo_write` 现有整表调用语义不变（增量 ops 是可选新路径）
- `todos.json` v1 可读，坏文件仍不覆盖
- `todo_updated` 事件只加字段不删字段
- Android 共用 `agent_core`，无分层改动

## 分阶段落地与测试计划

**PR #1（P0 写路径）**

- 改动：`TodoList`（ops 解析、`note` / `blocked`）、`TodoWriteTool`（双模式）、`TodoStore`（v2 兼容读写）、新增 `TodoReadTool`、`AgentAuditEvents` 字段扩展、`ProductivityAgentHost` 注册 `todo_read`
- 单测：`TodoListTest`（各 op 分支、未知 id、id 重复、blocked、note 截断、v1 兼容读）；`TodoWriteToolTest` 增补（ops 模式、ops/todos 互斥、增量回执、`todo_read`）；`ProductivityAgentHostTest` 回归
- 命令：`./java_agent/gradlew --no-daemon -p java_agent :core:test :cli:test` + `./scripts/ci-local.sh fast`

**PR #2（P1 联动）**

- 改动：`AgentRuntime`（TodoNudge 接线、交付点收尾）、`ProductivitySystemPromptBuilder`、`ProductivityToolCapabilities` ×2
- 单测：`AgentRuntimeTest` 增补（fake LlmClient 驱动 K 次工具调用后断言 tool result 含提醒；`todo_write` 重置计数；每 run ≤2 次；剩余轮次不足不注入）；提示词快照断言
- 命令同上

**PR #3（P2 消费端）**

- Web UI 清单卡渲染；`./agent1 ui` 手测记录截图进 PR

## 待决问题

1. `blocked` 是否强制携带 `note`：当前倾向**不强制**（校验保持简单），靠文案引导「blocked 时建议写清卡点」。
2. provenance（把每次工具调用关联到 `in_progress` 条目，入事件或 store）：审计价值 vs 复杂度，观察 P0/P1 落地后的实际需要再定。
3. K（默认 8）与每 run 上限 2 的取值：上线后按 `events.jsonl` 里 `todo_write` 频率与 run 长度分布回调。
4. 子智能体（explore / general）是否共享会话清单：当前实现共享同一 `sessionDir`，是否需要隔离待定。
