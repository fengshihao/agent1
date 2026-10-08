# Cloud Agent 开发约定

环境由 [`.cursor/environment.json`](../.cursor/environment.json) 安装：**JDK 17**、公开仓库 `fengshihao/weizhi`（检出到 `/weizhi`，并链接为仓库内 `weizhi`）、以及 `java_agent` 的 Gradle 依赖缓存。安装脚本是 `scripts/cloud-agent-install.sh`；每次启动用 `scripts/cloud-agent-start.sh` 确认 JDK 与 weizhi 已就绪。

## 编译与测试

Java 单测与静态分析可在 Cloud Agent 内运行：

```bash
./java_agent/gradlew --no-daemon -p java_agent :core:test :cli:test
./check-java-agent-static.sh
```

Android Debug APK、NDK 与完整 CI 仍以 **GitHub Actions** 为准：[`.github/workflows/ci.yml`](../.github/workflows/ci.yml)。本环境不安装 Android SDK。

| Job | 内容 |
|-----|------|
| `java-test` | Weizhi 联测或 `:core:test` |
| `quality-static` | PMD、SpotBugs、Android 分层/Detekt |
| `android-assemble-debug` | `publishCoreToLocalRepo` + `assembleDebug`（可选 Weizhi native） |
| `Release`（`release.yml`） | 手动 / 每周 / 每 N commit 打 GitHub Release + Release APK |

推送或更新 PR 后，**务必确认上述 job 全部通过**（Cloud Agent 可用 PR 的 CI 状态工具，或 GitHub Checks 页）。发版说明见 [`doc/发布/GITHUB_RELEASE.md`](发布/GITHUB_RELEASE.md)。

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

仓库根 [`.cursor/environment.json`](../.cursor/environment.json) 的 `install` 会执行 `scripts/cloud-agent-install.sh`（JDK 17、`/weizhi`、`:core:compileJava` 与 `:cli:compileJava`）。不包含 Android SDK 或 `assembleDebug`。
