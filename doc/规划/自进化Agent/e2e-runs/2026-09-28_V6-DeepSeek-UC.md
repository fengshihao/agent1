# V6 DeepSeek UC 回归（自动生成报告骨架）

> 由 `E2E_DEEPSEEK_REPORT=… ./scripts/e2e-deepseek-v6.sh` 追加各 UC 节；**勿含 API Key**。

**日期**：2026-09-28（UTC）  
**模型**：`deepseek-flash`（见 env）  
**UC 列表**：默认 `01,11,02,03,04,05,06,08,09`（不含 UC-12 LLM）

**结果**：Cloud 闲时全链 `01,11,02,03,04,05,06,08,09` 均已跑完（各 UC `exit_code=0`）；曾因 **UC-04 events 校验 regex** 误报 FAIL，已修。另：V6 总控现用 **`E2E_DEEPSEEK_V6_UCS` / 独立 `AGENT1_AGENT_ROOT`**，避免环境里 `E2E_DEEPSEEK_UCS=08,09` 或 tier4 目录污染。

---## UC-08
- time: 2026-09-27T23:50Z
- model: deepseek-flash
- agentRoot: /tmp/agent1-v6-deepseek-11906
- exit: 0
```
已创建会话 fa9a2b57-b8f1-4b78-b35f-e1518a3fd2f5
Agent 数据目录: /tmp/agent1-v6-deepseek-11906
Weizhi 脚本: 未启用（构建 ../weizhi 或设置 AGENT1_WEIZHI_REPO）
工具: 工作区读写 + chat_history；扩展：grep / glob / zip / bash / load_skill / MCP / webview_exec（CDP）（Weizhi 脚本未启用）
命令: /new  /list  /use <sessionId>  /delete <sessionId>  /tools  /logs …  /summarize  /stop  /quit
非交互: ./agent1 tools  ./agent1 models  ./agent1 sync check  ./agent1 logs failed  ./agent1 你好



已完成沉淀。

**操作记录**
1. `write_file` 创建工作区文件 `staging/skills/e2e-tier3-skill/SKILL.md`（92 字符），frontmatter 为 `name: e2e-tier3-skill`，description 与正文均为「DeepSeek E2E tier3 晋升测试」。
2. `promote_request` 提交沉淀 → 结果 `promotion_completed`，auto-approved，项 `skill:e2e-tier3-skill`，capabilities 已更新 `docs/capabilities/local.*.md`。
3. 验证：`skill read e2e-tier3-skill` 返回 `source: local`，路径 `shared/local/skills/e2e-tier3-skill/SKILL.md`，内容为「DeepSeek E2E tier3 晋升测试」。

**合规确认**
- 全程未对 `shared/` 或 `docs/system` 执行 write_file（capabilities 文档的更新由 promote 流程自身完成）。
- 文件内容不含任何敏感凭据相关字样，仅含指定的测试文案。

如需继续，可以：给该 skill 补充 references 资源、或按同一流程沉淀脚本到 `staging/scripts`。

run_id=1aa692dfbede
```

## UC-09
- time: 2026-09-27T23:50Z
- model: deepseek-flash
- agentRoot: /tmp/agent1-v6-deepseek-11906
- exit: 0
```
已加载会话 fa9a2b57-b8f1-4b78-b35f-e1518a3fd2f5（请把可复用 skill 沉淀到 shared/l）
Agent 数据目录: /tmp/agent1-v6-deepseek-11906
Weizhi 脚本: 未启用（构建 ../weizhi 或设置 AGENT1_WEIZHI_REPO）
工具: 工作区读写 + chat_history；扩展：grep / glob / zip / bash / load_skill / MCP / webview_exec（CDP）（Weizhi 脚本未启用）
命令: /new  /list  /use <sessionId>  /delete <sessionId>  /tools  /logs …  /summarize  /stop  /quit
非交互: ./agent1 tools  ./agent1 models  ./agent1 sync check  ./agent1 logs failed  ./agent1 你好

确认完毕，无需再次 promote。

**`skill action=list`** — 合并后共 3 个 skill：
- `executing-plans` [project]
- `weather-query` [project]
- **`e2e-tier3-skill` [local]** — DeepSeek E2E tier3 晋升测试

**`skill action=read skill_name=e2e-tier3-skill`** 结果：
- source：`local` ✅
- path：`/tmp/agent1-v6-deepseek-11906/shared/local/skills/e2e-tier3-skill/SKILL.md`
- 正文：`DeepSeek E2E tier3 晋升测试` ✅（含目标文案）

两项确认条件均满足：来源为 local，正文包含指定文本。上一轮已完成的 promote 生效无误。

run_id=2f71f17683e9
```

