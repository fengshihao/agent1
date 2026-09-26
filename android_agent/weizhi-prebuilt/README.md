# Weizhi 预编译集成（私有仓库 → Agent1）

当 **weizhi 为私有 Git 仓库**、CI / 协作者无法 `git clone` 时，在 **weizhi 工程内先 publish 一版 Maven 产物**，再拷贝到本目录，Agent1 即可编出 `WEIZHI_INTEGRATED=true` 的 APK（WebView / MCP / 脚本等），**无需 weizhi 源码树**。

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

# 方式 3：CI / 私有 Release 下载 URL
WEIZHI_PREBUILT_URL='https://...' ./import-weizhi-prebuilt.sh

cd android_agent && ./gradlew :app:assembleDebug
```

导入后 App「模型配置 / 聊天详情」里的 **Agent 工具** 应显示 Weizhi / WebView / MCP。

## GitHub Actions（agent1）

不必配置 `WEIZHI_GIT_URL`。改为在 weizhi 仓库发版时上传 **maven tgz** 到私有 Release / 内网，然后在 agent1 Secrets 中配置：

- **`WEIZHI_PREBUILT_URL`**：带鉴权的下载 URL（或 Actions 用 `gh release download` + PAT）

Agent1 workflow **Android Debug APK** 在配置了 Secret **`WEIZHI_PREBUILT_URL`** 时会执行 `./import-weizhi-prebuilt.sh`（无需 `WEIZHI_GIT_URL`）。

推荐链路：**weizhi CI** 跑 `publish-android-maven.sh` + `package-android-maven-bundle.sh` → 上传 artifact → Agent1 CI 用 `WEIZHI_PREBUILT_URL` 导入后再 `assembleDebug`。

## 与源码集成的优先级

Gradle **优先**使用同级 / 仓库内 **weizhi 源码**（`../weizhi/android` 或 `../../weizhi/android`）。仅当源码不存在且本目录 `maven/` + `coordinates.properties` 齐全时，才走预编译依赖。
