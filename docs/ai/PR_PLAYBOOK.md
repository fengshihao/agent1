# PR  playbook

## 分支

- 人类：`feature/xxx` 或 `fix/xxx`
- Cloud Agent：`cursor/<short-description>-dea4`

## 标题

简短祈使句，例如：

- `fix(cli): 修复 productivity 会话恢复`
- `docs: 更新 README 多语言与 AI 开工提示`

## 正文模板

```markdown
## Summary
（为什么改、改什么）

## User-facing
（CLI / Android 用户能感知的变化；无则写 None）

## Test plan
- [ ] `./java_agent/gradlew -p java_agent :core:test :cli:test`
- [ ] `./scripts/ci-local.sh fast`（或 full）
- [ ] （可选）配置 DASHSCOPE_API_KEY 后 `./agent1 "…"`

## AI-assisted
- [ ] 已读 AGENTS.md 与 docs/ai/CHECKLIST.md
```

## 合并前

- GitHub Actions [ci.yml](../../.github/workflows/ci.yml) 全部绿色
- 审查者可按 Test plan 复现
