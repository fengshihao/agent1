# Dynamic UI Android v1

本目录是第一版最小可用实现：支持本地 JSON 渲染，也支持在 Android 端直接调用 Qwen 生成 UI JSON 并回传用户选择结果。

## v1 范围

- 基础组件：`text` `button` `column` `row` `image`
- 基础样式：`padding` `backgroundColor` `textColor` `fontSize` `fontWeight`
- 基础事件：`navigate(route, params)`
- 数据源：`app/src/main/assets/ui/*.json`
- LLM 生成：`Qwen3.5-Flash`（DashScope OpenAI 兼容接口）
- 系统提示词：`app/src/main/assets/prompts/*.txt`（可直接修改提示词策略）

## 目录说明

- 应用 id / 包名：`com.agent1.android`（源码根目录 `app/src/main/java/com/agent1/android/`）
- `app/src/main/assets/ui`：本地 JSON 示例
- `app/src/test`：解析层单元测试
- 静态质量门禁：仓库根 `./check-agent1-quality.sh`（Java PMD/SpotBugs + Android 分层 + 主线程 Gateway）；仅 Android 见 `./check-android-agent-static.sh`
- 分层检查：仓库根执行 `./check-android-agent-layering.sh`，或 `python android_agent/scripts/check_android_layering.py`（默认扫描本模块 `app/src/main/java`）

## 快速验证

### 命令行一键编译、安装、启动（需 adb 已连上设备）

在终端进入本目录后执行：

```bash
chmod +x run.sh   # 首次可选
./run.sh
```

等价于依次执行 `./gradlew :app:assembleDebug`、`adb install -r app/build/outputs/apk/debug/agent1-android-debug.apk`、启动 `com.agent1.android` 的主界面。

### 覆盖安装与 versionCode（含 GitHub Actions CI 包）

Android **只认 `versionCode` 数字**（不是 APK 文件名）。从 **CI Artifacts** 下载 `agent1-android-debug.apk` 安装时，推荐：

```bash
adb install -r -d agent1-android-debug.apk
```

`-r` 覆盖同签名包；`-d` 允许在误装过**更高** versionCode 的旧 CI 包后，再装一次稍低的 artifact（例如重跑旧 workflow）。

**CI 版本号**：GitHub Actions 构建时使用 **`versionCode = 100000 + github.run_number`**（每次 workflow 单调递增）。本地 Gradle 仍用 `10000 + git 提交数`，除非你设 `VERSION_CODE`。

**CI 签名**：Debug 包统一用仓库内 **`android_agent/debug.keystore`** 签名，不同 CI 任务产物可互相覆盖。若你曾在本机用 Android Studio 默认 debug 签名装过同包名，或装过旧 id `com.dynamicui.demo`，需**卸载一次**后再装 CI 包。

仍无法覆盖时：设置里卸载 `com.agent1.android`，或临时打更大版本：`VERSION_CODE=200000 ./gradlew :app:assembleDebug`。

### 真机连通测试（Compose 冒烟，不调用 LLM）

已连接 `adb devices` 为 `device` 时：

```bash
chmod +x run-connected-tests.sh   # 首次可选
./run-connected-tests.sh
```

会先发布 `java-agent-core`，再在设备上运行 `MainActivitySmokeTest`（断言「本地样例」Tab 可见）。

### Android Studio

1. 在 Android Studio 打开 `android_agent` 目录
2. 同步 Gradle 后运行 `app`
3. 在顶部 Tab 切换：
   - `本地样例`：切换本地 JSON
   - `Qwen 生成`：输入需求后生成动态 UI
4. 在 `Qwen 生成` 页填写表单后点击 `提交用户选择`，查看模型总结

## Qwen 配置

在运行前设置 API Key（二选一）：

- 方式 1：环境变量
  - `DASHSCOPE_API_KEY`
  - 可选 `DASHSCOPE_BASE_URL`（默认 `https://dashscope.aliyuncs.com/compatible-mode/v1`）
- 方式 2：Gradle 属性（推荐本地开发）
  - 在 `~/.gradle/gradle.properties` 或项目 `gradle.properties` 中加入：

```properties
DASHSCOPE_API_KEY=your_key_here
DASHSCOPE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
```

`app/build.gradle.kts` 会把这两个值注入到 `BuildConfig`（可选，便于开发机打包）。

