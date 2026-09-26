package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.script.ScriptEngineFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

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
        String raw = System.getenv(ENV_SCRIPT_TIMEOUT_MS);
        if (raw == null || raw.isBlank()) {
            return 600_000L;
        }
        try {
            long v = Long.parseLong(raw.trim());
            return v > 0 ? v : 600_000L;
        } catch (NumberFormatException e) {
            return 600_000L;
        }
    }

    public static Optional<ScriptEngineFactory> tryCreateFactory(Path weizhiRepo, WeizhiRuntimeOptions options) {
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
        }
        return Optional.of(new WeizhiScriptEngineFactory(options));
    }

    public static String loadSandboxPromptAppend(Path weizhiRepo) {
        Path doc = weizhiRepo.resolve("docs/AGENT_SANDBOX_PROMPT.md");
        if (!Files.isRegularFile(doc)) {
            return "";
        }
        try {
            String text = Files.readString(doc);
            int marker = text.indexOf("## 系统提示（可复制）");
            if (marker < 0) {
                return text.trim();
            }
            int start = text.indexOf('\n', marker);
            int end = text.indexOf("---", start);
            if (end < 0) {
                end = text.indexOf("## 宿主侧备注", start);
            }
            if (end < 0) {
                end = text.length();
            }
            return text.substring(start, end).trim();
        } catch (Exception e) {
            return "";
        }
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
