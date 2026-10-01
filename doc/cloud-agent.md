# Cloud Agent 开发约定

Cloud Agent 虚拟机磁盘约 **7GB**，不适合在会话内跑 Android Gradle（`~/.gradle` 缓存易写满盘）。

## 编译与测试：GitHub Actions

联编、单测、静态分析、Debug APK 以 **CI** 为准：[`.github/workflows/ci.yml`](../.github/workflows/ci.yml)

| Job | 内容 |
|-----|------|
| `java-test` | Weizhi 联测或 `:core:test` |
| `quality-static` | PMD、SpotBugs、Android 分层/Detekt |
| `android-assemble-debug` | `publishCoreToLocalRepo` + `assembleDebug`（可选 Weizhi native） |

推送或更新 PR 后，**务必确认上述 job 全部通过**（Cloud Agent 可用 PR 的 CI 状态工具，或 GitHub Checks 页）。

## Cloud Agent 内可跑的校验（无 Gradle）

```bash
./scripts/cloud-agent-verify.sh
```

仅执行 Python 分层、主线程 Gateway，以及 `Files.readString`/`writeString` 检查；不下载依赖、不写 Gradle 缓存。

## 本机完整复现 CI

```bash
./scripts/ci-local.sh fast    # 日常
./scripts/ci-local.sh full    # 含 Android assemble（需 SDK）
```

## 环境配置

仓库根 [`.cursor/environment.json`](../.cursor/environment.json) 的 `install` **不执行 Gradle**；若 Dashboard  Personal 环境里仍有重型 install，建议在环境设置里去掉 Android/Gradle 预编译，或重建 lean snapshot。
