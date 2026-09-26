# 贡献指南

感谢你愿意参与 `agent1` 的建设。

## 开发环境

- **JDK 17**（Temurin 或等价发行版）
- **Gradle**：使用仓库内 wrapper（`gradle -p java_agent …`）
- Android 贡献需 Android SDK 与 adb（见 `android_agent/QUICKSTART.md`）

```bash
gradle -p java_agent :core:test :cli:test
```

## 代码规范

- 保持改动最小且聚焦
- 新增行为要同步更新 README / `doc/基础能力/` 对应篇
- 工具相关改动需考虑 **macOS 与 Ubuntu**（桌面官方支持平台）
- 涉及模型调用链路的改动，需确认 JSONL 事件字段不回退

## 提交流程

1. 新建分支：`feature/xxx` 或 `fix/xxx`
2. 完成开发并自测
3. 提交 PR，描述：
   - 背景与目标
   - 变更点
   - 验证方式
   - 风险与回滚方案

## 建议自测清单

- `gradle -p java_agent :core:test :cli:test`
- 配置 `DASHSCOPE_API_KEY` 后：`./run-java-agent-gradle --no-stream "1+1等于几"`
- 生产力路径：`gradle -p java_agent runJavaAgentCli --args="--productivity 你好"`（检查 `.agent1/` 与 `events.jsonl`）

## Issue 建议信息

- 环境信息（OS、JDK 版本、是否在 Android）
- 复现步骤
- 预期行为与实际行为
- 关键日志片段（可脱敏）
