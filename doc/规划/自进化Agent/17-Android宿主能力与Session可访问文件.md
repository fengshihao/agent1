# 17 — Android 宿主能力介绍与会话「可访问文件列表」

> **落盘路径（权威）**：[android_agent/doc/runtime-data-layout.md](../../../android_agent/doc/runtime-data-layout.md)（实现为 `accessible-files.json` + `workspace/imports/`，非下文 `session-meta.json` 草案）。  
> 面向产品 / 集成：说明 **出站分享、入站接收、系统选择器、对话框** 等与 Weizhi Caps、Agent 工具的关系。  
> 关联：[16-渲染-文档与Android附件计划](./16-渲染-文档与Android附件计划.md)、Weizhi [INTEGRATION_FOR_AI.md](https://github.com/fengshihao/weizhi/blob/master/docs/INTEGRATION_FOR_AI.md)、Agent1 `ProductivitySystemPromptBuilder`。

---

## 1. 为什么要分「三层」

Android 上「文件」至少有三类来源，权限与工具链不同，不宜混成一个 API：

| 层 | 含义 | 典型来源 | Agent 默认能否 `read_file` |
|----|------|----------|------------------------------|
| **A. 会话工作区** | 当前 Session 私有目录，**唯一可写** | Agent 生成、`write_file`、用户从聊天附件 **复制/import 进来** | ✅ 相对路径 |
| **B. 会话可访问列表（规划）** | 用户/宿主在本 Session **显式选中**、允许 AI 读（及可选写回策略）的文件或 URI | 文件选择器、多选、从微信/系统分享 **导入** | ✅ 列表 + 映射规则（见 §5） |
| **C. 区外只读授权** | 用户授权的外部树/目录，**不在 workspace 内** | SAF `pickDirectory`、DocumentsProvider | ⚠️ Weizhi 工具环 `extraReadRoot` / Caps `files.*`（引擎 `fs` 双根未落地） |

**原则（与 [07-系统提示词与环境摘要](./07-系统提示词与环境摘要.md) 一致）：**

- **挑选、授权、Intent、Toast** → 宿主（`logic.business.platform` + Activity Result）；UI 只转发意图。
- **读写在 workspace 内** → `read_file` / `write_file` / `execute_script` + `android.files.*`。
- **模型需要知道的「当前能碰哪些文件」** → 写入 **系统提示词环境段** 和/或 **Session 持久化元数据**（每轮 `refreshRuntimeForActiveSession` 时刷新）。

---

## 2. 能力地图（按用户动作）

### 2.1 出站：把内容交给别的 App（分享）

| 能力 | 用户看到什么 | 实现方式 | Agent1 现状 |
|------|--------------|----------|-------------|
| **分享工作区文件** | 系统分享面板（微信、邮件等） | `ACTION_SEND` + `FileProvider` URI + `createChooser` | ✅ `WorkspaceFileActions.shareWorkspaceFile`；聊天附件行分享按钮 |
| **分享纯文本** | 同上，无附件 | `ACTION_SEND` `text/plain` | ✅ Weizhi `android.share.send`（Caps）；Agent1 未验证 sheet 从 Application Context 启动 |
| **分享诊断包** | 同上，zip | `DiagnosticExportAction` | ✅ 已实现 |
| **打开文件（用外部 App 查看/编辑）** | WPS / 相册 / PDF 阅读器 | `ACTION_VIEW` + MIME + `FileProvider` | ✅ `WorkspaceFileActions.openWorkspaceFile`；失败 Toast |

说明：这是 **用户从 Agent1 向外** 发 Intent，**不需要**存储权限（URI 临时授权）。

### 2.2 入站：从别的 App 把文件交给 Agent1（接收分享）

| 能力 | 典型场景 | 实现方式 | Agent1 现状 |
|------|----------|----------|-------------|
| **接收单文件/多文件分享** | 微信 → 「用 Agent1 打开」 | Activity `intent-filter`：`ACTION_SEND` / `SEND_MULTIPLE`，`mimeType`；`onCreate`/`onNewIntent` 读 `EXTRA_STREAM` | ❌ Manifest 未声明 |
| **接收文本分享** | 分享链接/段落 | `ACTION_SEND` `text/plain` | ❌ |
| **导入到 Session** | 复制到 workspace 或登记 URI | 宿主 `logic.business`：`ImportSharedFilesUseCase`（规划） | ❌ |

推荐产品路径：**入站不直接让模型读 content URI**，而是宿主 **复制到 workspace 子目录**（如 `imports/`）或 **登记到 Session 可访问列表** 并写 sidecar 元数据（原名、MIME、导入时间）。

### 2.3 对话框 / 系统选择器（宿主替用户选）

| 能力 | Android API | Weizhi Caps | Agent1 现状 |
|------|-------------|-------------|-------------|
| **确认对话框** | `AlertDialog` 等 | `android.ui.confirm` | ⚠️ 已装 Caps，但 Confirmer 默认 **自动 true**（不弹窗） |
| **选目录（整棵树）** | `ACTION_OPEN_DOCUMENT_TREE` + 持久 URI 权限 | `android.files.pickDirectory`，后续 `files.*` 走 SAF | ❌ 未接 `directoryPicker` |
| **选单个或多个文件** | `ACTION_OPEN_DOCUMENT` / `GetContent` | **无独立 Caps**；需宿主实现 | ❌ |
| **选相册图片** | `PickVisualMedia` / `ACTION_PICK` + 权限 | **无**；可宿主复制进 workspace | ❌ |
| **保存到系统（导出）** | `ACTION_CREATE_DOCUMENT` | **无**；可宿主从 workspace 弹出 | ❌ |

Weizhi 的设计是：**Caps 暴露脚本可调用的 op**，**Activity 与 Result 回调必须由宿主接**。第三方集成文档明确要求配置 `Session.confirmer`、`Session.directoryPicker`（见 Weizhi Demo `MainActivity`）。

### 2.4 脚本与工具环（模型 tool-call，非系统 UI）

| 能力 | 入口 | 说明 |
|------|------|------|
| 工作区读写 | `read_file` / `write_file` / … | 仅 Session workspace |
| 脚本 + 平台对象 | `execute_script` → `android.*` | 文件、zip、分享文案、提醒等（见 Weizhi §5） |
| 搜索 / bash / WebView / MCP | Weizhi 扩展工具 | 联编 Weizhi 时注册 |
| 区外只读 | `AgentToolsBundle.extraReadRoot` | 与 SAF 目录授权配合；Agent1 未默认配置 |

---

## 3. 与「微智 / Weizhi」集成的边界

- **编进 APK（`WEIZHI_INTEGRATED=true`）**：Caps **JS 面** + 工具环 **自动注册**；不等于 **系统 UI 自动可用**。
- **默认应有、但 Agent1 仍缺宿主接线**：真 `ui.confirm`、`files.pickDirectory`、Android 13+ 通知权限（`reminders`）、脚本 `fetch`（`enableFetch`）。
- **Weizhi 未提供、需 Agent1 自己加 Caps 或纯宿主 API**：Toast、按包名打开微信、单文件/相册选择器、接收 `ACTION_SEND`。

---

## 4. 会话「可访问文件列表」— 推荐模型

用户描述的目标可以落成：**每个 Session 维护一份「当前 AI 可读（及可选可写）的文件清单」**，由宿主在用户挑选或导入后更新；对话时把清单交给模型。

### 4.1 数据模型（建议）

```text
sessions/<sessionId>/
  workspace/                 # 已有：唯一可写根
  session-meta.json          # 规划：可访问列表 + 外部授权摘要
```

`session-meta.json` 示例字段：

```json
{
  "accessibleFiles": [
    {
      "id": "af-1",
      "displayName": "合同.pdf",
      "kind": "workspace_relative",
      "path": "imports/合同.pdf",
      "mimeType": "application/pdf",
      "source": "user_picker",
      "addedAt": "2026-09-30T12:00:00Z"
    },
    {
      "id": "af-2",
      "displayName": "Downloads/notes",
      "kind": "saf_tree",
      "uri": "content://...",
      "access": "read_only",
      "source": "pick_directory"
    }
  ]
}
```

| `kind` | 模型怎么用 | 工具链 |
|--------|------------|--------|
| `workspace_relative` | 直接 `read_file(path)` | 与 today 一致 |
| `saf_tree` | 提示「仅能通过 execute_script 的 android.files.* 读该树」或后续 `read_file` 扩展 | 需 `directoryPicker` + 持久 URI |
| `content_uri_pending_import` | 提示用户或自动 import 到 `imports/` | 宿主复制后改为 `workspace_relative` |

### 4.2 宿主职责 vs Agent 职责

| 步骤 | 谁做 |
|------|------|
| 弹选择器、处理 Result、复制文件、申请 URI 权限 | **宿主**（`platform` + Activity） |
| 更新 `session-meta.json`、校验路径不逃逸 workspace | **logic.business** |
| 刷新系统提示词、暴露给 UI（附件 chip 列表） | **ViewModel + ProductivityAgentHost** |
| 按列表读文件、写总结 | **模型 + 工具** |

**可以**不做成新 tool：列表只作 **上下文**；模型仍用 `read_file` / `list_dir`，但 **不会盲目扫整盘**。

### 4.3 注入 AI 的位置（优先级）

1. **系统提示词 · 环境段（推荐主通道）**  
   - 扩展 `ProductivitySystemPromptBuilder.buildEnvironmentSection`（或 Android 专用 builder），增加：
     - `- 本会话用户指定的可访问文件：…（相对路径 / 只读 URI 摘要）`
   - Android 已通过 `ProductivityAgentHost` 的 `hostAppend(scriptPromptAppend)` 追加 Weizhi 图像说明；**可访问列表** 适合放在 **环境段**（事实）而非 hostAppend（策略）。

2. **`hostAppend`（次要）**  
   - 仅放 **行为指令**（例如「优先读 accessibleFiles 再 list_dir 全库」）。

3. **用户消息（可选）**  
   - 用户点「添加文件」后插入一条短 user 消息：`[已添加附件] imports/a.pdf`，便于 transcript 可见；与系统提示词 **双写** 时保持路径一致。

4. **不建议仅依赖 transcript**  
   - 列表在 Session 级持久化，切换轮次、压缩上下文时仍应在 **refreshRuntime** 时重注入环境段。

5. **events.jsonl（可观测，非模型必读）**  
   - `session_files_added` / `session_files_removed` 便于审计与调试。

### 4.4 UI 与聊天附件的关系

- **Phase F（文档 16）**：从 **助手 Markdown 链接** 解析 `[报告](out/report.docx)` → 可点打开/分享（**产出物**）。
- **本会话列表**：**输入侧**用户主动提供的上下文（**inputs**），应在聊天页有 **「已选文件」** 区域，与 `WorkspaceFileAttachments`（产出）区分。

### 4.5 AI 主动请用户选文件（源头在用户侧）

除用户随时点「添加文件」外，**模型应能在缺文件时主动发起请求**，由宿主弹出系统选择器；选完后再继续同一 Session 的对话或 **续跑** 当前 run。

**原则**

- 模型 **不能** 自己打开 File Chooser；只能 **说明需要什么** → 宿主 **挂起/展示 UI** → 用户选完 → 列表更新 → 模型再 `read_file` 等。
- 与 `android.ui.confirm` 类似：**需要用户身体在环上的动作，一律走宿主**，不要假装已读到区外文件。

**推荐双通道（可并存）**

| 通道 | 模型侧 | 宿主 / UI 侧 | 适用 |
|------|--------|--------------|------|
| **A. 对话询问 + UI  affordance** | 回复中明确说「请添加合同 PDF / 选相册里的截图」；可约定轻量标记便于 Compose 解析（如首行 `[需要用户选文件: pdf,最多3个]`，实现时再定） | 该条 assistant 消息下方出现 **「选择文件」** 按钮；用户选完写入 `accessibleFiles` 并可选自动插入 user 消息 | 实现快；run 已结束，用户选完 **下一轮** 继续 |
| **B. 宿主工具 `request_user_files`（规划）** | tool-call：`kinds`（pdf/image/…）、`maxCount`、`reason`；tool 结果在用户完成前为 **pending** 或阻塞直到超时 | ViewModel 收到 tool 事件 → 弹 picker → 复制到 `imports/` → tool result 返回 `{ paths: [...] }` | 同一 run 内 **选完即继续** tool loop；需扩展 Gateway 与 **主线程** picker |

**提示词（`hostAppend` / Android 环境段补充）建议写清**

- 工作区里没有所需文件、且任务依赖用户手机/网盘/微信里的原件时：**先向用户说明需要什么**，并 **请用户通过 App 的选文件入口添加**（或触发 `request_user_files`）；**不要编造已读内容**。
- 用户选完并出现在「本会话可访问文件」列表后，再 `read_file` / 脚本处理；若用户拒绝或超时，说明无法完成并给出替代方案（例如请用户粘贴文字）。

**`session-meta.json` 扩展（可选）**

```json
{
  "pendingFileRequest": {
    "id": "pfr-1",
    "requestedAt": "...",
    "kinds": ["application/pdf"],
    "maxCount": 1,
    "reason": "整理合同条款",
    "status": "open|fulfilled|cancelled"
  }
}
```

- UI 可根据 `pendingFileRequest.status === open` 高亮选文件入口； fulfilled 后清 pending 并把路径并入 `accessibleFiles`。
- events：`user_file_request_opened` / `user_file_request_fulfilled` / `user_file_request_cancelled`。

**与 Weizhi `ui.confirm` 的对比**

| | confirm | 选文件 |
|--|---------|--------|
| 用户动作 | 确定/取消 | 系统选择器 + 可能多文件 |
| 脚本 API | `android.ui.confirm` | 无标准 Caps；**宿主工具或纯 UI** |
| 续跑 | 同步返回 boolean | 多半 **异步**，适合通道 B 或「选完发下一轮 user 消息」 |

---

## 5. 实施顺序建议（与 16 号文档正交）

| 步骤 | 内容 | 验收 |
|------|------|------|
| S1 | 宿主默认接 Weizhi：`ui.confirm`（主线程）+ `directoryPicker` | instrumented：脚本 `pickDirectory` 非 unsupported |
| S2 | `ACTION_OPEN_DOCUMENT` 多选 → 复制到 `workspace/imports/` → 更新 meta | 复制后 `read_file` 可读 |
| S3 | `session-meta.json` + 环境段注入 | 新 Session 首轮 prompt 含列表 |
| S3b | **AI 询问 + 助手消息下「选择文件」**（通道 A）+ 提示词约定 | Mock：assistant 含请求 → UI 显示按钮 → 选后列表与 user 消息更新 |
| S3c | （可选）`request_user_files` 工具 + run 续跑（通道 B） | instrumented：tool pending → picker → tool result 含 paths |
| S4 | `ACTION_SEND` 入站 + 「导入当前 Session」 | 微信分享 → imports |
| S5 | `extraReadRoot` 与 SAF 树只读 grep/read（可选） | 大目录不复制，只读检索 |

---

## 6. 安全与权限摘要

| 操作 | 权限 / 配置 |
|------|-------------|
| 联网、fetch、MCP | `INTERNET`；`enableFetch(allowlist)` |
| 提醒 | Android 13+ `POST_NOTIFICATIONS` |
| 读相册/全盘 | **尽量避免**；优先 SAF 单次 URI 或复制进 workspace |
| FileProvider | 仅暴露 `sessions/.../workspace`（见 16 §7） |

---

## 7. 读者索引

| 角色 | 文档 |
|------|------|
| Android 实现 | 本文 §2、§4、§5；`android_agent/.../WorkspaceFileActions.kt` |
| 提示词 | [07-系统提示词与环境摘要](./07-系统提示词与环境摘要.md)；`ProductivitySystemPromptBuilder` |
| Weizhi 集成 | `android_agent/weizhi-prebuilt/README.md`；Weizhi `INTEGRATION_FOR_AI.md` |
| 聊天产出附件 UI | [16 Phase F](./16-渲染-文档与Android附件计划.md) |
