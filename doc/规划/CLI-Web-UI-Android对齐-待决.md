# CLI Web UI 与 Android 对齐 — 待你拍板

> 实现过程中**不阻塞开发**的项记录于此；默认按 Android 行为或「桌面合理缺省」推进，你确认后再改产品。

| # | 主题 | 当前默认 | 为何可能要改 |
|---|------|----------|--------------|
| 1 | **诊断 zip 范围** | 仅 `sessions/`、`logs/`、`shared/`（不含 `ui-config.json`、capabilities 全库） | 是否要整包 `agentRoot` 或含 `capabilities.db` |
| 2 | **聊天气泡 Markdown** | 图片/文件链接 + **围栏代码块** + **行内代码**（仍非完整 GFM 表格等） | 是否要引入完整 Markdown 渲染（表格、任务列表） |
| 3 | **MCP 保存后索引** | 保存即 `McpCapabilitySync.ensureIndexed`（可能访问远端 MCP） | 离线环境是否改为「仅保存、手动刷新索引」 |
| 4 | **Brief 聊天导出** | ✅ core `BriefChatExport` + 抽屉 📋 复制 | Android Kotlin 与 Java 双份实现，后续可考虑抽到单模块 |
| 5 | **设置页拆分** | 模型设置 + 独立 MCP 页；Tavily 暂留在模型设置 | 是否完全合并为 Android 式「MCP & web搜索」单页 |
