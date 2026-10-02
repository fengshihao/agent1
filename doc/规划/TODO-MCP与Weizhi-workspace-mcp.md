# TODO：MCP 发现层与 Weizhi `workspace/.mcp`

> 状态：**未决**，仅记录讨论结论，待专门 PR 处理。  
> 关联：Agent1 `McpCapabilitySync` + `capability_search`；Weizhi「目录模式」`workspace/.mcp/tools.jsonl`。

## 背景

- Weizhi `agent-tools-mcp` 在启用 MCP 时会把各 server 工具快照写入 **`sessions/.../workspace/.mcp/tools.jsonl`**，供模型 `grep`/`read_file` 发现，再通过 `mcp_call_tool`（限定名 `mcp__<server>__<tool>`）调用。
- Agent1 生产力路径已用 **`capability_search`（索引 kind=mcp）** + 脚本内 **`$mcp.<server>.<tool>`**（bridge 转 `mcp_call_tool`），外层不把每个 MCP 注册成工具。
- 两套 **发现** 通道并存时，模型易在检索失败后误读 `.mcp`（与 workspace 产出目录混淆）。

## 待讨论 / 待实现（任选或组合）

1. **Agent1 Host 装配 Weizhi 时关闭 workspace `.mcp` 写入**（或仅 Weizhi standalone 保留目录模式）。
2. **MCP 参数 schema**：索引目前只有短描述；若去掉 `tools.jsonl`，是否需要按需 schema 工具、或 agentRoot 只读文档 + `doc_path`。
3. **文档与提示**：与 `ProductivitySystemPromptBuilder`、Coach `capability.search_limit` 对齐，避免双协议。
4. **Weizhi 边界**：执行层（`McpAgentExtension` / `mcp_call_tool`）保留；发现层是否完全由 Agent1 负责。

## 参考代码

- `agent_core/.../mcp/McpCapabilitySync.java`
- `java_agent/weizhi-bridge/.../WeizhiWorkspaceTools.java`（`McpAgentExtension`）
- `java_agent/weizhi-bridge/.../WeizhiScriptToolInstaller.java`（`$mcp` → `mcp_call_tool`）

## 记录

- 2026-10-02：产品讨论 — MCP 细节延后；先记本文档。
