# 可信来源（catalog）

云端 catalog 由维护方发布 **catalog-index.json** 与对象存储；本地 **shared/catalog/** 仅通过 **sync apply** / **catalog_install** 更新。

- 勿从聊天或 workspace 手拷贝 `.so`、脚本包到 catalog。
- 安装前可读 **catalog-install.md**；安装后用 **docs/capabilities/** 查看用法。
- 官方 channel 与 digest 校验在阶段 5 的 sync 实现中强制执行。
