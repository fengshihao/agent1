# 通用制品 CDN

这个目录是发布侧，不是 Agent 运行时。它把一个源目录打成 zip，计算 sha256，写进清单，再用 SSH 拷到国优服务器的网站根。下载工具只负责把 zip 取回来并校验，不负责安装。公网只能下载，不能上传。

微智 JS 包是 `kind=weizhi_package`。官方 **Skill**（SKILL.md + 可选业务脚本）用 **`pack-skill.mjs`** 打包，字段见 [doc/SKILL-PACK-FIELDS.md](./doc/SKILL-PACK-FIELDS.md)；产物含 `skills/registry/stable.json`（系统提示词目录）与合并进 `dist/registry/stable.json` 的 `kind=skill` 条目。Skill 安装：`agent_core` 的 `SkillRegistryClient`（对称 `PackageRegistryClient`，读统一 `registry/stable.json` 中 `kind=skill`）。

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
export ARTIFACT_CDN_BASE_URL=https://agentone.guoyoutech.cn/
node pack.mjs \
  --root ../agent_core/src/main/resources/agent-home/weizhi-packages \
  --kind weizhi_package \
  --base-url "$ARTIFACT_CDN_BASE_URL"
```

`--base-url` 是 CDN 上该前缀的公网根。清单里的 `artifact.url` 用它拼出来。本机试拉取时可以用 `http://127.0.0.1:<port>/`。非 loopback 必须是 `https`。

## WebView 库清单

浏览器 JS/CSS 库卡片不随 App 预置，走云端清单，和微智包同一台服务器。真源是 `web-libs/cards.jsonl`：每行一张 `kind=web_lib` 卡，`id` 以 `web_lib.` 开头，`entry` 里必须带 `https://` 的稳定 CDN 地址（库文件本身在 jsdelivr 等公共 CDN，这里不打 zip）。

```bash
cd artifact-cdn
node pack-web-libs.mjs
```

输出 `dist/web-libs/registry/stable.json`，由 `upload.sh` 一并上传。core 侧 `WebLibRegistrySync` 默认读 `https://agentone.guoyoutech.cn/web-libs/registry/stable.json`，失败时保留手机上已有的卡片。

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

`./upload.sh` 用本机 SSH 密钥把 `dist/` 拷到 `root@49.232.7.38:/var/www/agentone`。没有公网上传接口。

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

微智读的是 `https://agentone.guoyoutech.cn/packages/registry/stable.json`。通用清单在 `https://agentone.guoyoutech.cn/registry/stable.json`。打包时加上 `--cards`，把 find_caps 卡片写进对应包的 `cards`。每个包名只保留当前版本，索引按这份清单替换 `source=registry` 的行。

```bash
node pack.mjs \
  --root ../agent_core/src/main/resources/agent-home/weizhi-packages \
  --kind weizhi_package \
  --base-url "$ARTIFACT_CDN_BASE_URL" \
  --cards ../agent_core/src/main/resources/agent-home/capabilities/office-api-cards.jsonl
```

## 公网主机

微智 `PackageRegistryClient` 的默认清单和 `OFFICIAL_HOST` 已是 `agentone.guoyoutech.cn`。见 [WEIZHI_HOST.md](WEIZHI_HOST.md)。

## 自测

```bash
cd artifact-cdn
node --test 'test/*.test.mjs'
```

夹具使用仓库里的 `agent-home/weizhi-packages`，不访问阿里云。
