package com.agent1.javaagent.weizhi;

import com.agent1.javaagent.script.ScriptEngine;
import com.agent1.javaagent.script.ScriptEngineFactory;
import java.nio.file.Path;

public final class WeizhiScriptEngineFactory implements ScriptEngineFactory {

    private final WeizhiRuntimeOptions options;

    public WeizhiScriptEngineFactory() {
        this(new WeizhiRuntimeOptions());
    }

    public WeizhiScriptEngineFactory(WeizhiRuntimeOptions options) {
        this.options = options == null ? new WeizhiRuntimeOptions() : options;
    }

    @Override
    public ScriptEngine open(Path workspace) {
        return new WeizhiScriptEngine(workspace, options);
    }
}
