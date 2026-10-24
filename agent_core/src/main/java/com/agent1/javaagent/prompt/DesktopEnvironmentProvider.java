package com.agent1.javaagent.prompt;

import java.util.List;

/**
 * 桌面（macOS / Ubuntu）默认环境信息：平台、QuickJS 运行环境、工作区目录标注。
 * Android 宿主请注入自己的 {@link HostEnvironmentProvider} 实现。
 */
public final class DesktopEnvironmentProvider implements HostEnvironmentProvider {

    public static final DesktopEnvironmentProvider INSTANCE = new DesktopEnvironmentProvider();

    private DesktopEnvironmentProvider() {
    }

    @Override
    public String platformLine() {
        String osName = System.getProperty("os.name", "unknown");
        String osVersion = System.getProperty("os.version", "");
        return osName + (osVersion.isBlank() ? "" : " " + osVersion);
    }

    @Override
    public String jsRuntimeLine() {
        return "QuickJS 扩展，非 Node；不能 npm 或 require";
    }

    @Override
    public List<String> directoryLines() {
        return List.of(
            "workspace/：本会话工作区（唯一可写）；外层文件工具用相对工作区根的路径（不要 workspace/ 前缀）",
            "shared/：公共能力库（只读），不能用 write_file 修改",
            "sessions/、logs/、sync/：系统目录（只读），文件工具不要写"
        );
    }
}
