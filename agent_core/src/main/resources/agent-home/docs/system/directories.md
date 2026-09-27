# 目录说明

```text
<agentRoot>/
  sessions/<sessionId>/workspace/   ← 文件工具唯一可写区（RW）
  shared/catalog/                   ← 云端 sync 落盘（只读）
  shared/local/                     ← promote 晋升（只读；写入仅 API）
  docs/system/                      ← 本手册（只读）
  docs/capabilities/                ← 能力索引（只读；由 sync/promote 更新）
  logs/events.jsonl                 ← 审计事件
  sync/state.json                   ← catalog 对比状态
```

区外写入：`promote_request`、`catalog_install` / `sync apply`，禁止用 write_file 写 shared 或 docs/system。
