# Catalog 安装（按需）

**安装** = 对 manifest 中的 `id` 执行 sync（只下载缺失或变更条目）。

1. `catalog_sync_status` 或 `sync check` 查看 pending  
2. `catalog_install` 或 `sync apply --ids <id>`  
3. 读 `docs/capabilities/` 了解用法；native 插件装完后脚本内 `await host.ensureNative("插件名")`

勿用 write_file 向 `shared/catalog/` 拷贝文件。
