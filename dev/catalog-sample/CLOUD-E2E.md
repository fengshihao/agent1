# 真实对象存储上测 catalog sync（COS / OSS / 其他 HTTPS）

Cloud Agent **不能**自动登录你的腾讯云/阿里云控制台，也**读不到**你账号下的密钥，除非你在 **Cursor Cloud 环境变量**里显式配置（且仍建议只用**测试桶 + 最小权限**）。

## Agent1 sync 实际需要什么

同步模块只做 **HTTP GET**：

1. `GET catalog-index.json`（manifest）
2. 对 pending 条目 `GET baseUrl + path`（对象文件）

因此**不必**把云账号交给 Agent；多数场景只需 **公网可读的 HTTPS URL**。

| 方式 | 是否够测 sync | 说明 |
|------|----------------|------|
| 桶前缀 **公共读** + HTTPS 域名 | ✅ 推荐 | 上传 `catalog-index.json` 与对象，设 `AGENT1_CATALOG_MANIFEST_URL` |
| **临时签名 URL** 写在 manifest 的 `baseUrl`/path | ✅ | 密钥在你侧生成；Agent 只拉 URL |
| 桶完全私有、无签名 | ❌ | 需扩展 Agent 侧 OSS SDK 鉴权（当前未做） |
| 把 SecretId/SecretKey 给 Agent | ⚠️ 不推荐 | 若必须，仅测试子账号 + 只读 + 单前缀，写入 Cloud 环境 **Secrets** |

## 建议你提供的内容（给 Agent 或本地自测）

1. **Manifest 稳定 URL**  
   例：`https://your-bucket.cos.ap-guangzhou.myqcloud.com/agent1/catalog/v1/catalog-index.json`  
   或阿里云 OSS 绑定域名 / CDN URL。

2. **Manifest 内 `baseUrl`**  
   与对象实际前缀一致（末尾 `/`），条目 `path` 相对该前缀。

3. **（可选）一条最小样例**  
   可直接复用仓库 `agent_core/src/test/resources/catalog-sample/` 的文件与 digest。

4. **在 Cloud Agent 环境配置**（Dashboard → Environment → Secrets / Variables）  
   ```bash
   AGENT1_CATALOG_MANIFEST_URL=https://.../catalog-index.json
   AGENT1_AGENT_ROOT=/tmp/agent1-e2e   # 可选，隔离数据目录
   ```

5. **验证命令**（Agent 或你本机）  
   ```bash
   ./agent1 sync check
   ./agent1 sync apply --ids script.sample-hello
   ls "$AGENT1_AGENT_ROOT/shared/catalog/scripts/"
   ```

## 腾讯云 COS / 阿里云 OSS 上传（思路）

- 控制台或 `coscli` / `ossutil` 上传 `catalog-index.json` + `scripts/*.js`。
- 测试前缀开启公共读，或使用 CDN 回源 + 公共读。
- **不要**在 GitHub 提交 AccessKey；用 Cursor Environment Secrets。

## OBS（华为云等）

规则相同：manifest + 对象的 **HTTPS 可 GET** 即可；Agent 不区分云厂商。

## 若希望 Agent「代你上传」

当前 Agent1 **没有**内置 COS/OSS 上传工具。可选：

- 你在控制台/cli 上传一次，只给 Agent **只读 URL**（推荐）；或  
- 后续在仓库增加 `agent1 catalog publish`（超出本迭代）。
