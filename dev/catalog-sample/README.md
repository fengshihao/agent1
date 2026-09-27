# Catalog 样例包（阶段 5.7 / P.1）

与单测资源同步：`agent_core/src/test/resources/catalog-sample/`。

| id | kind | 文件 |
|----|------|------|
| `script.sample-hello` | script | `scripts/sample-hello.js` |
| `lib.sample-inc` | js_lib | `libs/js/sample-inc.js` |

自动化：`CatalogSyncServiceTest`、`CatalogSampleManifestIntegrationTest`。

## 本地手测（无 COS）

终端 1：

```bash
chmod +x dev/catalog-sample/serve-local.sh
./dev/catalog-sample/serve-local.sh
```

将 `catalog-index.json` 内 `baseUrl` 改为 `http://127.0.0.1:8765/`（与静态服务前缀一致），保存后终端 2：

```bash
export AGENT1_CATALOG_MANIFEST_URL="http://127.0.0.1:8765/catalog-index.json"
export AGENT1_AGENT_ROOT=/tmp/agent1-catalog-$$
./agent1 sync check
./agent1 sync apply --ids script.sample-hello,lib.sample-inc
```

## 云端 HTTPS

见 [CLOUD-E2E.md](./CLOUD-E2E.md)。
