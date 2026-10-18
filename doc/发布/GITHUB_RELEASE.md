# GitHub 自动发版

工作流：[`.github/workflows/release.yml`](../../.github/workflows/release.yml)

## 触发方式

| 方式 | 行为 |
|------|------|
| **手动** `workflow_dispatch` | 随时发版（可选 `force` / `dry_run`） |
| **定时** 每周一 14:00 UTC | 自上次 `v0.1.*` 标签以来 **≥ 1** 个新提交才发版 |
| **推送** `master` / `main` | 自上次标签以来 **≥ N** 个新提交时发版（默认 **N = 10**） |

可在仓库 **Settings → Secrets and variables → Actions → Variables** 中设置：

- `RELEASE_EVERY_N_COMMITS`：覆盖默认的 `10`（仅影响 push 触发）

## 版本号

脚本 [`scripts/release/compute-version.sh`](../../scripts/release/compute-version.sh)：

- `versionName` = `0.1.<patch>`
- `patch` = `max(git rev-list --count HEAD, 上一 v0.1.* 标签 patch + 1)`
- `versionCode` = `10000 + patch`（发版构建时通过 `VERSION_NAME` / `VERSION_CODE` 注入 Gradle，**与 Git 标签一致**）

## 产物

- GitHub Release 标签 `v0.1.<patch>`
- 附件 `agent1-android-release.apk`（无 API Key；CI 无 `release.keystore` 时使用共用 debug 签名）

## 本地调试

```bash
chmod +x scripts/release/*.sh scripts/release/test-release-scripts.sh
./scripts/release/test-release-scripts.sh

GITHUB_EVENT_NAME=push RELEASE_EVERY_N_COMMITS=10 ./scripts/release/evaluate-release-trigger.sh
./scripts/release/compute-version.sh
```

在 Actions 页选择 **Release** → **Run workflow**，勾选 **dry_run** 可只查看门槛与版本、不上传 Release。

## 与 CI 的关系

日常 PR 仍走 [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml)（测试 + Debug/Release 构件 artifact，保留 14 天）。

发版 workflow **独立**构建 Release APK 并打 GitHub Release；不会在每个 CI run 上自动发版。
