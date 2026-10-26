# Android 签名与 versionCode

实现见 `app/build.gradle.kts`；日常安装说明见 [QUICKSTART.md](../QUICKSTART.md)。

## Debug 与 Release 签名

| 构建类型 | 签名来源 | 说明 |
|----------|----------|------|
| Debug | 仓库内 `android_agent/debug.keystore` | 团队共用，CI Debug/无 release 配置的 Release 均用此 keystore，可互相覆盖安装 |
| Release（本机） | `android_agent/release-signing.properties` | 复制 [release-signing.properties.example](../release-signing.properties.example) 或执行 `./bin/init-release-keystore` |
| Release（CI） | 无 `release-signing.properties` 时退回 **debug keystore** | 与 Debug CI 包同签名；R8 仍开启 |

`release-signing.properties` 已 gitignore，勿提交。字段：`storeFile`（相对 `android_agent/`）、`keyAlias`、`storePassword`、`keyPassword`。

## versionCode / versionName

- **覆盖**：环境变量 `VERSION_CODE`、`VERSION_NAME`。
- **CI**（`GITHUB_RUN_NUMBER` 存在）：`versionCode = 100_000 + max(run_number, git 提交数)`；`versionName` 默认为 `0.1.<ordinal>`（ordinal 为减 100_000 后的值）。
- **本地 Gradle**：`versionCode = 10_000 + git 提交数`；`versionName` 为 `0.1.<提交数>`。

历史曾用「100_000 + 提交数」打 Release；改为 run_number 后若只取 run 会出现新包低于旧包（例如 100437 &lt; 100445）。取 max 保证对 Google Play / `adb install` 单调不降。

本地号段（1 万+）通常低于 CI（10 万+）。从 CI 包降级装本地 debug 需 `adb install -r -d`，或提高本地 `VERSION_CODE`。

## 相关文件

- `android_agent/debug.keystore` — 共用 debug 证书（密码 `android`）
- `android_agent/.gitignore` — `release-signing.properties`、`release.keystore`
- `.github/workflows/ci.yml` — 注入 `GITHUB_RUN_NUMBER`，checkout `fetch-depth: 0`
