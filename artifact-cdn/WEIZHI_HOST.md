# 官方 CDN 主机

运行时与发布侧共用一个主机：`AGENT1_CDN_HOST` / `AGENT1_CDN_BASE_URL`（打包脚本别名 `ARTIFACT_CDN_BASE_URL`）。

源码与文档不写生产域名。本地把真实值放进 `artifact-cdn/cdn.local.env`（已 gitignore），或 export 环境变量。Android 打包读 `local.properties` / Gradle 属性 / 同名环境变量，写入 `BuildConfig.AGENT1_CDN_HOST`。

| 入口 | 作用 |
|------|------|
| `OfficialCdn`（java-agent-core） | 拼 packages / skills / web-libs / host-web 默认 URL，并作下载白名单主机 |
| `WeizhiPackageSync` | 默认读 `OfficialCdn.packagesRegistryUrl()`，仍可用 `AGENT1_WEIZHI_PACKAGE_REGISTRY_URL` 覆盖 |
| `pack.mjs` / `pack-web-libs.mjs` / `pack-host-web.mjs` | `--base-url` 或上述环境变量 |
| `upload.sh` | 必填 `ARTIFACT_CDN_SSH` 与 base URL |

微智 SDK 自己的 `PackageRegistryClient.OFFICIAL_HOST` 仍在 weizhi 仓；agent1 调用时显式传入 `OfficialCdn` 的 host，不依赖 SDK 常量。客户端读的是投影清单 `packages/registry/stable.json`（`packages[]`，无 `kind`）。不要把通用清单 `registry/stable.json` 配成微智默认地址。
