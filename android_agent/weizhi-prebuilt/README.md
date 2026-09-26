# Weizhi 预编译集成（私有仓库 → Agent1）

当 **weizhi 为私有 Git 仓库**、CI / 协作者无法 `git clone` 时，在 **weizhi 工程内先 publish 一版 Maven 产物**，再拷贝到本目录，Agent1 即可编出 `WEIZHI_INTEGRATED=true` 的 APK（WebView / MCP / 脚本等），**无需 weizhi 源码树**。

## 目录结构（导入后）

```text
android_agent/weizhi-prebuilt/
  coordinates.properties    # 从 coordinates.properties.example 复制并改 version
  maven/                    # 完整 Maven 仓库内容（publish 输出）
    com/weizhi/...
```

本目录已加入 `.gitignore`（默认不提交二进制）。若团队希望 **agent1 仓库内固定某一版 weizhi**，可去掉 gitignore 并由维护者手动提交 `maven/`（体积需自行评估）。

## 在 weizhi 私有仓库里（一次性 / 发版时）

1. 对 Android 多模块执行 **`publishMavenJavaPublicationTo...`** 或你们已有的 `publishToMavenLocal` / 发布到 **GitHub Packages**。
2. 产物需包含至少这些 module 的 AAR（与 Agent1 源码集成时的工程名一致）：
   - `weizhi`, `caps`, `agent-tools`, `agent-tools-webview`, `agent-tools-mcp`
3. 将 **整个 Maven 仓库目录**（含 `com/weizhi/...`）打包或 rsync 到 Agent1。

示例（weizhi 侧伪代码，以你们实际 Gradle 任务名为准）：

```bash
cd weizhi/android
./gradlew publishReleasePublicationToMavenLocal   # 或 publishAllPublicationsToGitHubPackages
tar -czf weizhi-android-maven-0.1.0.tgz -C ~/.m2/repository com/weizhi
```

## 导入 Agent1

在 **agent1 仓库根**：

```bash
# 方式 1：本地 Maven 目录（weizhi 刚 publish 出来的 repository 根）
./import-weizhi-prebuilt.sh ~/.m2/repository

# 方式 2：weizhi CI 打好的 tgz
./import-weizhi-prebuilt.sh /path/to/weizhi-android-maven-0.1.0.tgz

cd android_agent && ./gradlew :app:assembleDebug
```

导入后 App「模型配置 / 聊天详情」里的 **Agent 工具** 应显示 Weizhi / WebView / MCP。

## GitHub Actions（agent1）

不必配置 `WEIZHI_GIT_URL`。改为在 weizhi 仓库发版时上传 **maven tgz** 到私有 Release / 内网，然后在 agent1 Secrets 中配置：

- **`WEIZHI_PREBUILT_URL`**：带鉴权的下载 URL（或 Actions 用 `gh release download` + PAT）

Workflow 会在编译 Android 前执行 `./import-weizhi-prebuilt.sh "$URL"`。

## 与源码集成的优先级

Gradle **优先**使用同级 / 仓库内 **weizhi 源码**（`../weizhi/android` 或 `../../weizhi/android`）。仅当源码不存在且本目录 `maven/` + `coordinates.properties` 齐全时，才走预编译依赖。
