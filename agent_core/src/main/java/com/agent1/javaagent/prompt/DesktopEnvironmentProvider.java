package com.agent1.javaagent.prompt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 桌面（macOS / Ubuntu）默认环境信息：平台与内核、QuickJS 运行环境、JVM 内存、工作区目录标注。
 * Android 宿主请注入自己的 {@link HostEnvironmentProvider} 实现。
 */
public final class DesktopEnvironmentProvider implements HostEnvironmentProvider {

    public static final DesktopEnvironmentProvider INSTANCE = new DesktopEnvironmentProvider();

    private static final Duration UNAME_TIMEOUT = Duration.ofSeconds(2);
    private static volatile String cachedKernel;

    private DesktopEnvironmentProvider() {
    }

    @Override
    public String platformLine() {
        String osName = System.getProperty("os.name", "unknown");
        String osVersion = System.getProperty("os.version", "");
        String platform = osName + (osVersion.isBlank() ? "" : " " + osVersion);
        String kernel = kernelSummary();
        return kernel.isBlank() ? platform : platform + " 内核 " + kernel;
    }

    @Override
    public String jsRuntimeLine() {
        return "QuickJS 扩展，非 Node；不能 npm 或 require";
    }

    @Override
    public String memoryLine() {
        long maxBytes = Runtime.getRuntime().maxMemory();
        if (maxBytes <= 0 || maxBytes == Long.MAX_VALUE) {
            return "";
        }
        return "JVM 内存上限 " + (maxBytes / (1024 * 1024)) + "MB";
    }

    @Override
    public List<String> directoryLines() {
        return List.of(
            "workspace/：本会话工作区（唯一可写）；外层文件工具用相对工作区根的路径（不要 workspace/ 前缀），脚本内 fs 可用相对或绝对（须在根下）",
            "shared/：公共能力库（只读），不能用 write_file 修改",
            "sessions/、logs/、sync/：系统目录（只读），文件工具不要写"
        );
    }

    /** {@code uname -sr}（如「Darwin 22.6.0」）；执行失败回退 os.name + os.version。结果缓存一次。 */
    private static String kernelSummary() {
        String cached = cachedKernel;
        if (cached != null) {
            return cached;
        }
        String kernel = readUname();
        if (kernel.isBlank()) {
            kernel = System.getProperty("os.name", "") + " " + System.getProperty("os.version", "");
            kernel = kernel.trim();
        }
        cachedKernel = kernel;
        return kernel;
    }

    private static String readUname() {
        try {
            Process process = new ProcessBuilder("uname", "-sr").redirectErrorStream(true).start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = process.getInputStream()) {
                in.transferTo(out);
            }
            if (!process.waitFor(UNAME_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return "";
            }
            return out.toString(StandardCharsets.UTF_8).trim();
        } catch (IOException ignored) {
            // uname 不可用（极少见）：回退 os.name + os.version
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    /** 供测试清除内核缓存。 */
    static void resetKernelCacheForTest() {
        cachedKernel = null;
    }
}