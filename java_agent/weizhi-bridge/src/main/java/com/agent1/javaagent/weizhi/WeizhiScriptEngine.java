package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.script.ScriptEngine;
import com.weizhi.WeizhiEngine;
import java.nio.file.Path;

final class WeizhiScriptEngine implements ScriptEngine {

    private final WeizhiRuntimeOptions options;
    private final WeizhiEngine engine;

    WeizhiScriptEngine(Path workspace, WeizhiRuntimeOptions options) {
        this.options = options;
        this.engine = new WeizhiEngine(options.limits());
        engine.setFsRoot(workspace.toAbsolutePath().normalize().toString());
        if (options.scriptFolder() != null && !options.scriptFolder().isBlank()) {
            engine.setScriptFolder(options.scriptFolder());
        }
        if (options.enableFetch()) {
            if (options.fetchHostAllowlist() == null) {
                engine.enableFetch();
            } else {
                engine.enableFetch(options.fetchHostAllowlist());
            }
        }
        if (options.enableNativeMock()) {
            engine.enableNativeMock();
        }
        if (options.nativePluginDir() != null && !options.nativePluginDir().isBlank()) {
            engine.enableNativePlugins(options.nativePluginDir());
        }
        if (options.installDesktopCaps()) {
            try {
                com.weizhi.desktop.DesktopCaps.install(
                    engine,
                    workspace,
                    message -> true
                );
            } catch (Exception e) {
                throw new IllegalStateException("DesktopCaps.install failed", e);
            }
        }
        WeizhiScriptToolInstaller.install(engine, options.scriptToolBridge());
    }

    @Override
    public String eval(String jsSource, long timeoutMs, CancellationToken cancellationToken) {
        return runOnce(jsSource, timeoutMs, cancellationToken, null);
    }

    @Override
    public String evalForAgent(
        String userSource,
        String agentArgsPrelude,
        long timeoutMs,
        CancellationToken cancellationToken,
        String workspaceRelativeFile
    ) {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            throw new RuntimeException("cancelled");
        }
        String safeUser = userSource == null ? "" : userSource;
        if (agentArgsPrelude != null && !agentArgsPrelude.isBlank()) {
            runOnce(agentArgsPrelude, timeoutMs, cancellationToken, "<agent-args>");
        }
        String toolsPrelude = WeizhiScriptToolInstaller.preludeSource(options.scriptToolBridge());
        if (!toolsPrelude.isBlank()) {
            runOnce(toolsPrelude, timeoutMs, cancellationToken, "<tools-prelude>");
        }
        String filename = workspaceRelativeFile == null || workspaceRelativeFile.isBlank()
            ? "<eval>"
            : workspaceRelativeFile.trim();
        return runOnce(safeUser, timeoutMs, cancellationToken, filename);
    }

    @Override
    public int agentHostPreludeLines() {
        return WeizhiScriptToolInstaller.preludeLineCount(options.scriptToolBridge());
    }

    private String runOnce(String source, long timeoutMs, CancellationToken cancellationToken, String filename) {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            throw new RuntimeException("cancelled");
        }
        int timeout = timeoutMs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) timeoutMs;
        return engine.runJs(source, timeout, filename);
    }

    @Override
    public void cancel() {
        engine.cancel();
    }

    @Override
    public void close() {
        engine.close();
    }
}
