# 桌面 Web UI 发行包（仅 UI · 尽量小）

## 目标

- **一个 zip 解压即用**（不捆绑 JRE），面向 macOS / Windows / Ubuntu x64（及常见 arm64）。
- **不含** 终端 REPL（JLine）、不含 Android / Gradle 开发链。
- 用户本机需 **Java 17 或更高**（21、25 等均可；**不能低于 17**，因 jar 按 17 编译）；没有则启动脚本提示并给出安装页（**默认国内下载链接**）。

## 体积策略（相对完整 `fatJar` ~24MB）

| 措施 | 约节省 |
|------|--------|
| 去掉 **JLine**（`uiFatJar` + `DesktopUiLauncher`） | ~3 MB |
| **sqlite-jdbc 按平台 classifier**（不打全平台 native） | ~17–20 MB |
| 不打包 JRE | zip 不增 ~30–50 MB |

典型 **ui jar** 约 **8–11 MB**（视平台），**zip** 约 **7–9 MB**（仅脚本 + README + jar）。

## 构建

```bash
./scripts/package-desktop-ui.sh
# 输出 dist/agent1-desktop-ui-<os>-<arch>.zip
```

底层任务：

```bash
cd java_agent
./gradlew :cli:uiFatJar -Pagent1.sqliteClassifier=macosx-aarch64
# linux-x86_64 | linux-aarch_64 | macosx-x86_64 | win-x86_64
```

构建时从完整 `sqlite-jdbc` 中**只保留当前平台**的 `org/sqlite/native/...` 目录（无需 Maven classifier 构件）。

## 用户是否需要自带 Java？

| 平台 | 默认是否带 Java 17+ |
|------|---------------------|
| **macOS** | **否**（近年系统不预装 JRE/JDK） |
| **Windows 10/11** | **否** |
| **Ubuntu 桌面** | 多数需 `apt install openjdk-17-jre`，最小安装可能也没有 |

缺 Java 时：`bin/agent1-ui` / `agent1-ui.cmd` / `agent1-ui.ps1` **先提示国内下载**（腾讯 Kona、阿里 Dragonwell），再附国际 Temurin（Eclipse Adoptium，非 Oracle）。

启动脚本**查找顺序**（与仓库 `./agent1` 类似）：

1. 环境变量 `JAVA17_BIN`（显式指定 `java` 路径）
2. `JAVA_HOME/bin/java`
3. **PATH** 里的 `java`（Windows 用 `where java`）
4. **macOS 额外**：`/usr/libexec/java_home` 指向的安装

**Shell**：macOS / Linux 用 **Bash**（`agent1-ui`）；Windows 提供 **CMD**（`agent1-ui.cmd`）与 **PowerShell**（`agent1-ui.ps1`）。在 PowerShell 里也可执行 `.\bin\agent1-ui.cmd`。

## 可选：捆绑 `jlink` 运行时（免装系统 Java）

`jlink` 会从 JDK 裁出一份**最小运行时**（自带 `bin/java`），打进 zip 的 `jre/` 目录；启动脚本优先用 `./jre/bin/java`，用户**不必再装**系统 JRE。

| 形态 | 约 zip 体积 | 用户是否要装 Java |
|------|-------------|-------------------|
| **当前：仅 ui jar**（本规划默认） | **~8–10 MB** | 要，按上面链接安装 17+ |
| **ui jar + jlink 运行时**（未实现，可另做发行档） | **~38–45 MB** | **不要**（用包内 `jre/`） |
| ui jar + 完整 JRE 安装包 | 更大，不推荐 | 不要 |

本机粗测（macOS，Temurin 17）：`jlink` 裁剪运行时磁盘 **~48 MB**，单独 zip 约 **~30 MB**；加上 **~10 MB** 的 `cli-*-ui.jar` 与脚本，**整包 zip 大约 40 MB 量级**（随 OS/模块列表略有浮动）。  
与「不捆绑 JRE」相比，用体积换「解压即跑、不碰国外下载站」。

## zip 布局

```text
agent1-desktop-ui-macos-arm64/
  README.txt
  bin/agent1-ui          # 或 Windows: agent1-ui.cmd
  lib/cli-0.1.0-SNAPSHOT-ui.jar
```

入口类：`com.agent1.javaagent.cli.DesktopUiLauncher` → `./agent1 ui` 等价（仅 Web UI）。

## 非目标（本包不做）

- 捆绑 JRE（需要可另做 `jpackage` 大 zip）
- 捆绑 Chromium（`webview_exec` 仍依赖用户配置 `AGENT1_CHROMIUM_PATH` 或系统 Chrome）
- 替代开发者路径：`./agent1` + Gradle + `npm run dev`
