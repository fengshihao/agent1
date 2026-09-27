# 晋升（promote）

将 **workspace/staging/** 中整理好的 skill 或 script 沉淀到 **shared/local/**。

## 布局

```text
workspace/staging/skills/<name>/SKILL.md
workspace/staging/scripts/<name>.js   # 可选 <name>.meta.json
```

## API

- **promote_request**（可选 `note`）：规则扫描 → 自动审查通过 → 复制到 `shared/local/`。
- 工作 Agent **禁止** `write_file` 写入 `shared/local` 或 `shared/catalog`。
- 晋升后更新 **docs/capabilities/local.***.md**；审计写入 **logs/events.jsonl**（`promotion_completed` / `promotion_rejected`）。
