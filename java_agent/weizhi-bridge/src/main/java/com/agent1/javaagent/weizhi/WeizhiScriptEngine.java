package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.script.ScriptEngine;
import com.weizhi.WeizhiEngine;
import java.nio.file.Path;

final class WeizhiScriptEngine implements ScriptEngine {

    private final WeizhiEngine engine;

    WeizhiScriptEngine(Path workspace, WeizhiRuntimeOptions options) {
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
    }

    @Override
    public String eval(String jsSource, long timeoutMs, CancellationToken cancellationToken) {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            throw new RuntimeException("cancelled");
        }
        int timeout = timeoutMs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) timeoutMs;
        return engine.runJs(jsSource, timeout);
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
