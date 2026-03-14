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

## 快速验证

1. 在 Android Studio 打开 `agent_ui/android`
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
