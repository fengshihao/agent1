# 17 — 能力检索（capability_search）

> 状态：**需求 + 详细设计 + 实施安排**（2026-09-30）。  
> 背景：Agent 定位为 **编程智能体**（JS 为主、Java Tool 极简）；内置 Caps、catalog 脚本、Skill、MCP、$tools 桥等能力数量大，**不能**全部写入系统提示词。

关联：[07-系统提示词与环境摘要.md](./07-系统提示词与环境摘要.md)、[12-catalog安装与AI按需拉取.md](./12-catalog安装与AI按需拉取.md)、[Agent1-SDK愿景.md](../Agent1-SDK愿景.md)、Weizhi [AGENT_SANDBOX_PROMPT](https://github.com/fengshihao/weizhi/blob/master/docs/AGENT_SANDBOX_PROMPT.md)。

---

## 1. 问题与目标

### 1.1 问题

- 模型在外层 **臆造 tool 名**（如「分享 Intent」），或忽略 **仅存在于 QuickJS 内** 的 `android.*` API。
- `list_catalog`、`skill`、`read_agent_doc`、MCP 列表等 **分散**，缺少「接到任务 → 先搜能力 → 再写 JS」的统一协议。
- `docs/capabilities/` 在目录模型里已预留，但 **尚无结构化索引 + 搜索工具**。

### 1.2 目标

| 目标 | 说明 |
|------|------|
| **G1** | 单一工具 **`capability_search`**（或同名稳定 ID）作为能力发现主入口 |
| **G2** | 索引覆盖：builtin Caps、catalog 脚本/js_lib/native、Skill 摘要、MCP tool、$tools 桥、关键 system 文档条目 |
| **G3** | 搜索结果 **短摘要 + 指针**（doc 路径 / script 叶子名 / skill 名 / mcp 限定名），正文仍 `read_agent_doc` / `skill(read)` |
| **G4** | 系统提示词改为 **JS 优先 + 能力大类 + 必须先 search**（见 §6） |
| **G5** | catalog sync / promote 后 **索引可重建**，避免 stale |

### 1.3 非目标（本期）

- 向量 / embedding 检索（Phase C 可选）
- 用检索替代 `execute_script` 或 Weizhi 错误关键词自纠
- 云端集中式能力 DB（仍 **agentRoot 本地** 为主）

---

## 2. 存储：文档 + SQLite 索引（混合）

**结论**：正文用 **Markdown / 仓库文件**；机器检索用 **SQLite（FTS5 + 权重）**。  
**Seed** 仍用可 review 的 JSONL（classpath），bootstrap 时 **import → DB**。

| 层 | 路径 | 维护方 |
|----|------|--------|
| **索引库** | `<agentRoot>/docs/capabilities/capabilities.db` | `CapabilityIndexStore.ensure/rebuild` |
| **种子** | `agent-home/capabilities/search-index.seed.jsonl` | 随 core 发布；不直接编辑 DB |
| **人类说明** | `docs/capabilities/README.md` | 手改 + 规划文档 |
| **长文档** | `docs/system/*.md`、Weizhi 外链摘要 | agent-home 拷贝 + 版本策略 |
| **可执行体** | `shared/catalog/**`、workspace scripts | sync / promote |
| **Skill** | `.claude/skills`、`shared/*/skills` | 项目 + catalog + local |

索引一行一条 **CapabilityRecord**（见 §3）。`capability_search` 只读索引 + 可选读 manifest；**不写** shared/docs/system。

---

## 3. 索引记录 schema（v1）

每行 JSON（JSONL）：

```json
{
  "id": "caps.android.share.send",
  "kind": "caps",
  "title": "Android 文本分享",
  "summary": "execute_script 内 android.share.send({title,text})；文件分享走 App UI 或后续 host 扩展。",
  "tags": ["android", "share", "微信"],
  "platforms": ["android"],
  "entry": "android.share.send",
  "doc": { "type": "read_agent_doc", "path": "docs/system/tools-and-quickjs.md", "anchor": "caps-share" },
  "requires": [],
  "source": "bundled:v1"
}
```

### 3.1 `kind` 枚举

| kind | 含义 | `entry` 示例 |
|------|------|----------------|
| `caps` | Weizhi 平台对象 API | `android.files.read` |
| `builtin` | 引擎/fs/host/fetch/zip 等 | `host.ensureNative` |
| `catalog_script` | catalog/scripts 叶子 | `import './docx.js'` |
| `catalog_lib` | js_lib / qjs vendor | `vendor_fetch` / 路径约定 |
| `native` | catalog native 插件名 | `echo_math` |
| `skill` | Skill 流程（非函数） | `skill:travel-planner` |
| `mcp` | MCP 工具 | `mcp:server.tool` |
| `bridge_tool` | 仅 `$tools.*` 脚本内 | `$tools.grep` |
| `agent_tool` | 外层 Java Tool（宜少） | `execute_script` |
| `doc` | 纯文档条目 | `read_agent_doc:office-docx.md` |

### 3.2 字段约束

- `summary` ≤ 280 字符（与 tool 预览对齐）
- `id` 稳定、全局唯一；升级时 `source` 带版本或 hash
- `platforms`：`android` | `desktop` | `any`；桌面无 `android.*` 条目或标 `unsupported`

---

## 4. 工具设计：`capability_search`

### 4.1 注册

- 位置：`agent_core` → `ProductivityAgentHost.buildTools`
- 名称：`capability_search`（固定，进 SDK 默认 productivity 集）

### 4.2 参数 schema

```json
{
  "type": "object",
  "properties": {
    "query": { "type": "string", "description": "自然语言或关键词" },
    "kinds": { "type": "array", "items": { "type": "string" } },
    "limit": { "type": "integer", "minimum": 1, "maximum": 20 }
  },
  "required": ["query"]
}
```

**平台过滤**：不由模型传 `platform`。`ProductivityAgentHost` 装配时固定（CLI/desktop 包 → `desktop`，Android APK → `android`），检索 SQL 仍用索引行上的 `platforms` 字段过滤。

### 4.3 行为

1. 打开 `capabilities.db`（缺失或 schema 过旧 → `ensure` 从 seed 重建）。
2. **Phase A 检索**：FTS5 `MATCH` + `bm25(table, w_title, w_summary, w_tags, w_entry)`（列权 **title 10 / tags 4 / summary·entry 1**）× 行 `weight`；结果再按 **字段档位** 重排（title → tags → summary → entry → id，同档内 bm25/LIKE 分 + `weight`）。无 FTS 命中则 **LIKE 回退**（CASE 档位分与上同序）；kind/platform 过滤。
3. 返回 **纯文本**（模型友好）+ 可选 `details` JSON（UI/日志用），每条含 `id kind title summary entry doc_hint`。
4. **不**返回 SKILL 全文或 MCP 全 schema。

### 4.4 与现有工具关系

| 现有 | 检索后 |
|------|--------|
| `list_catalog` | 保留；search 给 **语义入口**，list 给 **数量摘要** |
| `skill(list/read)` | search 命中 skill → `skill(read)` |
| `read_agent_doc` | search 命中 doc → 分段阅读 |
| `catalog_install` | search 发现未安装条目 → 再 install |

---

## 5. 索引构建（CapabilityIndexBuilder）

### 5.1 输入源

| 源 | 扫描方式 |
|----|----------|
| Bundled 种子 | `classpath:/agent-home/capabilities/search-index.seed.jsonl` 或 Java 常量列表 |
| `docs/system/*.md` | 可选 front matter（二期）；v1 种子手工维护 + 单测快照 |
| `shared/catalog` | manifest / 目录枚举（复用 `ListCatalogTool` 逻辑） |
| Skills | `AgentSkillLoader` 合并 list → 标题+description 前 120 字 |
| MCP | 读 `mcp_servers.json` enabled servers → tool name + description |
| Bridge tools | `AgentToolsScriptBridge.exposedNames()` → kind=bridge_tool |

### 5.2 触发重建

- `AgentHomeBootstrap.ensure`：若 index 不存在 → 写 seed
- `catalog_install` / sync apply 成功：**append 或 rebuild**（Phase B）
- CLI：`./agent1 capabilities rebuild-index`（Phase B，可选）

### 5.3 代码位置（建议）

- `com.agent1.javaagent.capability.CapabilityIndexBuilder`
- `com.agent1.javaagent.capability.CapabilitySearchTool`
- `com.agent1.javaagent.capability.CapabilityRecord`（POJO / record）

---

## 6. 系统提示词调整（与 07 对齐）

在 `ProductivitySystemPromptBuilder` 增加固定段（示意）：

1. **编程智能体**：优先 `execute_script`（file orchestrator）；外层 Tool 仅编排。
2. **能力地图（大类）**：沙箱 fs、平台 Caps、host/native、fetch、catalog 脚本、Skill 流程、MCP、$tools 桥 — **各一行**。
3. **强制协议**：除 trivial 读写外，**先 `capability_search`**，再读 doc/skill，再写 JS。
4. **Weizhi  sandbox 长文**：不内嵌；指向 `read_agent_doc` → `docs/system/tools-and-quickjs.md` 或外链摘要。

Android hostAppend（`WeizhiHostLoader`）补充：Caps 仅脚本内；文件分享用 UI / 后续 host 扩展。

---

## 7. 可验证需求（REQ）

写入 [15-可验证需求.md](./15-可验证需求.md) 跟踪；概要如下。

### REQ-110 能力索引种子与 bootstrap

| 项 | 内容 |
|----|------|
| **做** | seed JSONL + ensure 时拷贝到 `docs/capabilities/search-index.jsonl` |
| **单测** | 空 agentRoot bootstrap 后文件存在、行数 ≥ N、JSON 合法 |
| **Covers** | UC-能力-01 |

### REQ-111 capability_search 工具

| 项 | 内容 |
|----|------|
| **做** | 工具实现 + Host 注册 + 参数校验 |
| **单测** | 关键词「docx」「share」「grep」命中预期 kind；platform 过滤 |
| **Scripted** | ProductivityAgentHostTest：fake LLM 调 search → 再 execute_script（二期） |
| **Covers** | UC-能力-02 |

### REQ-112 提示词 JS 优先 + 必须先检索

| 项 | 内容 |
|----|------|
| **做** | ProductivitySystemPromptBuilder + 单测 assert |
| **Covers** | UC-能力-03 |

### REQ-113 索引与 catalog 变更联动（Phase B）

| 项 | 内容 |
|----|------|
| **做** | catalog_install 后 rebuild 或增量 |
| **单测** | 安装 sample 脚本后 search 可命中 |
| **Covers** | UC-06 扩展 |

### REQ-114 MCP / Skill 入索引（Phase B）

| 项 | 内容 |
|----|------|
| **做** | Builder 扫描 MCP 与 skill 列表 |
| **单测** | mock mcp_servers + skill 目录 |
| **Covers** | — |

---

## 8. 用户场景（UC）

| UC | 场景 | 期望 |
|----|------|------|
| **UC-能力-01** | 新 agentRoot 首次启动 | index 存在，含 docx、caps.share 等种子 |
| **UC-能力-02** | 「把 md 转 word」 | search → docx 条目 → read doc → execute_script |
| **UC-能力-03** | 「分享到微信」 | search → 说明 caps 文本分享 + App 文件分享 UI，不臆造 Java Intent tool |
| **UC-能力-04** | catalog 新装脚本 | rebuild 后 search 命中 |

---

## 9. 实施阶段与 PR 切分

| 阶段 | 内容 | 交付 |
|------|------|------|
| **Phase A** | seed JSONL + Builder（静态）+ `capability_search` + REQ-110/111 + 提示词 REQ-112 | 1 PR |
| **Phase B** | catalog/skill/MCP 扫描 + install 后 rebuild + CLI rebuild + REQ-113/114 | 1 PR |
| **Phase C**（可选） | 别名表、中文 tags 扩展、embedding | 规划后续 |

建议 **Phase A 不依赖 Weizhi 源码树**；种子覆盖 Android/desktop 差异用 `platforms` 字段。

---

## 10. 讨论记录

- **2026-09-30**：编程智能体 + 混合存储（JSONL 索引 + Markdown 正文）；统一 `capability_search`；SDK 愿景另见 [Agent1-SDK愿景.md](../Agent1-SDK愿景.md)。

---

## 11. 开放问题

1. seed 由 **core 资源** 还是 **agent-home 目录** 携带？（建议：`agent-home/capabilities/search-index.seed.jsonl` 与 bootstrap 一致）
2. 是否在 JSONL 事件里记 `capability_search` 调用（便于分析模型是否遵守「先搜」）？
3. 三方 SDK 是否允许 **替换 IndexBuilder**（插件式追加条目）？
