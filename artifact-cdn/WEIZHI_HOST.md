# 微智默认 Registry 主机

微智 `PackageRegistryClient` 仍指向占位主机 `cdn.weizhi.example`。本目录不改微智代码。

等发布用的 `ARTIFACT_CDN_BASE_URL` 已是正式 HTTPS 域名（不是 `127.0.0.1`，也不是 `.example`）之后，在微智仓库另开 PR，只改这两个常量：

- `PackageRegistryClient.DEFAULT_REGISTRY_URL` = `{ARTIFACT_CDN_BASE_URL}packages/registry/stable.json`
- `PackageRegistryClient.OFFICIAL_HOST` = 该 URL 的主机名

白名单是主机名精确匹配，不是后缀匹配。OSS 默认域名和 CDN 域名必须是同一个被允许的主机。客户端读的是投影清单 `packages/registry/stable.json`（`packages[]`，无 `kind`）。不要把通用清单 `registry/stable.json` 配成微智默认地址。

没有真实主机名之前保持占位常量不动。不要用假域名开这个 PR。
