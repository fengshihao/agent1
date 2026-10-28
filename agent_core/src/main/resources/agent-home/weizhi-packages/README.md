# weizhi-packages（classpath 副本）

**真源**：[agent1-skills/packages-src](https://github.com/fengshihao/agent1-skills/tree/main/packages-src)。

本目录仅保留桌面 **bundled** 子集（`WeizhiBundledPackages`），由 `./scripts/sync-weizhi-packages-bundled.sh` 从 agent1-skills 同步。云端-only（如 `xlsx` / `xlsx-build` / `xlsx-raw`）不在此目录；单测与联调读 `packages-src`。

发布：`./scripts/pack-official-packages.sh` → `artifact-cdn/upload.sh`。
