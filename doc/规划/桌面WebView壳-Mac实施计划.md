# 桌面 WebView 壳 — macOS 实施计划（分步）

> **状态**：可执行路线图（先 Mac，后 Windows）  
> **总纲**：[桌面WebView壳与原生桥-计划.md](./桌面WebView壳与原生桥-计划.md)  
> **原则**：保留 `UiHttpServer`；壳 + `agent1Host` 原生桥；**一条 PR 一个里程碑**（见文末）

---

## 0. 开发环境：Mac 本地 vs 云端

| 环境 | 能做什么 | 不能做什么 |
|------|----------|------------|
| **你的 Mac（推荐主战场）** | 编/跑 `agent1-shell`（WKWebView）、肉眼验收窗口、调试桥 | — |
| **Cursor Cloud Agent（本仓库默认）** | Java `--no-browser`、`UiHttpServer` 单测、`web_ui` 构建、Linux 上写 Rust **源码**（语法检查） | **不能**在 Linux 上链接/运行 **macOS WKWebView** 二进制；无 GUI |
| **GitHub Actions `macos-latest`** | `cargo build` 出 Mac 壳、`uiFatJar`（`macosx-universal`）、打 zip、**无头**跑 Java 测 | 一般**不跑**需要点窗口的 UI 自动化（除非另接 macOS UI 测试）；适合 **编译 + artifact** |
| **Windows 开发机（你暂无）** | 将来 WebView2 壳 | 可等 Mac 里程碑 M4 后再开 Win 矩阵 job |

**结论**：

- **Mac 壳的开发与体验验收必须在 macOS 上完成**（本机或借 Mac mini / CI 下载 artifact 后本机双击）。
- **云端可以继续做**：Java 侧 `--no-browser`、脚本、`web_ui` 的 `agent1Host` 前端分支、文档、以及 **push 后让 `macos-latest` 编出 `agent1-shell`**。
- **没有「云端 Mac 桌面」** 替代本机点窗口；若只有 Linux，用 **Actions 产物 + 你稍后在有 Mac 时验收**。

### 0.1 Mac 本机前置（一次性）

```bash
# Xcode 命令行工具（WKWebView 链需要）
xcode-select --install

# Rust（壳工程）
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
rustc --version

# 仓库已有
java -version   # 17+
node -v         # 仅改 web_ui 时需要
```

可选：`brew install curl`（一般已有）。**不需要**单独装 Chromium。

### 0.2 日常联调命令（开发态，未打包 zip）

```bash
# 终端 A：起 Java UI（实现 --no-browser 后）
./agent1 ui --no-browser
# 或 AGENT1_UI_NO_BROWSER=1 ./agent1 ui

# 终端 B：起壳（里程碑 M1 之后）
desktop_shell/target/release/agent1-shell http://127.0.0.1:17831
```

开发树可用 **`AGENT1_SHELL_BIN`** 覆盖壳可执行文件路径（见 M3 `agent1-ui-window`）。

### 0.3 老旧 Mac：能否开发 / 验收？

先在本机执行（约 30 秒），把结果留给后续 Agent 或自己对照：

```bash
sw_vers
uname -m                   # arm64 = Apple 芯片；x86_64 = Intel
java -version 2>&1 | head -1
sysctl -n hw.memsize | awk '{print $1/1024/1024/1024 " GB RAM"}'
xcode-select -p 2>/dev/null || echo "未安装 Xcode CLI"
```

| 能力 | 一般要求 | 老 Mac 说明 |
|------|----------|-------------|
| **`./agent1 ui`（系统浏览器）** | Java **17+** + 可用 Safari/Chrome | 能跑现代浏览器即可，**与机器新旧关系不大** |
| **仅 Java：`--no-browser` + REST/SSE** | Java 17+ | **不依赖 WKWebView**；老 Mac、Linux、Cloud 均可 |
| **本地编/跑 `agent1-shell`（wry + WKWebView）** | 通常 **macOS 10.15+**（以 `desktop_shell` README / wry 版本为准）；**Xcode 命令行工具** + **Rust** | **10.14 及以下** 常无法装新工具链，**不宜本地编壳** |
| **联调 JVM + 壳 + 聊天** | 建议 **≥ 8 GB RAM**（16 GB 更舒适） | 4 GB 易卡；可只用浏览器模式减轻负担 |

