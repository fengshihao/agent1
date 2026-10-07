# TODO：MCP 发现层与 Weizhi `workspace/.mcp`

> 状态：**已在配对分支落地**（agent1 / weizhi 均为 `save/mcp-engine-client`）。  
> 关联：Agent1 `McpCapabilitySync` + `find_caps`；Weizhi 引擎 `mcp.connect`。

## 结论

- **发现**：Agent1 `find_caps`（kind=mcp），缓存写在 `agentRoot/mcp_cache/`，不进会话 workspace。
- **调用**：脚本 `$mcp.<server>.<tool>` → Weizhi `globalThis.mcp.connect` + `callTool`。
- **不再**：装配 `McpAgentExtension`、注册 `mcp_call_tool`、写入 `workspace/.mcp/tools.jsonl`。
- 联调必须同时切这两个仓库的 `save/mcp-engine-client`；不要用 weizhi `master` 去编这份 agent1。

## 参考代码

- `agent_core/.../mcp/McpCapabilitySync.java`
- `agent_core/.../mcp/McpServersFile.java`（`scriptServersJson`）
- `java_agent/weizhi-bridge/.../WeizhiScriptToolInstaller.java`（`$mcp` → `mcp.connect`）
- weizhi `src/mcp_client.js`
