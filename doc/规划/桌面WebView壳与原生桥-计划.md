# 桌面 WebView 壳与原生桥 — 长期计划（仅文档）

> **状态**：规划稿 · **本议题先不实现**  
> **分支**：`cursor/desktop-webview-shell-plan-927a`（仅收录本文与交叉引用）  
> **关联**：[CLI-Web-UI-MVP.md](./CLI-Web-UI-MVP.md)、[桌面UI发行包.md](./桌面UI发行包.md)、[18-内置HTML预览与点选审查.md](./自进化Agent/18-内置HTML预览与点选审查.md)、[CLI与Android-Agent能力对照.md](../CLI与Android-Agent能力对照.md)

## 1. 背景与目标

### 1.1 现状

- 桌面聊天：`./agent1 ui` → JVM 内 `UiHttpServer`（`127.0.0.1:17831`）+ **系统浏览器** 打开 SPA（`java_agent/web_ui/` 打进 jar）。
- Android：Jetpack Compose 聊天壳 + 工作区 HTML **内置 WebView** + **点选审查**（`HtmlInspectScript` 等）。
- 规划上曾明确：主聊天壳 **不做** Electron/Tauri/Compose Desktop（见 CLI-Web-UI-MVP「非目标」）。

### 1.2 新方向（待实现）

| 目标 | 说明 |
|------|------|
| **嵌入式壳** | 各平台系统 WebView 薄壳（Win WebView2 / Mac WKWebView / Linux WebKitGTK），1～N 个简单窗口，不做复杂窗口管理 |
| **可定制桥** | 页面向壳请求原生能力：第二窗口、系统文件/文件夹选择、通知、深链、将来 HTML 审查回传等 |
| **与 Android 对齐** | 工作区 HTML 预览、点选元素 → 结构化反馈 → Agent 改源文件（复用审查 JS 与协议思路） |
| **发行** | 延续 **zip 解压即用**（可选 jlink）；**不要求** MSI/dmg；CI 矩阵分平台编 jar + shell |

### 1.3 非目标（本期规划不承诺）

- 替代 Android Compose 主聊天壳
- 捆绑 Chromium（JCEF/Electron 级）
- 完整 DevTools / Office 365 深度集成
- 在本计划落地前改动 `UiHttpServer` 默认行为或删除 `./agent1 ui` 浏览器路径

---

## 2. 核心架构问题：是否去掉 Java 本地 Web 服务器？

### 2.1 方案对比

| 方案 | 页面加载 | Agent / 会话 / 工具 | 前端改动量 |
|------|----------|---------------------|------------|
| **A. 维持 localhost HTTP（现状增强）** | `http://127.0.0.1:<port>/` | 现有 `UiHttpServer` → `DesktopUiGateway` → `ProductivityAgentHost` | **小**：加 `window.agent1Native` 桥；壳只多窗口/系统 API |
| **B. `file://` 本地静态 + 桥调 Java** | 解压或 jar 抽出 `index.html` | 全部经 WebView **注入桥**（无 REST/SSE） | **大**：`web_ui` 全面改写传输层；SSE 需桥事件推送 |
| **C. 自定义 URL 方案 `agent1://`** | 壳拦截 `agent1://app/*`、`agent1://workspace/*` | 静态走壳；API 可走 **同一 scheme 由 JVM 处理** 或仍走 loopback | **中～大**：实现 scheme + 安全边界；可渐进替代 B |
| **D. 无 HTTP，JVM 仅 IPC 服务** | 同 B/C | 独立进程/线程，壳与 Java 用 **stdin/socket/命名管道** JSON-RPC | **最大**：前后端协议重定 |

### 2.2 建议结论（评审用）

**短期与中期：保留 Java 侧 `UiHttpServer`（方案 A），壳上叠加原生桥（方案 A+）。**

理由（与当前代码强相关）：

