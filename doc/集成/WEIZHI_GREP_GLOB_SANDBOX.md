# grep/glob 与 Agent1 沙箱

`grep` / `glob` 在 `agent_core`，走 `com.agent1.javaagent.workspace.WorkspaceSandbox`。Weizhi 不再提供这些模型工具，也没有 `WeizhiSandboxFactory`。

下文是迁移前的接口记录。

## 微智当前接口（master `android/agent-tools`）

### Sandbox

```java
new WorkspaceSandbox(Path baseDir);
new WorkspaceSandbox(Path baseDir, Path extraReadRoot);

Path getBaseDir();
Path resolveRead(String relativePath);   // 多 read root 尝试 containment
Path resolveWrite(String relativePath); // 仅 baseDir
String relativize(Path abs);            // 优先相对 workspace，否则 abs 字符串
```

### grep

| 参数 | 必填 | 说明 |
|------|------|------|
| `pattern` | 是 | Java 正则 |
| `path` | 否 | 目录/文件，默认工作区根 |
| `include` | 否 | 文件名 glob → 过滤 |
| `output_mode` | 否 | `content`（默认）/ `files` / `count` |

输出：`rel:line:content`（content 模式），上限默认 100 行。

### glob

| 参数 | 必填 | 说明 |
|------|------|------|
| `pattern` | 是 | glob（内部 `GlobToRegex`） |
| `path` | 否 | 起始目录，默认工作区根 |
| `limit` | 否 | 默认 100，按 mtime 新→旧 |

## Agent1 期望路径语义

| 路径示例 | 读写 | 解析根 |
|----------|------|--------|
| `hello.txt` | 读写 | session workspace |
| `docs/system/office-docx.md` | **只读** | agentRoot + 白名单 |
| `docs/capabilities/manifest.json` | **只读** | agentRoot + 白名单 |
| `docs/other/…`、`shared/…` | grep/read **拒绝** | 不暴露整棵 agentRoot |

展示路径：文档区文件在 tool 输出中应稳定为 `docs/system/...`，而非绝对路径。

## 兼容差距（需 Weizhi Issue 解决）

1. **只读挂载**：单个 `extraReadRoot=agentRoot` 会放开 `shared/`、`logs/` 等；需要 **前缀挂载**或 `ReadPathPolicy`（Agent1 已用 `AgentReadScope`）。
2. **relativize**：extra root 下文件应返回 **逻辑路径**（与 `read_file` 一致）。
3. **Agent1 侧**：`WeizhiWorkspaceTools` / Host 只注册微智 grep/glob；删除 `agent_core` 临时实现；`WeizhiToolkitAdapters` 不变。

## Agent1 装配

- `AgentDocReadMounts.forAgentRoot(agentRoot)` → `docs/system`、`docs/capabilities`
- `WeizhiSandboxFactory.forProductivity(agent1Sandbox)` → 微智 `WorkspaceSandbox(writeRoot, readMounts)`
- `WeizhiWorkspaceTools` / Android `WeizhiAgentTools` 注册 `com.weizhi.agent.tool.builtin.GrepTool` / `GlobTool`
