# 11 — QuickJS 调试与行号映射

> 目标：脚本失败时，AI 收到 **用户脚本** 上的 **行/列** 与片段，而不是被 prelude / 注入代码误导。

## 现状（代码事实）

执行链：

```text
ExecuteScriptTool
  → optional __agentArgs prelude（Java 拼）
  → WeizhiScriptToolInstaller.wrap()  → $tools Proxy prelude（~15 行）
  → WeizhiEngine.runJs(combinedSource)
```

失败时 `ExecuteScriptTool` 仅 `ToolExecutionResult.text(e.getMessage())`，**无结构化行号**（见 `ExecuteScriptTool.java`）。

AI 常见问题：

- 报错行号指向 **combined** 源的第 N 行，对应 **prelude 或 Weizhi 内建**，用户脚本从第 M 行才开始。  
- inline `code` 无文件名，栈里全是 `<eval>`。  
- Weizhi 自身还有 caps / fs 等注入，AI 未读 `docs/system/tools-and-quickjs.md` 时易误判。

## 目标输出（工具失败时 JSON 或固定格式文本）

建议 `execute_script` 失败返回 **机器可读** 块（仍遵守 tool preview 上限时可截断 snippet）：

```json
{
  "ok": false,
  "errorType": "SyntaxError",
  "message": "unexpected token",
  "location": {
    "userLine": 12,
    "userColumn": 4,
    "engineLine": 28,
    "engineColumn": 4
  },
  "source": {
    "kind": "file",
    "path": "scripts/foo.js"
  },
  "snippet": "  return x;\n  ^",
  "prelude": {
    "agentArgsLines": 1,
    "weizhiToolsLines": 15,
    "totalSkippedLines": 16
  },
  "hint": "Line numbers in 'location.userLine' are relative to your script only."
}
```

**`userLine` / `userColumn`** = 引擎报告位置减去 **已知 prelude 行数**（并做列对齐若仅整行报告）。

## 行号映射策略

### 层 1 — 可精确计数的 prelude（Agent1 侧，M-debug-1）

| 块 | 行数计算 |
|----|----------|
| `__agentArgs` | `buildArgsPrelude` 实际行数（通常 1） |
| `$tools` prelude | `WeizhiScriptToolInstaller` 固定模板行数 **常量** `WEIZHI_TOOLS_PRELUDE_LINES`（与 `prelude()` 同步维护） |

记录 `ScriptEvalFrame`：

- `userSource`（原文）  
- `combinedSource`  
- `userSourceStartLine` = preludeLines + 1  
- `sourceKind`：`inline` \| `file`  
- `filePath`（若 file）

映射公式（单行错误）：

```text
userLine = max(1, engineLine - preludeLineCount)
```

若引擎 **0-based** 或含 shebang，在单测中固定约定。

### 层 2 — 按文件执行 + sourceURL（M-debug-2）

- 读 workspace 文件后，用 Weizhi 支持的 **文件名/行号绑定**（若 `runJs` 支持第二参数 filename 或前缀 `//# sourceURL=workspace/scripts/foo.js`）让栈帧带 **用户路径**。  
- **强烈建议** Coach 钩子 `script.inline_long` 推动 `file` 模式。

### 层 3 — Weizhi 内建注入（需 weizhi 库协作，M-debug-3）

若 `runJs` 前还有 **不可见** bootstrap：

- 在 weizhi 侧提供 **`getBootstrapLineCount()`** 或错误栈 **已 remap 到 user 段** 的 API；  
- 或文档化「内建占 N 行」仍不精确时，在错误 JSON 加 `"bootstrapUncertain": true`，提示 AI 以 **snippet 上下文** 为准。

Agent1 与 weizhi 版本耦合：**manifest 记录 weizhi 版本 + prelude 行数**。

## 错误类型覆盖

| 类型 | 处理 |
|------|------|
| **SyntaxError** | 通常有 line/column；优先 remap |
| **Runtime Error** | 解析 stack string，取首帧；剥离 `$tools` prelude 内帧 |
| **超时 / cancel** | 无行号；返回 `errorType: timeout` |
| **Host / $tools** | 标 `errorType: tool_bridge`，**不计入** userLine（属 AI 调工具参数问题） |

## 栈清洗规则

从 Weizhi/QuickJS 原始 message 中：

1. 去掉仅含 `__caps`、`$tools`、prelude IIFE 的帧。  
2. 若存在 `file` 路径，只展示 **workspace 相对路径** 下的帧。  
3. 原始 engine 行号保留在 `engineLine` 供工程师 debug；**默认给 AI 只看 userLine**。

## 与文档 / 提示词

- [07](./07-系统提示词与环境摘要.md) 摘要加一句：**脚本错误以 `userLine` 为准；ignore prelude lines**。  
- `docs/system/tools-and-quickjs.md` 说明：prelude 作用、禁止依赖 prelude 行号、推荐 `file` 执行。  
- [10](./10-运行时钩子与Coach提示.md)：`script.fail_repeat` 指向结构化错误字段。

## 实现里程碑（[09](./09-行动计划.md)）

| 代号 | 内容 | 验收 |
|------|------|------|
| **D1** | `ScriptEvalFrame` + 已知 prelude 行数 | 单元测试覆盖扣减逻辑 |
| **D2** | 结构化错误 + snippet | 失败必带 `userLine`/`userColumn` 字段 |
| **D3** | **默认推荐 file 执行** + sourceURL / 文件名 | 报错必须是 `foo.js:12` 且 **12 就是用户文件第 12 行** |
| **D4** | **weizhi 协作**：bootstrap 行数或引擎 remap | 集成测试：inline 与 file 多种错误类型 **行号零偏差** |

### 产品要求（已定）

**行号必须准确。** 若只扣 Agent1 prelude、不处理 Weizhi 内建注入，视为**未达标**，不能开启依赖脚本的 **M3 晋升** 验证——否则 AI 会持续改错行。

**Gate 验收例**（weizhi 集成环境必跑）：

1. `workspace/scripts/t.js` 第 5 行故意语法错 → 返回 **userLine=5**。  
2. 第 5 行运行时 `throw` → 栈首帧 **t.js:5**。  
3. inline 仅当 D4 证明仍准确时才宣传给 AI；否则 Coach 强制 **写文件再跑**。

## 单测思路（不依赖完整 native 时）

- 纯 Java：prelude 行数、`mapEngineLineToUserLine(engineLine, preludeLines)`。  
- Mock `ScriptEngine` 抛出带 `line 20` 的 message，断言 userLine=20-16。  
- 有 weizhi：与 `WeizhiScriptEngineIntegrationTest` 同级加 `syntaxErrorReportsUserLine`。
