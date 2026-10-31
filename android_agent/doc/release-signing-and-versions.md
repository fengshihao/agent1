# Android 签名与 versionCode

实现见 `app/build.gradle.kts`；日常安装说明见 [QUICKSTART.md](../QUICKSTART.md)。

## Debug 与 Release 签名

| 构建类型 | 签名来源 | 说明 |
|----------|----------|------|
| Debug | 仓库内 `android_agent/debug.keystore` | 团队共用，可互相覆盖安装 |
| Release（本机与 CI） | 同一把 `debug.keystore` | R8 仍开启。独立 release 签名以后再接 |

本机 `./run_release.sh` 不再生成 `release.keystore`。`release-signing.properties` 与 `release.keystore` 仍 gitignore。以后若要换独立签名，再放这两份文件（字段见 [release-signing.properties.example](../release-signing.properties.example)）；没有该文件时 Release 继续用 debug 钥匙。

## versionCode / versionName

- **覆盖**：环境变量 `VERSION_CODE`、`VERSION_NAME`。
- **CI**（`GITHUB_RUN_NUMBER` 存在）：`versionCode = 100_000 + max(run_number, git 提交数)`；`versionName` 默认为 `0.1.<ordinal>`（ordinal 为减 100_000 后的值）。
- **本地 Gradle**：`versionCode = 10_000 + git 提交数`；`versionName` 为 `0.1.<提交数>`。

历史曾用「100_000 + 提交数」打 Release；改为 run_number 后若只取 run 会出现新包低于旧包（例如 100437 &lt; 100445）。取 max 保证对 Google Play / `adb install` 单调不降。

本地号段（1 万+）通常低于 CI（10 万+）。从 CI 包降级装本地 debug 需 `adb install -r -d`，或提高本地 `VERSION_CODE`。

## 官方 CDN（BuildConfig）

源码不写生产 CDN 域名。Release / Debug APK 在编译时把主机写入 `BuildConfig.AGENT1_CDN_HOST`，启动时 `OfficialCdn.configure(...)`。未配置时使用占位 `cdn.example.test`，云装 registry / docx·md 预览壳不可用。

| 场景 | 配置方式 |
|------|----------|
| **GitHub Actions 产物 APK** | 仓库 Settings → Secrets → Actions：`AGENT1_CDN_HOST`（推荐）和/或 `AGENT1_CDN_BASE_URL`（`https://$AGENT1_CDN_HOST/`）。见 `.github/workflows/ci.yml` 的 `android-assemble-debug` job。 |
| **本机 `./run_debug.sh` / `./run_release.sh`** | 同级 `../agent1-skills/artifact-cdn/cdn.local.env`（从 `cdn.local.env.example` 复制，已 gitignore）；脚本会 `source scripts/cdn-env.sh`。或 `android_agent/local.properties` 写同名键。 |
| **桌面 `./agent1`** | 同上 `cdn.local.env`（`agent1` 入口已自动加载）。 |
| **上传** | 同一 `cdn.local.env` 另配 `ARTIFACT_CDN_SSH`、`ARTIFACT_CDN_REMOTE_DIR`，在 `agent1-skills/artifact-cdn` 跑 `upload.sh`。 |

Gradle 读取顺序见 `app/build.gradle.kts`：`local.properties` → Gradle 属性 → 环境变量。`AGENT1_CDN_HOST` 优先于 `AGENT1_CDN_BASE_URL`（完整 URL 也可，由 `OfficialCdn` 解析 host）。

主机说明在同级仓库 `agent1-skills/artifact-cdn/WEIZHI_HOST.md`。本仓只保留客户端读取（`OfficialCdn`）。

## 相关文件

- `android_agent/debug.keystore` — 共用 debug 证书（密码 `android`）
- `android_agent/.gitignore` — `release-signing.properties`、`release.keystore`
- `.github/workflows/ci.yml` — 注入 `GITHUB_RUN_NUMBER`、checkout `fetch-depth: 0`；Android 构建注入 `AGENT1_CDN_*` Secrets
