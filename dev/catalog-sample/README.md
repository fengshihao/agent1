# Catalog 样例包（阶段 5.7）

与单测资源同步：`agent_core/src/test/resources/catalog-sample/`。

- `catalog-index.json` + `scripts/sample-hello.js`（digest 与 `CatalogSyncServiceTest` 一致）
- 本地验证需 Mock HTTP 或自建静态服务；manifest URL 写入 `AGENT1_CATALOG_MANIFEST_URL`

```bash
# 示例（需自行起静态服务器指向 catalog-sample 目录）
export AGENT1_CATALOG_MANIFEST_URL="https://your-cdn/catalog-index.json"
./agent1 sync check
./agent1 sync apply --ids script.sample-hello
```
