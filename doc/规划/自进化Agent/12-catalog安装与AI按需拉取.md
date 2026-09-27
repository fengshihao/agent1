# 12 — Catalog 安装与 AI 按需拉取

> SO、脚本、图片、JS 库 **共用一套「清单 + sync apply」**；AI **根据文档** 在需要时安装，不必每次全量同步。

## 一句话

**安装 = 对某个 catalog `id` 执行 sync apply**（只下载缺失或变更的条目）。没有单独的「SO 安装程序」。

## 文档分工

| 文档 | 内容 |
|------|------|
| `docs/system/catalog-install.md` | 通用流程：check → 看 pending → apply --ids；禁止 write_file 写 catalog |
| `docs/system/trusted-sources.md` | 哪些 CDN/清单可信 |
| `docs/capabilities/` | 每个（或每类）资源的 **何时需要、id 是什么、装完怎么用** |
| 插件自带 `AGENT_SNIPPET.md`（sync 后可在 capabilities 里链到） | native：`ensureNative` + 方法签名 |

系统提示词摘要只写：**「缺资源时读 catalog-install + capabilities，用 sync apply 安装」**。

## AI 典型流程（举例）

**用户**：帮我把这批照片缩略图处理一下。

1. AI 读 capabilities → 需要 **`native.image_resize`**（示例 id）。  
2. `catalog_sync_status` → 未安装或版本旧。  
3. 调用 **`sync apply --ids native.image_resize`**（或封装工具）。  
4. 读 snippet → 脚本里 `await host.ensureNative("image_resize")` …  
5. 在 **workspace** 写 `.js`，`execute_script` 执行。

若 check 显示 **已安装且 up to date** → 直接第 4 步，**不下载**。

## 与 Coach 联动（见 [10](./10-运行时钩子与Coach提示.md)）

| hookId | 触发 | 提示 |
|--------|------|------|
| `catalog.missing_native` | 脚本/文档引用某 native id，本地无 digest | 读 capabilities，对 id 执行 sync apply |
| `catalog.pending` | pending 含任务相关 id | 建议 apply，勿手抄 SO |

## 实现备忘

- 工具名统一：`catalog_sync_status` + `catalog_install`（内部即 apply --ids）降低 AI 理解成本。  
- native apply 成功后更新 `sync/state.json` 与 **插件目录索引**；尽量无需用户重启 CLI。
