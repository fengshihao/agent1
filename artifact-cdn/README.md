# 通用制品 CDN

这个目录是发布侧，不是 Agent 运行时。它把一个源目录打成 zip，计算 sha256，写进清单，再用 SSH 拷到站点根。下载工具只负责把 zip 取回来并校验，不负责安装。公网只能下载，不能上传。主机由 `AGENT1_CDN_BASE_URL` / `ARTIFACT_CDN_BASE_URL` 配置，见 [WEIZHI_HOST.md](WEIZHI_HOST.md)。

微智 JS 包是 `kind=weizhi_package`。本目录提供通用 `pack.mjs` / `fetch.mjs` / `upload.sh`，把任意源目录打成 zip 并写清单。字段见 [doc/SKILL-PACK-FIELDS.md](doc/SKILL-PACK-FIELDS.md)。产物含 `packages/registry/stable.json`、`skills/registry/stable.json` 与合并进 `dist/registry/stable.json` 的条目。

不进入 `java_agent` / `android_agent` 的 Gradle，也不进入 `./scripts/ci-local.sh`。

## TODO：清单条目标明最低客户端版本

包只增不改会引入一类兼容问题：**新包用了高版本客户端才有的能力**（如 html2pptx@0.2.1
依赖 webview 自动 async 包装修复、溢出检测桥），低版本客户端「下载成功但跑不起来」。
待办（协议变更，实施前先对齐）：

- 清单条目 schema 加 `min_client_version`（如 `"0.3.0"`；缺省视为 `0.0.0` 兼容全部，
  存量条目零迁移）；`pack.mjs` 读包内 `manifest.json` 的同名字段写入清单。
- 客户端 `WeizhiPackageSync.namesNeedingEnsure` 拿到清单后先比宿主版本
  （宿主版本来源对齐内置包 manifest / 客户端版本常量），低于则**跳过下载**、
  保留本地已装版本（行为与「只升不降」的降级保护同轨）。
- 云端 registry 条目可多版本并存时（目前只保留最新），低版本客户端回落取
  `<= 自身版本` 的最大版本。

## 清单

| 文件 | 谁读 |
|------|------|
| `dist/registry/stable.json` | 通用真源。每条有 `kind`、`name`、`version`、`artifact.url`、`artifact.sha256`、`artifact.size` |
| `dist/packages/registry/stable.json` | 从真源投影出的微智清单，只有 `packages[]`，没有 `kind`。微智现有客户端读这一份 |
| `dist/web-libs/registry/stable.json` | WebView 库卡片清单，只有 `cards[]`。core 的 `WebLibRegistrySync` 在宿主启动后读这一份刷新 find_caps |

`kind=skill` 的条目只出现在通用清单里，不会进微智投影。

对象路径：

- `weizhi_package` → `packages/<name>/<version>/<name>-<version>.zip`
- `skill` → `skills/<name>/<version>/<name>-<version>.zip`

zip 根就是源目录里的文件（含 `manifest.json`），不套一层父目录名。

## 打包

源目录的每个子目录为包名 `<name>`（**不含版本**）；`manifest.json` 的 `name` 须与目录名一致，`version` 为发布版本。目录树原样打进 zip；云端对象路径仍是 `packages/<name>/<version>/…`（制品不可变）。旧式 `<name>@<version>` 目录仍兼容（须与 manifest 一致）。仓内布局与通道预留见 [doc/集成/WEIZHI_PACKAGE_REGISTRY.md §3.1](../doc/集成/WEIZHI_PACKAGE_REGISTRY.md)。

```bash
cd artifact-cdn
export ARTIFACT_CDN_BASE_URL="$AGENT1_CDN_BASE_URL"   # 或 source cdn.local.env
# --root 指向待打包的包源目录（每子目录一个 <name>）
node pack.mjs \
  --root <package-src-dir> \
  --kind weizhi_package \
  --base-url "$ARTIFACT_CDN_BASE_URL" \
  --cards ./office-api-cards.jsonl
```

`--base-url` 是 CDN 上该前缀的公网根。清单里的 `artifact.url` 用它拼出来。本机试拉取时可以用 `http://127.0.0.1:<port>/`。非 loopback 必须是 `https`。

