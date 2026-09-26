# Weizhi 预编译集成（私有仓库 → Agent1）

**备选路径**：当无法联编 weizhi 源码（离线、无 NDK、CI 跳过 clone）时，在 weizhi 内 **publish Maven** 再导入本目录。  
**默认推荐**仍是仓库根 **`./sync-weizhi.sh`** 源码联合编译（公开仓库 `https://github.com/fengshihao/weizhi.git`，Gradle 优先 `../weizhi/android`，见 `settings.gradle.kts`）。

## 不要只提交裸 AAR

`implementation(files("weizhi-release.aar"))` 或把单个 AAR 拷进 Agent1 **可以凑合**，但 Agent1 一次依赖 **五个模块**（`weizhi` / `caps` / `agent-tools` / `agent-tools-webview` / `agent-tools-mcp`），模块间还有 **POM 传递依赖**。应使用 **Maven 布局**（AAR + POM），与 `import-weizhi-prebuilt.sh` 一致；不要只丢裸 AAR 文件。

## 目录结构（导入后）

```text
android_agent/weizhi-prebuilt/
  coordinates.properties    # 从 coordinates.properties.example 复制并改 version
  maven/                    # 完整 Maven 仓库内容（publish 输出）
    com/weizhi/...
```

本目录已加入 `.gitignore`（默认不提交二进制）。若团队希望 **agent1 仓库内固定某一版 weizhi**，可去掉 gitignore 并由维护者手动提交 `maven/`（体积需自行评估）。

## 在 weizhi 私有仓库里（一次性 / 发版时）

weizhi 仓库已提供 Maven 发布脚本（见 [weizhi PR #1](https://github.com/fengshihao/weizhi/pull/1) 合并后的 `scripts/`）：

1. **发布到本地 Maven 布局**（输出 `android/build/maven/com/weizhi/...`，含 AAR + POM + 模块间依赖）：

   ```bash
   cd weizhi
   ./scripts/publish-android-maven.sh arm64-v8a
   ```

2. **可选打包**供 CI / `WEIZHI_PREBUILT_URL`：

   ```bash
   ./scripts/package-android-maven-bundle.sh
   ```

3. 模块需与 Agent1 源码联编时一致：`weizhi`, `caps`, `agent-tools`, `agent-tools-webview`, `agent-tools-mcp`（默认 `group=com.weizhi`，版本见 `coordinates.properties`）。

4. **GitHub Packages**（tag `android-v*` 等）：weizhi CI 可设 `WEIZHI_PUBLISH_URL=https://maven.pkg.github.com/fengshihao/weizhi`；Agent1 侧仍可用 tgz + `import-weizhi-prebuilt.sh`，或后续在 Gradle 里加只读 Packages 仓库（需 PAT）。

## 导入 Agent1

在 **agent1 仓库根**：

```bash
# 方式 1（推荐）：weizhi publish-android-maven.sh 的输出目录
./import-weizhi-prebuilt.sh /path/to/weizhi/android/build/maven

# 方式 2：weizhi package-android-maven-bundle.sh 生成的 tgz
./import-weizhi-prebuilt.sh /path/to/weizhi-android-maven-bundle.tgz

# 方式 3：CI / 私有 Release 直链 tgz
WEIZHI_PREBUILT_URL='https://.../weizhi-android-maven.tgz' ./import-weizhi-prebuilt.sh

# 方式 4：GitHub Actions artifact 页面 URL（artifact 为 zip，脚本会解压并找 com/weizhi）
export WEIZHI_GITHUB_TOKEN='ghp_...'   # 需 repo 读 + actions 读 weizhi 仓库
WEIZHI_PREBUILT_URL='https://github.com/fengshihao/weizhi/actions/runs/36242824982/artifacts/10906622668' \
  ./import-weizhi-prebuilt.sh

cd android_agent && ./gradlew :app:assembleDebug
```

导入后 App「模型配置 / 聊天详情」里的 **Agent 工具** 应显示 Weizhi / WebView / MCP。

## 发版完成后（Agent1 Checklist）

weizhi 侧 Maven 已 publish 后，在 **agent1 仓库根**按顺序做即可：

1. **导入**：`./import-weizhi-prebuilt.sh` + Maven 目录或 tgz（见上一节三种方式）。
2. **对齐版本**：打开 `android_agent/weizhi-prebuilt/coordinates.properties`，`group` / `version` 必须与 Maven 里 POM 一致（首次导入会从 `.example` 复制，默认 `0.1.0-SNAPSHOT`，若发版用了固定 tag 版本请改成实际值）。
3. **本机验证**：`cd android_agent && ./gradlew :app:assembleDebug`；若同级仍有 weizhi 源码树，Gradle **会优先源码联编**——只想测预编译时请先移走或不要 checkout 源码路径。
4. **CI 带 Weizhi 的 APK**：在 agent1 仓库 Secrets 配置 **`WEIZHI_PREBUILT_URL`**（Release tgz 直链，或 weizhi workflow 上传的 **Actions artifact 页面 URL**），并配置 **`WEIZHI_GITHUB_TOKEN`**（PAT，能读 weizhi 的 artifact；仅用 Release 公开直链时可省略）。不必再配 `WEIZHI_GIT_URL`。
5. **仅推到 GitHub Packages、没有 tgz**：可把 weizhi 本机 `android/build/maven` 目录导入 Agent1，或跑 `package-android-maven-bundle.sh` 再上传 Release 供 URL 导入（Agent1 尚未默认从 GPR 拉依赖）。

## GitHub Actions（agent1）

不必配置 `WEIZHI_GIT_URL`。改为在 weizhi 仓库发版时上传 **maven tgz** 到私有 Release / 内网，然后在 agent1 Secrets 中配置：

- **`WEIZHI_PREBUILT_URL`**：tgz 直链，或 `https://github.com/OWNER/weizhi/actions/runs/RUN_ID/artifacts/ARTIFACT_ID`
- **`WEIZHI_GITHUB_TOKEN`**：通过 GitHub API 下载 **Actions artifact** 时用（仓库公开后 artifact **仍须鉴权**，401；agent1 自带的 `GITHUB_TOKEN` **不能**跨仓库代下 weizhi artifact）。任意有 `public_repo` 的 PAT 或本机 `gh auth login` 后的 `GH_TOKEN` 即可。

Agent1 workflow **Android Debug APK** 在配置了 **`WEIZHI_PREBUILT_URL`**（及必要时 **`WEIZHI_GITHUB_TOKEN`**）时会执行 `./import-weizhi-prebuilt.sh`（无需 `WEIZHI_GIT_URL`）。

推荐链路：**weizhi CI** 跑 `publish-android-maven.sh` + `package-android-maven-bundle.sh` → 上传 artifact → Agent1 CI 用 `WEIZHI_PREBUILT_URL` 导入后再 `assembleDebug`。

## 与源码集成的优先级

Gradle **优先**使用 **weizhi 源码**（`agent1/weizhi/android` 或 `../weizhi/android`）。仅当源码不存在且本目录 `maven/` + `coordinates.properties` 齐全时，才走预编译依赖。

Agent1 CI **默认** `sync-weizhi.sh` 源码联编；预编译仅在仓库 Variable **`WEIZHI_USE_PREBUILT=true`** 且配置 **`WEIZHI_PREBUILT_URL`** 时作为 fallback。