**机型粗分（非硬性，以 `cargo build` 为准）**

- **Intel 老机（约 2015～2019）**：若最高系统为 **macOS 12 Monterey** 或 **13 Ventura**，多数可完成 M1～M4 本机开发与验收。
- **最高仅 Catalina（10.15）**：优先试 `rustup` + `cargo build`；失败则 **不要强升硬件**，改用 CI 产物。
- **10.13 及更早**：建议 **不本地编壳**；本机用 **`./agent1 ui` 浏览器** + 从 GitHub Actions 下载 **darwin zip** 试 `bin/agent1-ui-window`（仍需 Java 17+）。

**老 Mac 推荐分工**

1. **本机**：Java / `web_ui` / `./agent1 ui`（浏览器）——与壳内 WebView 加载同一 URL，功能对齐。
2. **壳二进制**：由 **GitHub Actions `macos-latest`** 构建（M4）；本机只解压验收窗口。
3. **实现 PR**：Cloud Agent 或任意 OS 可写 Java、脚本、文档；**合并前**需 Mac 或 CI artifact 做一次壳冒烟。

**快速试探能否本地编壳**（`desktop_shell` 落地后）：

```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
cd desktop_shell && cargo build --release
```

成功 → 可走全文 Mac 路线图；失败且报 OS/SDK 过旧 → 采用 **浏览器 + CI 壳** 组合。

本地 Agent 开工说明与复制用提示词见：[docs/ai/桌面WebView壳-本地Agent开工.md](../../docs/ai/桌面WebView壳-本地Agent开工.md)。

---

## 1. 里程碑总览（Mac 优先）

| ID | 名称 | 产出 | 建议 PR |
|----|------|------|---------|
| **M1** | Rust 空壳 | 单窗口加载 URL，关窗退出 | `feature/desktop-shell-m1-macos` |
| **M2** | Java 不打开浏览器 | `--no-browser` + 单测 | 可与 M1 同 PR 或紧跟 |
| **M3** | `agent1-ui-window` 编排 | 探活 `/api/health`、启停 JVM | 脚本 + README |
| **M4** | 打进桌面 zip | `package-desktop-ui.sh` 拷 `agent1-shell` | 发行 |
| **M5** | `agent1Host` 最小桥 | `getCapabilities` + `invoke` 骨架 | 壳 + 少量 `web_ui` |
| **M6** | `openWindow` | 第二窗口加载预览 URL | 桥 |
| **M7** | `pickFiles` | 系统选文件 → 现有 attachment API | 桥 + `web_ui` |
| **M8** | HTML 审查注入 | 预览窗注入 `HtmlInspectScript` | 对齐 Android |

Windows：在 **M4 稳定后** 复制 `desktop_shell` 目标为 `x86_64-pc-windows-msvc`，加 `windows-latest` job（**M4w**），不阻塞 Mac。

---

## 2. M1 — Rust 薄壳（macOS 可运行）

### 2.1 目标

- 仓库新增 `desktop_shell/`（Cargo，`wry` + `tao`）。
- 接受 CLI 参数：URL（默认 `http://127.0.0.1:17831`）。
- 一个窗口、标题 `Agent1`、合理默认大小；用户关窗 → 进程 exit 0。

### 2.2 步骤

