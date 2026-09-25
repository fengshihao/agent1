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

- `app/src/main/java/com/dynamicui/demo/dynamicui/model`：UI DTO 与序列化
- `app/src/main/java/com/dynamicui/demo/dynamicui/core`：解析与校验
- `app/src/main/java/com/dynamicui/demo/dynamicui/ui`：Compose 渲染器
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

等价于依次执行 `./gradlew :app:assembleDebug`、`adb install -r app/build/outputs/apk/debug/app-debug.apk`、启动 `com.dynamicui.demo` 的主界面。

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

聊天助手默认装配 **工作区五件套**：`read_file` / `write_file` / `edit_file` / `list_dir` / `chat_history`（见 `ProductivityAgentHost`）。

**WebView、MCP、Weizhi 脚本与 grep/glob/zip/bash 等** 在代码里已写好（`app/src/weizhi/`、`WeizhiAgentTools`），但 **只有编译时存在 sibling 目录 `../weizhi/android` 才会打进 APK**（`BuildConfig.WEIZHI_INTEGRATED=true`）。GitHub Actions 上的 CI APK **通常不含 Weizhi**，所以模型侧只能看到上述基础工具。

本地完整集成：

```bash
# 与 agent1 同级 checkout weizhi 仓库，保证存在 weizhi/android/
cd android_agent && ./gradlew :app:assembleDebug
```

App 内 **模型配置** 与聊天页 **模型详情** 会显示当前包装配的「Agent 工具」摘要。

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

### 未捕获崩溃日志（adb）

`CrashReporter` 会写入应用私有目录：`files/last_crash_report.txt`，并在 `files/crash-reports/` 下留一份带时间戳的归档。Debug 包可用 `run-as` 读出（无需 root）：

```bash
adb exec-out run-as com.dynamicui.demo cat files/last_crash_report.txt
# 或列出归档
adb shell run-as com.dynamicui.demo ls files/crash-reports
```

若需紧急退出应用（需 adb 已连接设备）：

```bash
./stop-app.sh
# 等价：adb shell am force-stop com.dynamicui.demo
```
