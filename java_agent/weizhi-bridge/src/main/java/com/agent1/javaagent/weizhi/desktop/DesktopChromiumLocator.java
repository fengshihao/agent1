package com.agent1.javaagent.weizhi.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** 解析本机 Headless Chromium / Chrome 可执行文件。 */
public final class DesktopChromiumLocator {

    public static final String ENV_CHROMIUM_PATH = "AGENT1_CHROMIUM_PATH";
    public static final String ENV_WEBVIEW = "AGENT1_WEBVIEW";

    private DesktopChromiumLocator() {
    }

    public static boolean enabledByEnvironment() {
        String mode = System.getenv(ENV_WEBVIEW);
        if (mode == null || mode.isBlank()) {
            return true;
        }
        String t = mode.trim().toLowerCase(Locale.US);
        return !"off".equals(t) && !"none".equals(t) && !"false".equals(t);
    }

    public static Optional<Path> resolveExecutable() {
        if (!enabledByEnvironment()) {
            return Optional.empty();
        }
        String fromEnv = System.getenv(ENV_CHROMIUM_PATH);
        if (fromEnv != null && !fromEnv.isBlank()) {
            Path p = Path.of(fromEnv.trim());
            if (Files.isExecutable(p)) {
                return Optional.of(p.toAbsolutePath().normalize());
            }
            return Optional.empty();
        }
        List<String> candidates = List.of(
            "google-chrome-stable",
            "google-chrome",
            "chromium-browser",
            "chromium",
            "chrome"
        );
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return Optional.empty();
        }
        for (String dir : pathEnv.split(":")) {
            if (dir.isBlank()) {
                continue;
            }
            Path base = Path.of(dir);
            for (String name : candidates) {
                Path exe = base.resolve(name);
                if (Files.isExecutable(exe)) {
                    return Optional.of(exe.toAbsolutePath().normalize());
                }
            }
        }
        return Optional.empty();
    }
}
