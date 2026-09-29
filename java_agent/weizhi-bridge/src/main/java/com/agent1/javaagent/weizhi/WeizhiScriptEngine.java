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
        return evalForAgent(jsSource, null, timeoutMs, cancellationToken, null);
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
        String last = "";
        for (WeizhiScriptToolInstaller.EvalStep step : WeizhiScriptToolInstaller.evalSteps(
            userSource,
            agentArgsPrelude,
            options.scriptToolBridge(),
            workspaceRelativeFile
        )) {
            last = runOnce(step.source(), timeoutMs, cancellationToken, step.filename());
        }
        return last;
    }

    @Override
    public int agentHostPreludeLines() {
        // prelude 是单独的 runJs，QuickJS 行号已经对应用户脚本。
        return 0;
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
