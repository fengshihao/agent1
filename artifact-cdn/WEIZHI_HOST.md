# 微智默认 Registry 主机

公网主机是 `agentone.guoyoutech.cn`。文件在国优服务器的 `/var/www/agentone`，由 nginx 提供 HTTPS 下载。上传只走 SSH。

微智 `PackageRegistryClient`：

- `DEFAULT_REGISTRY_URL` = `https://agentone.guoyoutech.cn/packages/registry/stable.json`
- `OFFICIAL_HOST` = `agentone.guoyoutech.cn`

白名单是主机名精确匹配。客户端读的是投影清单 `packages/registry/stable.json`（`packages[]`，无 `kind`）。不要把通用清单 `registry/stable.json` 配成微智默认地址。
