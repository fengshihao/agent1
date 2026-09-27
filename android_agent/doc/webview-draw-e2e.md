# WebView 绘图验收（非大模型生图）

## 自动化（推荐先做）

不消耗 API、不依赖 LLM：

```bash
./sync-weizhi.sh
cd android_agent && ./run-webview-draw-test.sh
```

验证 `webview_exec` 在真机 Chromium 中用 **canvas** 画出 PNG，并写入工作区 `webview_draw.png`（内容为 base64，解码后为 PNG）。

## 手动：让 Agent 用 WebView 画图

前提：APK 已 **Weizhi 集成**（`WEIZHI_INTEGRATED=true`），设置页 Agent 工具摘要含 WebView。

1. 安装：`./build-android-agent.sh`
2. 打开生产力助手，新建会话。
3. 发送（或加载 skill `webview-canvas-draw` 后发送）：

> 请**不要**用大模型生图。只用 **webview_exec** 在 canvas 上画 256×256 橙底蓝圆，把 PNG 的纯 base64 写到工作区 `draw/agent-webview.png`，完成后告诉我路径。

4. 预期：一轮或多轮 tool call 后出现 `webview_exec`，会话 workspace 下有 `draw/agent-webview.png`。
5. 拉取验证：

```bash
adb exec-out run-as com.dynamicui.demo cat files/agent1/sessions/<sessionId>/workspace/draw/agent-webview.png | head -c 80
# 应为 base64 字符；解码后 PNG 魔数 89 50 4E 47
```

## 与 CLI 差异

桌面 `./agent1` **无 WebView**；本验收仅 Android（或后续单独 headless 浏览器方案）。