**推荐**：安装 APK 后在 App 内打开 **「模型」→ 模型配置**，填写 API Key、Base URL，点 **从网络拉取模型** 选择模型并保存。配置加密存在本机，无需把 Key 打进 APK。

## 生产力助手 · Agent 工具（Weizhi / WebView 可选）

聊天助手默认装配 **工作区读写**、`chat_history` 和 **`read_url`**（公开网页正文）：`read_file` / `write_file` / `edit_file` / `list_dir` / `chat_history` / `read_url`（见 `ProductivityAgentHost`）。

**脚本、MCP、WebView** 依赖联编 Weizhi。`grep` / `glob` / `zip` / `bash` / `load_skill_through_path` 的实现在 `java-agent-core`，由 `WeizhiAgentTools` 在 `BuildConfig.WEIZHI_INTEGRATED=true` 时注册。未联编时 APK 只有工作区读写等内核工具。

**推荐：源码联合编译**（与 CI 默认一致，`weizhi` 已公开）：

```text
方式 A（推荐，与 CI 一致）          方式 B（传统同级目录）
agent1/                            parent/
  weizhi/   ← clone 你的 weizhi       agent1/
  android_agent/                     weizhi/
                                       android/
                                       agent1/
```

```bash
./sync-weizhi.sh    # 默认 https://github.com/fengshihao/weizhi.git；fork 可设 WEIZHI_GIT_URL
cd android_agent && ./gradlew :app:assembleDebug
```

CI 默认执行 **`sync-weizhi.sh`** 再 `assembleDebug`，APK 应含完整 Weizhi 工具。若 fork 不想拉 weizhi，在仓库 Variables 设 **`WEIZHI_SKIP_SYNC=true`**。

**备选 · Maven 预编译**（无源码、或 native 环境受限）：见 [`weizhi-prebuilt/README.md`](weizhi-prebuilt/README.md)，并设 Variable **`WEIZHI_USE_PREBUILT=true`** + Secret **`WEIZHI_PREBUILT_URL`**。

App 内 **模型配置** 与聊天页 **模型详情** 会显示当前包装配的「Agent 工具」摘要。

**WebView 绘图（canvas，非大模型生图）**

- 自动化（adb 真机/模拟器，不调 LLM）：`./run-webview-draw-test.sh`
- 手动让 Agent 走 `webview_exec`：见 [`doc/webview-draw-e2e.md`](doc/webview-draw-e2e.md)（直接在对话里说明任务，无内置 canvas skill）

桌面 Java 生产力模式：`java -jar … --productivity`（需 `../weizhi` 才有 Weizhi 脚本环）；普通 `JavaAgentCli` 仍是 read/bash/python/skill 四套老工具。

## JSON 示例（按钮导航）

```json
{
  "version": "1.0",
  "root": {
    "type": "button",
    "text": "打开详情页",
    "action": {
      "type": "navigate",
      "route": "detail",
      "params": {
        "id": "42"
      }
    }
  }
}
```

## 后续扩展建议

- 增加布局属性：`spacing` `alignment` `weight`
- 增加 Schema 校验与版本迁移策略
- 把 `onNavigate` 对接到正式 `NavController`
- 增加敏感信息保护（正式环境建议走服务端代理，避免 API Key 下发到客户端）

### 未捕获崩溃日志（App 内 + adb）

- **下次启动**：若存在上次 Java 崩溃/启动失败记录，会先进入**纯 View 崩溃页**（可复制、清除、仍要进入）。
- **落盘**：`files/last_crash_report.txt` + `files/crash-reports/crash-*.txt`；Debug 包还会 Toast 提示已写入。完整目录树见 [`doc/runtime-data-layout.md`](doc/runtime-data-layout.md)。
- **电脑拉取**（推荐）：

```bash
cd android_agent && ./pull-crash-report.sh
# 或手动：
adb exec-out run-as com.agent1.android cat files/last_crash_report.txt
```

若为 **JNI/native 闪退**（无 Java 栈），请同时：`adb logcat -d | tail -300` 搜索 `FATAL` / `DEBUG`.

若需紧急退出应用（需 adb 已连接设备）：

```bash
./stop-app.sh
# 等价：adb shell am force-stop com.agent1.android
```
