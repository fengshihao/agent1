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

等价于依次执行 `./gradlew :app:assembleAppDebug`、`adb install -r app/build/outputs/apk/app/debug/agent1-android-app-debug.apk`、启动 `com.agent1.android` 的主界面。

### 覆盖安装与 versionCode

Android **只认 `versionCode` 数字**（不是 APK 文件名）。若 Gradle 里长期写死 `versionCode = 1`，而手机里曾装过更高版本（例如 Android Studio 调过版本、或旧渠道包），新包会提示「当前版本低于已安装版本」，只能卸载重装。

本工程现为 **`versionCode = 10000 + git 提交数`**（见 `app/build.gradle.kts`），每次有新提交或拉新代码后编译，版本号会自动变大，一般可直接：

```bash
adb install -r app/build/outputs/apk/app/debug/agent1-android-app-debug.apk
```

仍无法覆盖时：设置里卸载旧包（旧 id 为 `com.dynamicui.demo` 的需单独卸），或临时指定更大版本：`VERSION_CODE=200000 ./gradlew :app:assembleDebug`。

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

**WebView、MCP、Weizhi 脚本与 grep/glob/zip/bash 等** 在代码里已写好（`app/src/weizhi/`、`WeizhiAgentTools`），但 **只有编译时联编 weizhi 源码或导入 Maven 预编译** 才会打进 APK（`BuildConfig.WEIZHI_INTEGRATED=true`）。

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
- 手动让 Agent 走 `webview_exec`：见 [`doc/webview-draw-e2e.md`](doc/webview-draw-e2e.md)；内置 skill `webview-canvas-draw`

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

### 启动即崩溃：只用手机（无电脑）

主界面若一打开就闪退，会话里的「导出诊断包」用不了。

**方式 A — 同一主 App（推荐，无需再装诊断包）**

较新版本在检测到「上次崩溃」时，会**先**打开「上次崩溃日志」页（不加载聊天/Weizhi），再点 **复制全部** 即可粘贴发给 Agent。

1. 安装/打开会崩溃的 APK，**闪退一次**（让系统写完日志）。
2. **再次**从桌面点图标打开（不要长按强制停止后再试也可）。
3. 若出现「上次崩溃日志」→ **复制全部**。

若仍是一闪而过、看不到日志页，说明崩溃发生在写日志之前，或当前 APK 过旧；请用方式 B 或 C。

**方式 B — 文件管理器（无需 adb）**

崩溃写入成功后，会镜像到：

`下载/Agent1/com.agent1.android/last_crash_report.txt`

用系统「文件管理器」→ **下载** → **Agent1** → **com.agent1.android** → 打开 `last_crash_report.txt`，全选复制。

**方式 C — 诊断包（与主 App 可并存，需能安装 APK）**

没有电脑时，可用**手机浏览器**从 GitHub PR / Actions 产物下载 `agent1-android-diagnostic-debug.apk`，允许「未知来源安装」后安装。桌面 **「Agent1 诊断」** 仅用于查看/复制日志，不替代主 App。

**荣耀 / MagicOS（含 Android 16）**

- 开发者选项里的「报告错误」可能无弹窗：可试 **设置 → 系统和更新 → 开发人员选项 → 提交错误报告**，或 **我的荣耀 → 反馈助手** 描述「打开 Agent1 闪退」。
- 安装前建议 **卸载** 旧的 `生产力助手` 与 `Agent1 诊断` 再装 CI 最新包。
- **诊断包** 已去掉 Weizhi 原生库，若诊断包能停留界面而主包仍闪退，多半是主包 Weizhi/会话初始化问题，请把诊断页 **复制全部** 发给排查方。

**方式 D — 系统错误报告（兜底）**

开启开发者选项 → **错误报告** / **提交错误报告**（各厂商名称略有不同），生成 zip 后从「文件管理器」里找到 bugreport，发给排查方（体积较大）。

### 启动即崩溃：有电脑时

1. **诊断包脚本**（与主 App 可并存）：
   ```bash
   chmod +x install-diagnostic.sh pull-crash-report.sh   # 首次可选
   ./install-diagnostic.sh
   ```
2. 触发主 App 闪退后，打开 **Agent1 诊断** 或执行 `./pull-crash-report.sh`。

### 未捕获崩溃日志（adb / 电脑）

私有目录：`files/last_crash_report.txt`；归档：`files/crash-reports/`。一键拉到本机当前目录：

```bash
./pull-crash-report.sh
# 或指定输出路径
./pull-crash-report.sh /tmp/crash.txt
```

手动命令（debug 包，无需 root）：

```bash
adb exec-out run-as com.agent1.android cat files/last_crash_report.txt
adb shell run-as com.agent1.android ls files/crash-reports
```

若需紧急退出应用（需 adb 已连接设备）：

```bash
./stop-app.sh
# 等价：adb shell am force-stop com.agent1.android
```