## WebView 库清单

浏览器 JS/CSS 库卡片不随 App 预置，走云端清单，和微智包同一台服务器。

- **真源**：`web-libs/cards.jsonl`（每行 `lib_url` + `template` 文件名，不含内嵌 HTML）
- **薄壳 HTML 真源**：`artifact-cdn/web-libs/templates/*.html`（不进 jar/APK），pack 复制到 `dist/web-libs/templates/` 并上传
- **库脚本**：仍在 jsdelivr 等公共 CDN（`lib_url`），不打 zip

```bash
cd artifact-cdn
node pack-web-libs.mjs --base-url "$ARTIFACT_CDN_BASE_URL"
```

输出：

| 路径 | 内容 |
|------|------|
| `dist/web-libs/registry/stable.json` | `find_caps` 卡片；`entry` 为两行：`URL`（库）、`壳`（官方 HTML URL，运行时 GET） |
| `dist/web-libs/templates/*.html` | 可热更的薄壳静态文件 |

由 `upload.sh` 上传模板与清单。core 侧 `WebLibRegistrySync` 默认读 `$AGENT1_CDN_BASE_URL/web-libs/registry/stable.json`，失败时保留已有卡片。薄壳**仅**从 `壳` URL 拉取，客户端不预置 HTML 模板。

## 校验下载

`fetch` 按清单 URL 下载指定条目，核对 `size`（若有）和 sha256。通过后把 zip 写到 `--out`，并把绝对路径打到标准输出。失败不留下 zip。不解压，不写安装目录。

```bash
node fetch.mjs \
  --index "${ARTIFACT_CDN_BASE_URL%/}/packages/registry/stable.json" \
  --name docx \
  --kind weizhi_package \
  --out /tmp/artifact-fetch
```

清单 URL 与 `artifact.url` 必须是同一主机（含端口）。重定向每一跳都重新检查。`http` 只允许 `127.0.0.1` / `localhost`。

微智安装仍用它自己的 `PackageInstaller`。本目录没有 Skill 安装器。

## 上传

`./upload.sh` 用本机 SSH 密钥把 `dist/` 拷到 `ARTIFACT_CDN_SSH` 上的 `ARTIFACT_CDN_REMOTE_DIR`。没有公网上传接口。生产值只放 `cdn.local.env`。

```bash
cd artifact-cdn
./upload.sh
```

顺序对所有 kind 相同：全部 zip，然后 `registry/stable.json`，再 `packages/registry/stable.json`，最后 `web-libs/registry/stable.json`。这样清单不会指向还没传完的对象。

`UPLOAD_DRY_RUN=1 ./upload.sh` 只打印 `scp`。清单里如果是 loopback 地址，默认拒绝上传。

服务器上的目录和 `dist/` 一样：

```text
/var/www/agentone/
  registry/stable.json
  packages/registry/stable.json
  packages/<name>/<version>/<name>-<version>.zip
  skills/<name>/<version>/<name>-<version>.zip
  web-libs/registry/stable.json
```

微智读的是 `$AGENT1_CDN_BASE_URL/packages/registry/stable.json`。通用清单在 `$AGENT1_CDN_BASE_URL/registry/stable.json`。打包时加上 `--cards`，把 find_caps 卡片写进对应包的 `cards`。每个包名只保留当前版本，索引按这份清单替换 `source=registry` 的行。

```bash
node pack.mjs \
  --root <package-src-dir> \
  --kind weizhi_package \
  --base-url "$ARTIFACT_CDN_BASE_URL" \
  --cards ./office-api-cards.jsonl
```

## 公网主机

Agent1 侧默认清单主机见 [WEIZHI_HOST.md](WEIZHI_HOST.md)（`OfficialCdn` / `AGENT1_CDN_HOST`）。

## 自测

```bash
cd artifact-cdn
node --test 'test/*.test.mjs'
```

夹具默认使用仓内固化测试快照 `java_agent/weizhi-bridge/src/test/resources/weizhi-test-packages/`（无外部仓依赖）；`ARTIFACT_CDN_TEST_PACKAGES_ROOT` 可覆盖。