1. **`web_ui` 已深度绑定 HTTP**：`fetch("/api/…")` 与 `EventSource("/api/events")`（见 `java_agent/web_ui/src/api.ts`），会话、附件、产物库、诊断导出、workspace 文件 URL 均为 REST 形态。
2. **`UiHttpServer` 已是成熟边界**：localhost 绑定、脱敏 config、SSE 事件、workspace 文件路由与单测（`UiHttpServerTest`）— 拆掉等于重写桌面网关。
3. **`file://` 的坑**：CORS、模块脚本、相对路径、`fetch` 读工作区、多窗口同源策略；Vite 产物需 `base: './'` 且仍难优雅暴露「按 session 读文件」。
4. **原生 API 与 HTTP 不互斥**：Electron/Tauri 常见模式是 **本地 server 或 custom protocol + `ipc`/postMessage**；壳定制 API **不需要**为了桥而砍掉 HTTP。

**长期可选（Phase 3+）**：若希望 **不占用端口、不暴露 loopback**，再评估：

- 仅把 **静态资源** 迁到 `agent1://app/`（壳或 JVM 提供字节流），**API 仍映射到** `agent1://api/...` 由 JVM 统一处理（等价于把 HttpServer 换成 scheme handler，**语义可保持 REST**），前端 URL 前缀配置化；或
- 保留 loopback 但只监听 **Unix domain socket / named pipe**（浏览器不适用，仅嵌入式 WebView 经壳转发）— 安全面更小，实现成本高。

**不推荐** 一步到位「纯 file:// + 无 HTTP」除非接受 **重写 `web_ui` 与全部桌面 API 契约**。

---

## 3. 目标架构（推荐演进路径）

### 3.1 Phase 0 — 文档与分支（当前）

- 本文档定方向、阶段、开放问题。
- **无产品代码变更**。

### 3.2 Phase 1 — 薄壳 + 现有 HTTP（MVP 壳）

```text
┌──────────────── agent1-ui-window ────────────────┐
│  JVM: UiHttpServer + DesktopUiGateway（不变）      │
│  Shell: WebView → http://127.0.0.1:17831         │
│  启动: 探活 /api/health → 显示窗口；关窗 → 停 JVM   │
└──────────────────────────────────────────────────┘
```

- Java：`--no-browser`（或 `AGENT1_UI_NO_BROWSER`）。
- 新二进制：`desktop_shell/`（建议 Rust `wry`+`tao`）→ 打入 `package-desktop-ui.sh` 产出 zip。
- 发行：GitHub Actions `macos-latest` / `windows-latest`（+ 可选 `ubuntu-latest`）矩阵；jar 可继续用 classifier 交叉打。

### 3.3 Phase 2 — 原生桥 `agent1Host`（壳定制 + 页面 API）

WebView 在页面加载前注入（或 `addScriptToEvaluateOnDocumentCreated`）：

```javascript
window.agent1Host = {
  platform: "windows" | "macos" | "linux",
  version: "…",
  invoke(method, params) { /* Promise，JSON-RPC 风格 */ }
};
```

**建议由壳转发到 JVM**（壳不执行业务逻辑）：

- 壳：WebView2 `WebMessageReceived` / WKWebView `messageHandlers`
- JVM：已有进程内 Gateway，可增加 **轻量本地 IPC**（同进程 JNI 若壳嵌 JVM 则直接调用；**推荐 sidecar 两进程 + loopback 或 stdio JSON 行**）仅处理「壳专属」方法；聊天仍走 HTTP。

| 方法（草案） | 执行层 | 说明 |
|--------------|--------|------|
| `openWindow({ url, title, kind })` | 壳 | `kind`: `chat` \| `html-preview` \| `web-app`；第二窗口加载 **同源 http URL** 或受控路径 |
| `closeWindow({ id })` | 壳 | |
| `pickFiles({ multiple, mimeTypes })` | 壳 → 回调路径 | 对齐 Android 附件选取；结果交 JVM 写入 workspace / 调现有 attachment API |
| `pickDirectory()` | 壳 | 可选，导入目录 |
| `showNotification({ title, body })` | 壳 | 可选 |
| `onInspectElement(callback)` | 壳 + 注入 JS | 预览页复用 `HtmlInspectScript` 语义；回调可 `POST /api/.../draft` 或桥发 `inspectResult` |
| `revealInFolder(path)` | 壳 | 打开系统文件管理器 |
| `getShellCapabilities()` | 壳 | 能力协商 |

**页面使用原则**：