1. **建工程**
   - `desktop_shell/Cargo.toml`：`wry`、`tao`（版本以 [wry releases](https://github.com/tauri-apps/wry) 为准，选稳定版）。
   - `desktop_shell/src/main.rs`：EventLoop + Window + `WebViewBuilder::with_url`。
   - `desktop_shell/README.md`：仅 Mac 构建说明。

2. **本地构建**
   ```bash
   cd desktop_shell
   cargo build --release
   # 产物：target/release/agent1-shell（二进制名在 Cargo.toml `[[bin]] name` 定）
   ```

3. **手动验收**（必须先 `./agent1 ui` 或任意方式让 17831 有 SPA）
   - 运行壳，应看到与浏览器相同的聊天 UI。
   - 关窗后进程结束。

4. **仓库 hygiene**
   - 根目录或 `desktop_shell/.gitignore`：`target/`。
   - **不提交** `target/` 二进制；CI / 本机 release 构建。

### 2.3 完成标准

- [ ] Mac 上 `cargo build --release` 无警告（或仅允许 documented allow）。
- [ ] 加载 `http://127.0.0.1:17831` 可聊天（与浏览器一致）。
- [ ] 文档写明：Linux 上 **不能** `cargo build` 出 Mac 二进制（需 `macos` target + SDK）。

---

## 3. M2 — Java：`--no-browser`

### 3.1 目标

`ProductivityUiCommand` 在 `--no-browser` 或 `AGENT1_UI_NO_BROWSER=1` 时：**不**调用 `openBrowser()`，仍阻塞等待 shutdown。

### 3.2 步骤

1. **解析参数**  
   - 在 `resolvePort` 同级增加 `resolveNoBrowser(args)`；环境变量优先或与 CLI 一致（与项目其它 env 风格对齐）。

2. **`DesktopUiLauncher` / `ui` 子命令**  
   - 确保 `java -jar … ui --no-browser` 传到 `ProductivityUiCommand.run`。

3. **单测**  
   - `ProductivityUiCommandTest` 或扩展现有 UI 测试：mock 不测 Desktop，至少测参数解析。
   - 已有 `UiHttpServerTest` 不变。

4. **文档**  
   - `CLI-Web-UI-MVP.md` 增加一行「壳模式：`--no-browser`」。

### 3.3 完成标准

- [ ] `./java_agent/gradlew -p java_agent :cli:test` 通过。
- [ ] Mac 上：`./agent1 ui --no-browser` 只打印 URL，不弹 Safari。

---

## 4. M3 — `bin/agent1-ui-window`（Mac 编排脚本）

### 4.1 目标

解压版或开发树中 **一条命令**：后台 JVM + 前台壳 + 关壳杀 JVM。

### 4.2 步骤

1. 新增 `scripts/desktop-ui-launcher/agent1-ui-window`（Bash）：
   - 复用 `agent1-ui` 的 `find_java_bin`、jar 发现逻辑（`source java-install-hint.sh`）。
   - `PORT` / `AGENT1_UI_PORT` / `--port` 与 Java 一致（可只支持 env + 默认 17831，端口透传后续再加）。
   - 后台：`"$JAVA" -jar "$JAR" ui --no-browser --port "$PORT"`，记 `JAVA_PID`。
   - 循环：`curl -sf "http://127.0.0.1:$PORT/api/health"`，超时 30s 失败则 kill Java 并 exit 1。
   - 前台：`exec "$SHELL_BIN" "http://127.0.0.1:$PORT/"`  
     - `SHELL_BIN`：开发时指向 `desktop_shell/target/release/agent1-shell`；打包后 `$ROOT/bin/agent1-shell`。
   - `trap`：EXIT 时 `kill $JAVA_PID`。

2. **开发树约定**  
   - 环境变量 `AGENT1_SHELL_BIN` 覆盖壳路径，便于未打包时联调。

3. **README**（`desktop_shell/README.md` + 桌面发行包 doc 一句）。

### 4.3 完成标准

- [ ] Mac：`./scripts/desktop-ui-launcher/agent1-ui-window`（或拷到 staging 的相对路径）能完整走通。
- [ ] 关窗后 `17831` 不再监听（JVM 已退）。

---

## 5. M4 — 纳入 `package-desktop-ui.sh` + GitHub Actions（Mac）

### 5.1 目标

`dist/agent1-desktop-ui-darwin-universal.zip` 内含 `bin/agent1-shell`、`bin/agent1-ui-window`。

### 5.2 步骤

1. **本地 / CI 在 macOS 上**：
   ```bash
   cd desktop_shell && cargo build --release
   cp target/release/agent1-shell ../scripts/desktop-ui-launcher/  # 或 dist staging 直拷
   ```

2. **改 `package-desktop-ui.sh`**：
   - `darwin` 分支：`chmod +x bin/agent1-shell`，拷贝 `agent1-ui-window`。
   - README 增加「窗口模式：`./bin/agent1-ui-window`」。

3. **CI（建议新 job 或扩展现有 workflow）**：
   ```yaml
   runs-on: macos-latest
   steps:
     - uses: actions/checkout@v4
     - uses: dtolnay/rust-toolchain@stable
     - run: cd desktop_shell && cargo build --release
     - run: ./scripts/package-desktop-ui.sh darwin-universal
     - uses: actions/upload-artifact@v4
       with:
         name: agent1-desktop-ui-darwin-universal
         path: dist/agent1-desktop-ui-darwin-universal.zip
   ```

4. **无 Mac 时的协作**：push 分支 → 从 Actions 下载 zip → 本机解压验收。

### 5.3 完成标准

- [ ] zip 在 Mac 解压后 `bin/agent1-ui-window` 可用（仅需用户 Java 17+）。
- [ ] artifact 体积较原 zip **+约 3～10 MB**。

---

## 6. M5 — `agent1Host` 最小桥（Mac WKWebView）

### 6.1 目标

页面加载前注入：

```javascript
window.agent1Host = {
  platform: "macos",
  version: "0.1.0",
  invoke(method, params) { /* Promise */ }
};
```

首批方法（实现可 stub）：

| method | 行为 |
|--------|------|
| `getCapabilities` | 返回 `{ multiWindow, pickFiles, inspect, … }` |
| `ping` | `{ ok: true }` 连通性 |

### 6.2 步骤（壳侧，Rust）

1. 使用 wry 的 **IPC / custom protocol / evaluate on load**（以当前 wry 文档为准，常见为 `with_ipc_handler` 或初始化脚本注入 + `with_navigation_handler`）。
2. **消息格式**（建议 JSON-RPC 2.0 风格，便于以后 Java 复用）：
   ```json
   { "id": "1", "method": "ping", "params": {} }
   ```
   响应：
   ```json
   { "id": "1", "result": { "ok": true } }
   ```
3. **安全**：仅 `http://127.0.0.1:*` 页面可调用（检查 webview URL）。

### 6.3 步骤（web_ui）

1. `src/agent1Host.ts`：`invoke` 封装；无桥时 no-op。
2. 设置页或 About 显示 `platform`（仅当有桥）。
3. **不改**现有 `fetch` / SSE。

### 6.4 完成标准

- [ ] Mac 壳内打开控制台或 About 可验证 `ping`。
- [ ] Safari 打开同一 URL 无 `agent1Host`，页面不报错。

---

## 7. M6 — `openWindow`（第二窗口，Mac）

### 7.1 目标

`invoke("openWindow", { url, title, kind })` 新开 WKWebView 窗口；`url` 限于同源 `http://127.0.0.1:port/...`。

### 7.2 步骤

1. 壳内维护 `window_id` 表；tao 多 `Window` + 每窗一个 WebView。
2. `kind: "html-preview"` 时可选：导航拦截，禁止离开 127.0.0.1。
3. `web_ui`：打开 workspace HTML 附件时，有桥则 `openWindow`，无桥则 `window.open` 或新标签（浏览器回退）。

### 7.3 完成标准

- [ ] 聊天主窗不变；预览窗打开 `.../files/xxx.html`。
- [ ] 关预览窗不影响主窗与 JVM。

---

## 8. M7 — `pickFiles`（Mac NSOpenPanel）

### 8.1 目标

系统文件对话框 → 路径列表回传页面 → 页面 `POST` 现有 `/api/sessions/.../attachments`（**上传逻辑仍在 HTTP**）。

### 8.2 步骤

1. Rust：`rfd` 或 `wry` 生态的文件对话框 crate（仅 macOS 先实现）。
2. 桥返回 `{ paths: ["/Users/..."] }`；页面读文件用 FormData 上传（与现浏览器选文件一致）。
3. JVM 侧路径校验规则不变。

### 8.3 完成标准

- [ ] 选图/ PDF 后出现在附件列表。
- [ ] 拒绝工作区外路径（若 API 已校验，单测覆盖）。

---

## 9. M8 — HTML 点选审查（预览窗）

### 9.1 目标

预览窗 `document start` 注入与 Android 同源的 `HtmlInspectScript`（常量可抽到 `agent_core` resources 或 `desktop_shell/assets/inspect.js` 从 Android 同步）。

### 9.2 步骤

1. 复制/共享 `HtmlInspectScript` 文本（避免 drift，优先单源）。
2. 桥方法 `onInspectResult` 或由 JS 调 `agent1Host.invoke("inspectResult", payload)`。
3. 主窗聊天输入框接收草稿（`postMessage` 跨窗或 HTTP 小端点 `POST /api/ui/draft` — **优先简单方案**：壳把结果发给主 WebView 的 `evaluate_script`）。

### 9.3 完成标准

- [ ] 点选元素后输入框出现带 selector 的草稿（不自动发送）。
- [ ] JVM 单测：`HtmlInspectParser` 仍适用。

---

## 10. 风险与对策（Mac）

| 风险 | 对策 |
|------|------|
| wry API 变更 | 锁版本；README 记最低 Rust |
| WebView 与 Safari 行为差 | 以壳内为准验收；关键 CSS/JS 冒烟 |
| 双进程调试麻烦 | `AGENT1_SHELL_BIN` + 双终端流程写进 README |
| 无 Mac 开发者 | 依赖 Actions artifact；M5+ 前端可在 Linux 写，壳合并前 Mac 验 |

---

## 11. Windows 后续（M4w，简表）

1. `desktop_shell` 增加 `windows` target 说明；`webview2` 由 wry 拉取。
2. `agent1-ui-window.cmd` / `.ps1`：同 Bash 逻辑（`curl` / `Invoke-WebRequest` 探活）。
3. `windows-latest` CI 编 exe，打入 `agent1-desktop-ui-windows-x86_64.zip`。
4. README：WebView2 Runtime 缺失时的安装链接。

**不需要**在 Windows 上先完成 M1～M8 才能合 Mac；Mac 分支可先 merge。

---

## 12. 建议 PR 顺序（你「先开始 Mac」）

1. **PR-1（M1+M2）**：`desktop_shell` 最小可运行 + `--no-browser` + 测试  
2. **PR-2（M3）**：`agent1-ui-window` 脚本  
3. **PR-3（M4）**：打包脚本 + `macos-latest` CI artifact  
4. **PR-4（M5）**：`agent1Host` ping / capabilities  
5. **PR-5（M6～M8）**：可再拆 3 个 PR  

当前规划分支 `cursor/desktop-webview-shell-plan-927a` 仅文档；**实现请从 `master` 拉 `feature/desktop-shell-m1-macos`（或 `cursor/desktop-shell-m1-macos-927a`）按 PR-1 开工**。

---

## 13. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-10-21 | Mac 分步实施计划初稿 |
| 2026-10-21 | §0.3 老旧 Mac；链到 docs/ai/桌面WebView壳-本地Agent开工.md |
