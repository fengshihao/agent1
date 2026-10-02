# 提交前清单（与 CI 同源）

AI 与人类在开 PR 前逐项确认。

## 范围

- [ ] **一件事一个 PR**
- [ ] 改动落在 [AGENTS.md](../../AGENTS.md) 允许路径内
- [ ] 未恢复或扩展 `python_agent/`

## 测试（强制）

- [ ] **Java**：`./java_agent/gradlew --no-daemon -p java_agent :core:test :cli:test` 通过
- [ ] **新增/修改 Java 行为** 已附带或更新 JUnit 单测（非仅改注释/文档）
- [ ] **Android 解析 / ViewModel / 业务逻辑**：已更新 `android_agent` 下对应 `src/test`（若该层已有测试惯例）
- [ ] **PR 门禁**：`./scripts/ci-local.sh fast` 通过
- [ ] 动 **Android assemble**、Gradle 依赖或原生桥时：`./scripts/ci-local.sh full` 通过（或 PR 中说明仅依赖 GitHub `android-assemble-debug` 且已绿）

## 静态质量

- [ ] `./check-agent1-quality.sh` 或 CI `quality-static` 等价步骤通过
- [ ] Android 改动符合 [`.cursor/rules/android-layering.mdc`](../../.cursor/rules/android-layering.mdc)

## 安全与仓库卫生

- [ ] 无 API Key / token / 私钥 / `.env` 实值
- [ ] 无意外的大体积二进制（除非 PR 说明用途）

## PR 描述

- [ ] Summary（摘要）
- [ ] User-facing changes（用户可见变化，或写 None）
- [ ] Test plan（贴出命令与结果）
- [ ] 若 AI 辅助：勾选并写明读过 AGENTS.md / CHECKLIST

## 文档

- [ ] 行为变更已更新 README 或 `doc/` / `docs/` 相关篇
- [ ] 链接相对路径有效；与 AGENTS.md 冲突时 **先改 AGENTS**
