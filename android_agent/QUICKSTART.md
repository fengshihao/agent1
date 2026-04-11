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
- 分层检查：仓库根执行 `./check-android-agent-layering.sh`，或 `python android_agent/scripts/check_android_layering.py`（默认扫描本模块 `app/src/main/java`）

## 快速验证

### 命令行一键编译、安装、启动（需 adb 已连上设备）

在终端进入本目录后执行：

```bash
chmod +x run.sh   # 首次可选
./run.sh
```

等价于依次执行 `./gradlew :app:assembleDebug`、`adb install -r app/build/outputs/apk/debug/app-debug.apk`、启动 `com.dynamicui.demo` 的主界面。

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

`app/build.gradle.kts` 会把这两个值注入到 `BuildConfig`，供客户端调用使用。

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

## 悬浮宠物（实验）

第三 Tab **悬浮宠物**：启动前台 `AgentForegroundService`（内嵌长期 `AgentRuntime` + 语音助手提示词），通过 DashScope **Fun-ASR WebSocket** 边录边传 PCM，长按右下角宠物说话、上滑取消、松手结束听写并提交 LLM；回复在浮层 Markdown 卡片中显示。

- 权限：麦克风、通知（API 33+）、**在其他应用上层显示**
- 提示词：`app/src/main/assets/prompts/voice_assistant_system_prompt.txt`
- **无 UI 壳冒烟**（仅拉起前台服务并 bind，不加载 Compose 浮层）：`adb shell am start -n com.dynamicui.demo/.pet.ui.view.PetHeadlessSmokeActivity`
- 桌面 **java_agent CLI** 与 Android 侧共享同一套 `AgentRuntime` / Tool 思路；Android 上工具表由 [`PetVoiceAgentTooling`](app/src/main/java/com/dynamicui/demo/pet/logic/data/PetVoiceAgentTooling.kt) 装配，编排见 [`AgentSessionCoordinator`](app/src/main/java/com/dynamicui/demo/pet/logic/business/AgentSessionCoordinator.kt)（可通过 `PetAgentToolSupplier` 注入缩减集做无头实验）。

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