- **会话 / Run / SSE / 配置**：继续 `fetch` + `EventSource`（不变）。
- **仅壳能做的**：`agent1Host.invoke(...)`，`web_ui` 内 `if (window.agent1Host)` 渐进增强。

### 3.4 Phase 3 — 多窗口 HTML 与审查闭环

- 聊天内点 HTML 附件 → `openWindow({ url: sessionFileUrl, kind: "html-preview" })`。
- 预览页：注入 `HtmlInspectScript`（与 Android 共享常量/资源）；选中元素 → 桥或 REST 回填输入框。
- `WorkspaceFileWatcher` 逻辑：桌面可由 JVM 监听 + SSE 通知前端 `fileChanged`（已有事件体系可扩展），预览窗 `location.reload()`。

### 3.5 Phase 4（可选）— 弱化 loopback

- 评估 `agent1://` 统一路由或 UDS；**仅当** 有明确安全/合规需求（禁止 localhost 监听）时启动。
- 需迁移清单：`api.ts` 基址、SSE 替代、E2E 测试。

---

## 4. 工程布局（实现时）

```text
desktop_shell/           # Rust wry（或分平台子目录）
  src/main.rs
  README.md

scripts/
  package-desktop-ui.sh  # 拷贝 agent1-shell + agent1-ui-window

java_agent/cli/
  ProductivityUiCommand  # --no-browser
  （可选）ShellBridgeServer — 仅壳用 IPC，非替代 UiHttpServer

doc/规划/
  本文档
```

---

## 5. 体积与构建（复习）

| 项 | 约增量 |
|----|--------|
| `agent1-shell` / 平台 | +3～10 MB / zip |
| ui jar | 基本不变 |
| jlink 可选整包 | ~38～45 MB（与壳无关） |
| WebView2 Runtime | 通常不打入包 |

**发版**：每平台一个 zip；**jar 可在一台机交叉 classifier**；**shell 需分 OS 构建（CI 矩阵）**，不要求开发人手一台 Mac/Win。

---

## 6. 安全

- HTTP 继续 **仅 127.0.0.1**；壳只加载受信 URL（禁止任意外链为 `openWindow` 默认）。
- `agent1Host.invoke`：**方法白名单**；路径参数经 JVM `SessionWorkspacePaths` 校验（与现有 attachment 一致）。
- 不在桥中暴露 API Key；config 仍经 `/api/config` 脱敏。

---

## 7. 开放问题（实现前需拍板）

1. **单进程 vs 双进程**：壳嵌 JVM（jpackage 一体）还是 zip 内 `agent1-ui-window` 脚本编排（先双进程）。
2. **Linux 桌面是否首发**：WebKitGTK 依赖与受众。
3. **桥 IPC 形态**：loopback 辅助端口 vs stdio vs UDS。
4. **是否与「去掉 HTTP」设硬性里程碑**：建议 **否**；Phase 4 单独立项。
5. **CLI-Web-UI-MVP「非目标」措辞**：壳落地后更新 MVP 文档，区分「不做 Electron」与「做系统 WebView 薄壳」。

---

## 8. 测试策略（实现阶段）

| 层级 | 内容 |
|------|------|
| JVM | 保持 `UiHttpServerTest`；新增 `--no-browser` 冒烟 |
| 壳 | 各平台 CI 编过 + 手动：探活、关窗退进程 |
| `web_ui` | 有 `agent1Host` 时 E2E（可选 Playwright 仍对 localhost） |
| 审查 | 复用 `HtmlInspectParser` / `HtmlInspectScript` JVM 单测；预览窗 instrumented 后补 |

---

## 9. 文档与里程碑检查表

- [ ] Phase 1：`desktop_shell` + `agent1-ui-window` + `--no-browser`
- [ ] Phase 2：`agent1Host` 能力与 `web_ui` 渐进接入
- [ ] Phase 3：HTML 预览窗 + 点选审查对齐 Android
- [ ] Phase 4（可选）：custom scheme / 无 loopback 评估报告
- [ ] 更新 [桌面UI发行包.md](./桌面UI发行包.md)、[CLI-Web-UI-MVP.md](./CLI-Web-UI-MVP.md) 的「非目标/入口」小节

---

## 10. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-10-21 | 初稿：长期计划分支，明确保留 HTTP + 原生桥；不做实现 |
