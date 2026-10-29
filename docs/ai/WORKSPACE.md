# 本地 Workspace（agent1 + weizhi）

日常联调涉及 **两个独立 Git 仓**（agent1 与 weizhi），建议放在同一父目录下，并用 **多根 workspace** 一次打开，避免 Cursor 只看见 agent1 而改错副本路径。

```text
work/
  agent1/           # 本仓
  weizhi/           # QuickJS 引擎、Android 子工程、Caps
```

Skill / Weizhi Package **不进 jar/APK**——生产靠 registry 云装（`load_skill` / `WeizhiPackageSync.beforeEval`）。集成测试 fixture 固化在 `java_agent/weizhi-bridge/src/test/resources/weizhi-test-packages/` 与 `weizhi-test-skills/`。上云用本仓 `artifact-cdn/` 的 pack / upload 工具，指向任意源目录即可。

## 推荐做法

1. 一键准备同级仓（缺则 clone）并同步 weizhi 补丁：

   ```bash
   ./scripts/ensure-dev-repos.sh
   ```

   也可分步：`./scripts/ensure-weizhi-sibling.sh`，再 `./sync-weizhi.sh`。

2. **用多根 workspace 打开**，不要只 Open Folder `agent1`：

   - 文件：`agent1/agent1.code-workspace`
   - Cursor / VS Code：**File → Open Workspace from File…**

   侧栏应有两个根：**agent1**、**weizhi**。

3. **改哪里**

   | 内容 | 编辑位置 | 说明 |
   |------|----------|------|
   | 引擎、Package 安装器、Android native | `weizhi/` | 联编 Android / Java；必要时 agent1 侧 PR |
   | Skill 流程 / `SKILL.md` / `weizhi:<name>` JS 包 | 云端 registry | 不进 agent1 jar/APK；测试用仓内 fixture |
   | 集成测试 fixture | `java_agent/weizhi-bridge/src/test/resources/weizhi-test-{packages,skills}/` | 仅测试用，不进生产产物 |

4. 上云：`artifact-cdn` 的 pack 脚本产出 `dist/`，再 `upload.sh`。

## 路径与环境变量

| 变量 | 含义 |
|------|------|
| `AGENT1_TEST_PACKAGES_ROOT` | weizhi-bridge 集成测试的 package 源目录（默认读固化 fixture `weizhi-test-packages/`） |
| `ARTIFACT_CDN_TEST_PACKAGES_ROOT` | `artifact-cdn` 自测的包源目录（默认 `weizhi-bridge` 测试 fixture） |
| `WEIZHI_GIT_URL` / `WEIZHI_REPO_URL` | weizhi 克隆地址（默认 `fengshihao/weizhi`） |
| `WEIZHI_DIR` | `ci-local.sh` 等覆盖 Weizhi 根目录 |

Gradle 会按顺序查找 **`../../weizhi`**（同级）与 **`../weizhi`**（仓内）。多根 workspace 推荐 **同级 `../weizhi`**。若你只有 `agent1/weizhi`，可 `mv agent1/weizhi ../weizhi` 后重开 workspace。

## Cloud Agent

默认只检出 agent1。要改 weizhi 时，在会话里克隆对应仓或设置上述环境变量；**PR 通常分仓提交**（agent1 / weizhi 各开）。
