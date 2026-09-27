# 晋升（promote）— 占位

将 **workspace/staging/** 中整理好的 skill 或 script 沉淀到 **shared/local/**。

- 工作 Agent **禁止** `write_file` 写入 `shared/local` 或 `shared/catalog`。
- 正式 API：**promote_request**（阶段 6 实现；当前工具返回「未实现」说明）。
- 晋升后能力索引会更新 **docs/capabilities/**（与 sync 类似，由系统写入）。
