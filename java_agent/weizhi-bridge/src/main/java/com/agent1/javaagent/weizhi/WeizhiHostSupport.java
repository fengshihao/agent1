package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.script.ScriptEngineFactory;
import com.agent1.javaagent.tool.script.ExecuteScriptTool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 从环境变量解析 Weizhi 仓库路径并尝试启用脚本引擎。 */
public final class WeizhiHostSupport {

    public static final String ENV_WEIZHI_REPO = "AGENT1_WEIZHI_REPO";
    public static final String ENV_SCRIPT_ENGINE = "AGENT1_SCRIPT_ENGINE";
    public static final String ENV_SCRIPT_TIMEOUT_MS = "AGENT1_SCRIPT_TIMEOUT_MS";

    private WeizhiHostSupport() {
    }

    public static Path defaultWeizhiRepo() {
        String env = System.getenv(ENV_WEIZHI_REPO);
        if (env != null && !env.isBlank()) {
            return Path.of(env.trim());
        }
        Path sibling = Path.of(System.getProperty("user.dir", ".")).resolve("../../../weizhi").normalize();
        if (!Files.isDirectory(sibling)) {
            sibling = Path.of(System.getProperty("user.dir", ".")).resolve("../weizhi").normalize();
        }
        return sibling.toAbsolutePath().normalize();
    }

    public static boolean scriptEngineEnabledByEnv() {
        String mode = System.getenv(ENV_SCRIPT_ENGINE);
        if (mode == null || mode.isBlank()) {
            return true;
        }
        return !"off".equalsIgnoreCase(mode.trim()) && !"none".equalsIgnoreCase(mode.trim());
    }

    public static long scriptTimeoutMs() {
        long cap = ExecuteScriptTool.MAX_TIMEOUT_MS;
        String raw = System.getenv(ENV_SCRIPT_TIMEOUT_MS);
        if (raw == null || raw.isBlank()) {
            return ExecuteScriptTool.DEFAULT_TIMEOUT_MS;
        }
        try {
            long v = Long.parseLong(raw.trim());
            if (v <= 0) {
                return ExecuteScriptTool.DEFAULT_TIMEOUT_MS;
            }
            return Math.min(v, cap);
        } catch (NumberFormatException e) {
            return ExecuteScriptTool.DEFAULT_TIMEOUT_MS;
        }
    }

    public static Optional<ScriptEngineFactory> tryCreateFactory(Path weizhiRepo, WeizhiRuntimeOptions options) {
        return tryCreateFactory(weizhiRepo, options, null);
    }

    public static Optional<ScriptEngineFactory> tryCreateFactory(
        Path weizhiRepo,
        WeizhiRuntimeOptions options,
        Path agentRoot
    ) {
        if (!scriptEngineEnabledByEnv()) {
            return Optional.empty();
        }
        if (!WeizhiJniBootstrap.tryLoad(weizhiRepo)) {
            return Optional.empty();
        }
        try {
            Class.forName("com.weizhi.WeizhiEngine");
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        } catch (UnsatisfiedLinkError e) {
            return Optional.empty();
        }
        WeizhiPackageSync.refreshIndexAsync(agentRoot);
        installBundledAsync(agentRoot);
        return Optional.of(new WeizhiScriptEngineFactory(options, agentRoot));
    }

    /**
     * 启动时后台装好内置包并刷随包 skill 卡，让第一次 run_js 之前的 find_caps 也能命中
     * 包内 skill。engine open 时 {@code WeizhiBundledPackages} 还会幂等重装。
     */
    private static void installBundledAsync(Path agentRoot) {
        if (agentRoot == null) {
            return;
        }
        Thread thread = new Thread(
            () -> {
                try {
                    WeizhiBundledPackages.syncFromClasspath(agentRoot);
                } catch (IOException | RuntimeException e) {
                    Logger.getLogger(WeizhiHostSupport.class.getName())
                        .log(Level.WARNING, "install bundled packages failed", e);
                }
            },
            "weizhi-bundled-install"
        );
        thread.setDaemon(true);
        thread.start();
    }

    public static String platformLabel() {
        String os = System.getProperty("os.name", "unknown").toLowerCase(Locale.US);
        if (os.contains("mac")) {
            return "mac";
        }
        if (os.contains("linux")) {
            return "linux";
        }
        return os;
    }
}
