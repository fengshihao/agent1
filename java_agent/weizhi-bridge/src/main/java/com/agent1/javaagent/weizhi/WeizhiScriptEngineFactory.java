package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.catalog.AgentCatalogPaths;
import com.agent1.javaagent.script.ScriptEngine;
import com.agent1.javaagent.script.ScriptEngineFactory;
import java.nio.file.Path;

public final class WeizhiScriptEngineFactory implements ScriptEngineFactory {

    private final WeizhiRuntimeOptions options;
    private final Path agentRoot;

    public WeizhiScriptEngineFactory() {
        this(new WeizhiRuntimeOptions());
    }

    public WeizhiScriptEngineFactory(WeizhiRuntimeOptions options) {
        this(options, null);
    }

    /** agentRoot 非空时，每次 open 重新解析 {@code shared/catalog/native/<platform>}（7.3 apply 后同 Session 可用）。 */
    public WeizhiScriptEngineFactory(WeizhiRuntimeOptions options, Path agentRoot) {
        this.options = options == null ? new WeizhiRuntimeOptions() : options;
        this.agentRoot = agentRoot == null ? null : agentRoot.toAbsolutePath().normalize();
    }

    @Override
    public ScriptEngine open(Path workspace) {
        WeizhiRuntimeOptions effective = options;
        if (agentRoot != null) {
            effective = options.copy();
            Path nativeDir = AgentCatalogPaths.resolveExistingNativePluginsDir(agentRoot);
            if (nativeDir != null) {
                effective.nativePluginDir(nativeDir.toString());
            }
            try {
                // 无 classpath 预装：package 由 WeizhiPackageSync.beforeEval 从云端 registry 惰性安装
                effective.packageRoot(
                    java.nio.file.Files.createDirectories(
                        WeizhiBundledPackages.weizhiHome(agentRoot)).toAbsolutePath().toString());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("prepare weizhi-home failed", e);
            }
            effective.mcpAgentRoot(agentRoot);
            if (!effective.enableFetch()) {
                effective.enableFetch(true);
            }
        }
        return new WeizhiScriptEngine(workspace, effective);
    }
}
