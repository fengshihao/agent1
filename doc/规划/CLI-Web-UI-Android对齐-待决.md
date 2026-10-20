# CLI Web UI 与 Android 对齐 — 待你拍板

> 实现过程中**不阻塞开发**的项记录于此；默认按 Android 行为或「桌面合理缺省」推进，你确认后再改产品。

| # | 主题 | 当前默认 | 为何可能要改 |
|---|------|----------|--------------|
| 1 | **诊断 zip 范围** | 仅 `sessions/`、`logs/`、`shared/`（不含 `ui-config.json`、capabilities 全库） | 是否要整包 `agentRoot` 或含 `capabilities.db` |
| 2 | **聊天气泡 Markdown** | 轻量：图片 + workspace 文件链接 + 纯文本转义（非完整 GFM） | 是否要引入完整 Markdown 渲染（代码块、表格） |
| 3 | **MCP 保存后索引** | 保存即 `McpCapabilitySync.ensureIndexed`（可能访问远端 MCP） | 离线环境是否改为「仅保存、手动刷新索引」 |
| 4 | **Brief 聊天导出** | 已实现「📋 复制简报」（`GET .../brief` 轻量格式，非 Android 全文算法） | 是否要 1:1 移植 `BriefChatExport`（失败摘要、工具 id 索引等） |
| 5 | **设置页拆分** | 模型设置 + 独立 MCP 页；Tavily 暂留在模型设置 | 是否完全合并为 Android 式「MCP & web搜索」单页 |
