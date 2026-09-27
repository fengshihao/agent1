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
            Path scriptFolder = AgentCatalogPaths.resolveCatalogScriptFolder(agentRoot);
            if (scriptFolder != null) {
                effective.scriptFolder(scriptFolder.toString());
            }
        }
        return new WeizhiScriptEngine(workspace, effective);
    }
}
