# TodoWrite：静态约定 + write/read + UI（不进 system）

> 状态：**Phase 1 实现中（2026-10-29）** · 清单**不**回注系统提示（保护 KV cache 前缀）

## 结论

- **模型**：系统提示仅含**固定**维护约定（`todo_write` / `todo_read`）；动态清单通过 **`todo_write` 回执**与按需 **`todo_read`** 进入上下文。
- **用户**：Web UI「任务清单」面板（`GET /api/sessions/{id}/todos` + SSE `todoList`）；工具链仍可展开调试。
- **不做（本期）**：system 末尾 `promptSection`、ops 增量、`TodoNudge`、blocked/note。

## 代码

- `ProductivitySystemPromptBuilder` 工作流第 5 条
- `ProductivityAgentHost`：注册 `todo_read`；`applySystemPrompt` 不再拼清单
- `TodoReadTool` · `TodoWriteTool`（整表，无 `onChanged → applySystemPrompt`）
- `DesktopUiGateway.loadSessionTodos` · `UiHttpServer` `/todos` · `UiEventCodec` `todoList`
- `java_agent/web_ui` 清单面板

## 后续（待观察）

- ops、交付点收尾、Android Compose 清单卡（若与 Web 不同步）
- 若 `cached_tokens` 仍敏感：禁止任何动态 system 补丁（已满足）

## 历史设计

完整三阶段草案见同目录 git 历史；ops / nudge 等已 defer。
