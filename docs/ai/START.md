# 对 AI 说这句话就能开工

## 给人看的两步

1. **准备环境**：复制下面「准备环境」整段发给 Cursor、Claude Code、Codex、Windsurf 等编码智能体（不必自己先克隆）。智能体应自行克隆、读契约、安装 JDK 17、能跑 Gradle 单测。
2. **再说意图 + 验收**：环境就绪后说明**一件事**要改什么；要求 `./scripts/ci-local.sh fast`（或 PR 说明里写明的子集）通过后再开 PR。

## 复制给 AI（准备环境）

```text
帮我准备开源项目 Agent1（https://github.com/fengshihao/agent1）的贡献环境：请你自己克隆仓库、读 AGENTS.md 和 docs/ai/START.md，安装 JDK 17 后运行 ./java_agent/gradlew -p java_agent :core:test :cli:test。准备好后告诉我，我再说想贡献什么。
```

## 环境就绪后对 AI 说（贡献一句）

```text
我要贡献：〈一件事〉。按 AGENTS.md 与 docs/ai/CHECKLIST.md 修改；新增或改动 Java 行为必须带 JUnit 单测；改完必须 ./scripts/ci-local.sh fast 通过，再按 docs/ai/PR_PLAYBOOK.md 开 PR。若改 Android 组装或大量 Android 代码，再跑 ./scripts/ci-local.sh full。
```

## 英文（给其他智能体）

**Bootstrap:**

```text
Set up contribution environment for Agent1 (https://github.com/fengshihao/agent1): clone the repo, read AGENTS.md and docs/ai/START.md, use JDK 17, run ./java_agent/gradlew -p java_agent :core:test :cli:test. Tell me when ready for my task.
```

**One task:**

```text
I want to contribute: 〈one scoped change〉. Follow AGENTS.md and docs/ai/CHECKLIST.md; add/update JUnit tests for Java changes; run ./scripts/ci-local.sh fast before opening a PR per docs/ai/PR_PLAYBOOK.md.
```

## AI 做完后人类怎么验

```bash
./java_agent/gradlew --no-daemon -p java_agent :core:test :cli:test
./scripts/ci-local.sh fast          # 动 Android 构建链时用 full
# 可选真机/模拟器：./build-android-agent.sh（Release：加 --release）
```

细则：[CHECKLIST.md](./CHECKLIST.md) · [PR_PLAYBOOK.md](./PR_PLAYBOOK.md)
