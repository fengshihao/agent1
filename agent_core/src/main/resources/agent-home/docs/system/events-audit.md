# 事件审计（events.jsonl）

路径：`{agentRoot}/logs/events.jsonl`（或 `AGENT1_EVENTS_LOG_FILE`）。与 Run 对话事件（`run_started`、`tool_call` 等）同文件追加。

## 自进化 / Catalog / Coach（P.4）

| type | 含义 | 典型 source 字段 |
|------|------|------------------|
| `catalog_sync_checked` | manifest diff 完成 | `catalog_sync_status`、`cli_sync_check` |
| `catalog_sync_completed` | 条目 apply 完成 | `catalog_install`、`cli_sync_apply`；自动 native 为 `execute_script_auto_native`（含 `plugin_name`） |
| `promotion_completed` | staging → shared/local 成功 | — |
| `promotion_rejected` | 晋升校验失败 | — |
| `coach_fired` | 工具结果追加 `[coach] hookId` | 含 `hook_id`、`tool_name`、`advice`（截断） |

Run 内触发时带当前 `sessionId` / `runId`；CLI 单独执行 sync 时 `sessionId=cli`。

## 排查

```bash
grep catalog_sync_completed logs/events.jsonl | tail
grep coach_fired logs/events.jsonl | tail
```
