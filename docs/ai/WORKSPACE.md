# 本地 Workspace（agent1 + agent1-skills + weizhi）

日常联调涉及 **三个独立 Git 仓**，建议放在同一父目录下，并用 **多根 workspace** 一次打开，避免 Cursor 只看见 agent1 而改错副本路径。

```text
work/
  agent1/           # 本仓
  agent1-skills/    # 官方 Skill + Weizhi Package 真源（skills-src/、packages-src/）
  weizhi/           # QuickJS 引擎、Android 子工程、Caps
```

## 推荐做法

1. 一键准备同级仓（缺则 clone）并同步 weizhi 补丁：

   ```bash
   ./scripts/ensure-dev-repos.sh
   ```

   也可分步：`./scripts/ensure-agent1-skills.sh`、`./scripts/ensure-weizhi-sibling.sh`，再 `./sync-weizhi.sh`。

2. **用多根 workspace 打开**，不要只 Open Folder `agent1`：

   - 文件：`agent1/agent1.code-workspace`
   - Cursor / VS Code：**File → Open Workspace from File…**

   侧栏应有三个根：**agent1**、**agent1-skills**、**weizhi**。

3. **改哪里**

   | 内容 | 编辑位置 | 同步到 agent1 |
   |------|----------|----------------|
   | Skill 流程 / `SKILL.md` | `agent1-skills/skills-src/<name>/` | `./scripts/sync-skills-bundled.sh` |
   | `weizhi:<name>` JS 包 | `agent1-skills/packages-src/<name>/` | `./scripts/sync-weizhi-packages-bundled.sh`（bundled）+ `./scripts/pack-official-packages.sh` 上云 |
   | 引擎、Package 安装器、Android native | `weizhi/` | 联编 Android / Java；必要时 agent1 侧 PR |

4. 上云：`./scripts/pack-official-packages.sh`、`./scripts/pack-official-skills.sh`。

## 路径与环境变量

| 变量 | 含义 |
|------|------|
| `AGENT1_SKILLS_REPO` | agent1-skills 仓根（默认 `../agent1-skills`，或仓内 `agent1-skills/`） |
| `AGENT1_SKILLS_PACKAGES_ROOT` | 覆盖 `packages-src` 路径（默认 `<repo>/packages-src`） |
| `AGENT1_SKILLS_ROOT` | `sync-skills-bundled.sh` 的 `skills-src` 目录（默认 `../agent1-skills/skills-src`） |
| `AGENT1_SKILLS_GIT_URL` | `ensure-agent1-skills.sh` 克隆地址 |
| `WEIZHI_GIT_URL` / `WEIZHI_REPO_URL` | weizhi 克隆地址（默认 `fengshihao/weizhi`） |
| `WEIZHI_DIR` | `ci-local.sh` 等覆盖 Weizhi 根目录 |

Gradle 会按顺序查找 **`../../weizhi`**（同级）与 **`../weizhi`**（仓内）。多根 workspace 推荐 **同级 `../weizhi`**。若你只有 `agent1/weizhi`，可 `mv agent1/weizhi ../weizhi` 后重开 workspace。

## Cloud Agent

默认只检出 agent1。要改 Skill 或 weizhi 时，在会话里克隆对应仓或设置上述环境变量；**PR 通常分仓提交**（agent1 / agent1-skills / weizhi 各开）。
