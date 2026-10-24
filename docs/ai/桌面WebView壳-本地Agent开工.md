# 桌面 WebView 壳 — 本地 Agent 开工说明

> 给 **本机 macOS（含老旧机型）** 上的 Cursor / Agent 用。Cloud Agent /Linux 请只做 Java、`web_ui`、文档，或等 PR 在 `macos-latest` 出 artifact。

## 必读（按顺序）

1. [AGENTS.md](../../AGENTS.md) · [docs/ai/START.md](./START.md) · [docs/ai/CHECKLIST.md](./CHECKLIST.md)
2. [doc/规划/桌面WebView壳与原生桥-计划.md](../../doc/规划/桌面WebView壳与原生桥-计划.md) — 总纲（**保留 UiHttpServer**，原生桥 `agent1Host`）
3. [doc/规划/桌面WebView壳-Mac实施计划.md](../../doc/规划/桌面WebView壳-Mac实施计划.md) — **M1～M8 分步**、PR 顺序、**§0.3 老旧 Mac**
4. 关联：[CLI-Web-UI-MVP.md](../../doc/规划/CLI-Web-UI-MVP.md)、[桌面UI发行包.md](../../doc/规划/桌面UI发行包.md)、Android HTML 审查 [18-内置HTML预览与点选审查.md](../../doc/规划/自进化Agent/18-内置HTML预览与点选审查.md)

规划文档分支（仅文档时可合 master）：`cursor/desktop-webview-shell-plan-927a` / PR #100。

## 实现分支命名

从 **master** 拉实现分支，例如：

- `feature/desktop-shell-m1-macos` 或 Cloud 约定：`cursor/desktop-shell-m1-macos-927a`

**一条 PR 一个里程碑**（见 Mac 实施计划 §12）：

| PR | 内容 |
|----|------|
| PR-1 | M1 `desktop_shell` + M2 `--no-browser` + 单测 |
| PR-2 | M3 `agent1-ui-window` |
| PR-3 | M4 打包 + `macos-latest` CI artifact |
| PR-4+ | M5～M8 原生桥 |

## 本机先跑环境自检

```bash
cd /path/to/agent1
sw_vers && uname -m
java -version   # 需要 17+
./agent1 ui     # 现有 MVP：应能开浏览器聊天（可选，需 API Key 才发消息）
```

若计划本地编壳，再执行 Mac 实施计划 **§0.3** 与 `xcode-select --install`、`rustup`。

## 复制给本地 Agent 的提示词（整段粘贴）

```text
你在 agent1 仓库做「桌面 WebView 薄壳」Mac 方向实现，请严格按文档执行。

【先读】
- AGENTS.md、docs/ai/START.md、docs/ai/CHECKLIST.md
- doc/规划/桌面WebView壳与原生桥-计划.md
- doc/规划/桌面WebView壳-Mac实施计划.md（含 §0.3 老旧 Mac、M1～M8、PR 顺序）
- docs/ai/桌面WebView壳-本地Agent开工.md（本文件）

【架构约束】
- 不删除、不替代 UiHttpServer；聊天仍用 fetch + EventSource(/api/events)。
- 壳只加载 http://127.0.0.1:<port>/；原生能力通过 window.agent1Host.invoke（M5 起），与 HTTP 并存。
- 一条 PR 只做当前里程碑；Java 改动须有 :cli:test / 相关单测。

【本机环境】
请先运行并汇报：sw_vers、uname -m、java -version、内存；对照 Mac 实施计划 §0.3 判断：
- 能否本地 cargo 编 desktop_shell；若不能，说明用 GitHub Actions darwin zip artifact 验收壳。
- 无论能否编壳，都应能跑 ./agent1 ui（浏览器）或实现后的 ui --no-browser。

【当前任务（由人类指定其一）】
- 若尚未实现：从 PR-1 开始 — M1 新建 desktop_shell/（wry+tao，单窗加载 URL）+ M2 ProductivityUiCommand --no-browser（及 AGENT1_UI_NO_BROWSER）+ 单测。
- 若 PR-1 已完成：按 Mac 实施计划继续下一里程碑（M3 agent1-ui-window 等）。

【验证】
- ./java_agent/gradlew --no-daemon -p java_agent :cli:test（凡改 Java）
- Mac 上：cargo build --release（凡改 desktop_shell）；手动关窗、探活 /api/health
- PR 正文含 Summary / User-facing / Test plan；读过 CHECKLIST

【不要】
- 不要砍掉 HTTP 改 file:// 主路径；不要做 Electron/JCEF；不要在本 PR 顺带做 Windows（除非人类明确要求 M4w）。
```

人类只需在提示词末尾把 **「当前任务」** 改成例如「只做 M2」或「从 PR-1 全流程做到 M3」。

## 变更记录

| 日期 | 说明 |
|------|------|
| 2026-10-21 | 本地 Agent 必读列表 + 可复制提示词 |
