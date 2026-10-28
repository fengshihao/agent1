# weizhi-packages（官方预置离线快照）

本目录为 **官方预置离线快照** 的桌面 **bundled** 子集（`WeizhiBundledPackages`）：Android APK / 桌面离线兜底用。agent1 **不依赖、不感知任何外部内容仓**；如需更新快照，从内容仓（官方默认 [agent1-skills/packages-src](https://github.com/fengshihao/agent1-skills/tree/main/packages-src)，可插拔）或 CDN registry 手动替换。

云端-only 包（如 `xlsx` / `xlsx-build` / `xlsx-raw` / `chart`）**不**在此目录，生产路径靠 registry 云装；集成测试 fixture 固化在 `java_agent/weizhi-bridge/src/test/resources/weizhi-test-packages/`。

发布（内容仓侧）：内容仓的 pack 脚本 → 本仓 `artifact-cdn/upload.sh`。
