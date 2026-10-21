package com.agent1.javaagent.cli.ui;

import com.agent1.javaagent.catalog.WebLibRegistrySync;
import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.script.MutableScriptToolBridge;
import com.agent1.javaagent.script.ScriptEngineFactory;
import com.agent1.javaagent.script.ScriptToolBridge;
import com.agent1.javaagent.weizhi.WeizhiHostSupport;
import com.agent1.javaagent.weizhi.WeizhiRuntimeOptions;
import com.agent1.javaagent.weizhi.WeizhiWorkspaceTools;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

/** {@code ./agent1 ui}：启动本机 Web UI 并打开系统浏览器。 */
public final class ProductivityUiCommand {

    public static final int DEFAULT_PORT = 17831;

    private ProductivityUiCommand() {
    }

    public static int run(Path agentRoot, String[] args) throws IOException {
        int port = resolvePort(args);
        AgentRuntimeConfig config = UiConfigStore.mergeWithEnvironment(agentRoot);

        Path weizhiRepo = WeizhiHostSupport.defaultWeizhiRepo();
        MutableScriptToolBridge scriptTools = new MutableScriptToolBridge();
        WeizhiRuntimeOptions weizhiOptions = new WeizhiRuntimeOptions()
            .installDesktopCaps(true)
            .scriptToolBridge(scriptTools);
        Optional<ScriptEngineFactory> scriptEngine =
            WeizhiHostSupport.tryCreateFactory(weizhiRepo, weizhiOptions, agentRoot);
        ScriptToolBridge bridge = scriptEngine.isPresent() ? scriptTools : null;
        Path projectRoot = com.agent1.javaagent.log.AgentDataPaths.projectRoot(agentRoot);

        DesktopUiGateway gateway = DesktopUiGateway.createDefault(
            agentRoot,
            config,
            scriptEngine.orElse(null),
            WeizhiHostSupport.scriptTimeoutMs(),
            bridge,
            WeizhiWorkspaceTools.provider(agentRoot, projectRoot)
        );
        WebLibRegistrySync.refreshIndexAsync(agentRoot);

        final UiHttpServer http;
        try {
            http = new UiHttpServer(gateway, port);
        } catch (IOException bindError) {
            System.err.println(
                "无法绑定 127.0.0.1:" + port + "（端口可能被占用）。可换端口：./agent1 ui --port <port> 或 AGENT1_UI_PORT");
            throw bindError;
        }
        try (gateway; http) {
            if (gateway.listSessions().isEmpty()) {
                gateway.createSession();
            } else if (gateway.getActiveSessionId() == null) {
                gateway.switchSession(gateway.listSessions().get(0).getSessionId());
            }

            http.start();
            String url = http.baseUrl();
            System.out.println("Agent1 Web UI: " + url);
            System.out.println("数据目录: " + agentRoot);
            if (http.servesWebUiFromDisk()) {
                System.out.println(
                    "前端: 从仓库 web-ui 目录提供（npm run build 后刷新浏览器即可；设 AGENT1_WEB_UI_DEV=0 改用 jar 内资源）");
            } else {
                System.out.println(
                    "前端: jar/classpath 内资源（改前端需 npm run build 并重启 ui；或设 AGENT1_WEB_UI_ROOT 指向 web-ui 目录）");
            }
            if (!config.isModelConfigured()) {
                System.out.println("提示: 未检测到 API Key，请在浏览器设置页填写（将写入 " + UiConfigStore.configPath(agentRoot) + "）");
            }
            openBrowser(url);

            CountDownLatch latch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(latch::countDown));
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return 0;
    }

    static int resolvePort(String[] args) {
        String env = System.getenv("AGENT1_UI_PORT");
        if (env != null && !env.isBlank()) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if ("--port".equals(args[i]) && i + 1 < args.length) {
                    return Integer.parseInt(args[i + 1].trim());
                }
            }
        }
        return DEFAULT_PORT;
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {
            // fall through
        }
        // macOS / Windows / Linux 常见回退
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception e) {
            System.out.println("无法自动打开浏览器，请手动访问: " + url);
        }
    }
}
