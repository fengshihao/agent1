package com.agent1.javaagent.prompt;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.List;

/**
 * 桌面（macOS / Ubuntu）默认环境信息：平台、屏幕、QuickJS 运行环境、工作区目录标注。
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

    /** 主显示器逻辑尺寸；无头环境（CI / 无显示器的 Linux）取不到则返回空串，不渲染该行。 */
    @Override
    public String screenLine() {
        try {
            Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
            return size.width + "×" + size.height;
        } catch (RuntimeException e) {
            // HeadlessException 等：无显示环境，屏幕尺寸对决策无意义
            return "";
        }
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
