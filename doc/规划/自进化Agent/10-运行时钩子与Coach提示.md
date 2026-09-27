# 10 — 运行时钩子与 Coach 提示

> 在特定条件下**主动告诉工作 Agent「可以/应该做什么」**，减少盲目重试与越权尝试。钩子 **不替用户做决定**，只注入**短、可执行**的建议（含工具名、路径约定）。

## 设计原则

| 原则 | 说明 |
|------|------|
| **触发可解释** | 每条提示带 `hookId` + 触发原因（可进 `events.jsonl`） |
| **短** | 单条建议 ≤ 400 字；不重复 [07] 全文环境摘要 |
| **可关** | `AGENT1_COACH=0` 或 manifest 开关 |
| **不伪造用户消息** | 以 **Coach 通道** 注入（见下），避免假装用户说话 |
| **与权限一致** | 只建议已有能力（promote、sync、write_file 到 workspace 等） |

## 注入方式（实现候选）

1. **Tool result 后缀**（P1 最简单）：在触发工具返回末尾加 `\n\n---\n[coach] …`  
2. **Ephemeral system 追加**（P2）：本 Run 内临时追加一段 system，**不写入** transcript 永久链（若 runtime 支持）。  
3. **Dedicated `coach_hint` 事件**（P2）：JSONL + 下一轮开头由 host 拼进 context。

建议先做 **1**，对现有 `ProductivityAgentHost` 改动小。

## 钩子注册表（设想场景）

阈值可在 `agent.manifest.json` 的 `coach.triggers` 配置；下表为默认值讨论稿。

### 工作区与文件

| hookId | 触发条件 | 告诉 AI 什么 |
|--------|----------|--------------|
| `file.large_write` | 单次 `write_file` / 累积 edit 后文件 **> 64 KB**（可配） | 大内容应放 workspace 文件，回复只摘要；若需复用可整理进 `staging/` 并 **promote_request** |
| `file.many_versions` | 同一相对路径 **≥ 5 次** edit/write 本 Run | 考虑拆模块或写 `notes/设计.md`；稳定后晋升 script |
| `workspace.total_bytes` | `workspace/` 总大小 **> 20 MB** | 清理 `.spill/`、归档到 `artifacts/`；避免把二进制塞进 JS |
| `path.outside_attempt` | 工具返回「路径超出工作区」 | 重申仅 workspace 可写；读 shared/docs 用只读工具；改 shared 用 **promote** / **sync** |

### QuickJS / execute_script

| hookId | 触发条件 | 告诉 AI 什么 |
|--------|----------|--------------|
| `script.inline_long` | `execute_script` 使用 **inline code** 且 **> 80 行或 > 8 KB** | 请 `write_file` 到 `*.js` 再 `file` 执行，便于行号与调试（见 [11](./11-QuickJS调试与行号映射.md)） |
| `script.fail_repeat` | 同一 Run 内同一脚本（file 或 code hash）**失败 ≥ 3 次** | 先读 [11] 结构化错误中的 **userLine**；查 `docs/system/tools-and-quickjs.md`；缩小变更面 |
| `script.timeout` | 执行超时 | 拆批处理、写中间结果到 workspace；勿增大单次循环 |
| `script.use_tools` | 脚本多次调用 `$tools` 失败 | 确认工具名在 exposed 列表；脚本内勿递归 `execute_script` |

### 进化与同步

| hookId | 触发条件 | 告诉 AI 什么 |
|--------|----------|--------------|
| `staging.ready` | 检测到 `workspace/staging/skills/` 或 `scripts/` 有完整条目且 Run 将结束 | 可 **promote_request** 到 `shared/local/`；区外勿 write_file |
| `catalog.pending` | `sync/state.json` 中 **pending ≥ 1** 且本 Run 涉及相关 skill 名 | 提醒用户或调用 **sync apply**；catalog **只读** |
| `promote.rejected` | Promotion API 拒绝（P2+ 若审查可拒） | 看 audit 原因；回 staging 修改 |

### 对话与上下文

| hookId | 触发条件 | 告诉 AI 什么 |
|--------|----------|--------------|
| `context.near_limit` | transcript 轮次或 token 估计 **> 80%** 上限 | 用 `chat_history` 检索；要点写入 workspace 文件再继续 |
| `tool.budget` | 本 Run `tool_call` 次数 **> 70%** `AGENT1_MAX_TOOL_CALLS_PER_RUN` | 优先写结论文件；减少 explore 循环 |
| `run.long` | 单 Run 时长 **> N 分钟** | 分段：保存 checkpoint 到 `artifacts/`，下条消息继续 |

### 安全与误用

| hookId | 触发条件 | 告诉 AI 什么 |
|--------|----------|--------------|
| `secret.pattern` | write/staging 内容命中密钥 heuristic | 勿 promote；改 env；从文件删除敏感信息 |
| `docs.system_write` | 试图改 `docs/system`（若已 enforce） | system 文档只读；能力说明在 promote/sync 后由系统更新 |

## 与里程碑关系

| 阶段 | 范围 |
|------|------|
| **H1** | `path.outside_attempt` + `script.inline_long` + `file.large_write`（纯 Java 计数，tool result 后缀） |
| **H2** | `script.fail_repeat` + `staging.ready` + `catalog.pending`（依赖 M3/S1） |
| **H3** | 近 token/工具预算、ephemeral system |

建议：**H1 跟 M2/M3 同期**（权限收紧后立即给正确引导）。

## 验收示例

- 连续 3 次 `execute_script` 报错 → 第 3 次 tool result 含 `[coach] script.fail_repeat` 与「改用 file + 看 userLine」。  
- 单次写入 100KB → 返回含 `[coach] file.large_write` 与 promote/staging 建议。
