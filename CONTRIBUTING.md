# 贡献指南

感谢你愿意参与 `agent1` 的建设。

## 开发环境

- **JDK 17**（使用 `JAVA_HOME` 或 `~/.gradle/gradle.properties` 中的 `org.gradle.java.home`，勿在仓库内提交本机路径）
- 可选：**Android SDK**（仅改 `android_agent` 时需要）

```bash
./java_agent/gradlew -p java_agent :core:test :cli:test
```

## 代码规范

- 保持改动最小且聚焦
- 新增行为要同步更新 README / docs
- 工具相关改动需考虑跨平台（macOS / Ubuntu / Windows）
- 涉及模型调用链路的改动，需确认 JSONL 日志字段不回退

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
- `./run-java-agent-gradle --no-stream "1+1等于几"`（需配置 `DASHSCOPE_API_KEY` 或 `OPENAI_API_KEY`）
- 检查 `logs/agent1.jsonl` 是否有 `run_started` / `model_response` / `usage` / `run_completed`

## Issue 建议信息

- 环境信息（OS、JDK 版本、Shell）
- 复现步骤
- 预期行为与实际行为
- 关键日志片段（可脱敏）
