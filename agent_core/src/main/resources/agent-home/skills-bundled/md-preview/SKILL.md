---
name: md-preview
version: 1.0.0
description: 用户要本地预览 Markdown 成品效果时使用。用墨览（molan studio）起本地静态服务并打开浏览器阅读视图；写稿、改稿、交付前检查排版时用。只预览 .md，不做格式转换。
category: document.preview
catalog_line: 本地预览 Markdown（墨览纸面阅读视图）时用。
tags: markdown,预览,molan,墨览,阅读,文档
---
    10|
# 本地预览 Markdown（墨览 molan）

## 何时用

- 刚写完 / 改完一篇 `*.md`，用户想看**排版成品**（纸面阅读视图，非源码）。
- 交付前自查：标题层级、表格、流程图（mermaid）渲染是否正常。

**不要**用于格式转换（转 docx / pptx 走对应 Skill）；墨览预览是"打开即阅读"，保存为下载副本、不写回原文件。

    20|
## 开工

1. 先 **`load_skill`（`md-preview`）** 读本正文。
2. 用 **`run_bash`** 后台起预览服务，再把 URL 告诉用户（或直接已自动打开浏览器）。

### 命令

```bash
# agent1 仓库内
./scripts/molan-preview.sh <markdown 文件> [更多 md 文件...]

# 其它位置 / skills-home 里（skill 自带脚本）
bash "<skill 目录>/molan-preview.sh" <markdown 文件>
```

    30|
- 服务地址：`http://127.0.0.1:5517/`（可用 `MOLAN_PORT` 换端口）。
- 多文件：全部列出即可，侧栏可切换；URL 打开的是第一个文件。
- **后台运行**（agent 调用推荐）：

```bash
nohup ./scripts/molan-preview.sh out/报告.md > /tmp/molan-preview.log 2>&1 &
```

脚本会：检查 molan 仓库（`MOLAN_REPO`，默认与本仓库同级的 `../molan`）→
缺 `vendor/vditor` 时自动 `pnpm install && pnpm build && sync-media`（首次约 1–2 分钟）→
起 `apps/studio/serve.mjs` → `open` 浏览器 `?file=/doc/<文件名>`。

    40|
### 前置条件

- molan 仓库不在 `../molan` 时先克隆：

```bash
git clone https://github.com/fengshihao/molan.git ../molan
```

- 需要 `node >= 20` 与 `pnpm`（首次准备 vendor 时用；服务本身只用 node）。

## 边界与排障

| 情况 | 处理 |
|------|------|
| 端口被占 / 已有服务在跑 | 脚本自动复用已运行的服务，只换 URL 打开 |
| `vendor/vditor` 404 | 首次未准备：按上方命令手动 `pnpm install && pnpm build && node apps/vscode-molan/scripts/sync-media.mjs` |
| 页面空白 / "Vditor 预览未加载" | 同上，vendor 缺失 |
| 跨目录多个 md | 脚本自动复制到临时目录再挂载 |
| 用户要编辑并保存回原文件 | 告知墨览预览模式的保存是下载；编辑回写请直接改 `.md` 后重新预览 |

    50|
## 与其他 Skill 的分工

- 写 Word → `docx`；写 PPT → `pptx` / `html2pptx`。
- 本 Skill 只负责 **Markdown → 人眼预览**，产物仍是 `.md`。
