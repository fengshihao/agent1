# 贡献指南

感谢你愿意参与 `agent1` 的建设。

## 开发环境

```bash
cd python_agent
uv sync
```

或在仓库根目录：`uv sync --project python_agent`。

## 代码规范

- 保持改动最小且聚焦
- 新增行为要同步更新 README / docs
- 工具相关改动需考虑跨平台（macOS / Ubuntu / Windows）
- 涉及模型调用链路的改动，需确认日志字段不回退

## 提交流程

1. 新建分支：`feature/xxx` 或 `fix/xxx`
2. 完成开发并自测
3. 提交 PR，描述：
   - 背景与目标
   - 变更点
   - 验证方式
   - 风险与回滚方案

## 建议自测清单

- `cd python_agent && uv run agent1 "1+1等于几" --no-stream`
- `cd python_agent && uv run agent1 "请用 run_python 运行 print(2+3)" --no-stream`
- 检查 `logs/agent1.jsonl` 是否有 `run_started/model_request/model_response/usage/run_completed`

## Issue 建议信息

- 环境信息（OS、Python、Shell）
- 复现步骤
- 预期行为与实际行为
- 关键日志片段（可脱敏）
