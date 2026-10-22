# desktop_shell（Agent1 桌面 WebView 薄壳）

M1 里程碑：基于 [wry](https://github.com/tauri-apps/wry) + [tao](https://github.com/tauri-apps/tao) 的单窗口壳，**只**加载本机 `UiHttpServer` 提供的 `http://127.0.0.1:<port>/`。聊天仍走页面内 `fetch` + `EventSource(/api/events)`，不替代 HTTP、不用 `file://` 主路径。

## 构建

```bash
cd desktop_shell
cargo build --release
# 产物: target/release/agent1-ui-window
```

## 运行（需先启动 Java UI 服务）

```bash
./agent1 ui --no-browser
# 另一终端
./desktop_shell/target/release/agent1-ui-window http://127.0.0.1:17831/
```

探活：`curl -s http://127.0.0.1:17831/api/health`

## 本机无 Rust 工具链

可在 GitHub Actions `desktop-shell-macos` 工作流产物中下载 `agent1-ui-window`（darwin）zip 验收。

## 后续里程碑

- M3：Java 侧自动拉起 `agent1-ui-window`（发行包内嵌二进制）
- M5+：`window.agent1Host.invoke` 原生桥（与 HTTP API 并存）
