package com.agent1.javaagent.script;

import java.util.Map;
import java.util.Set;

/**
 * 会话切换时由宿主替换委托。引擎在每次 {@code eval} 时读取当前名单。
 */
public final class MutableScriptToolBridge implements ScriptToolBridge {

    private volatile ScriptToolBridge delegate = ScriptToolBridges.empty();

    public void set(ScriptToolBridge delegate) {
        this.delegate = delegate == null ? ScriptToolBridges.empty() : delegate;
    }

    @Override
    public Set<String> exposedNames() {
        return delegate.exposedNames();
    }

    @Override
    public String call(String toolName, Map<String, Object> arguments) {
        return delegate.call(toolName, arguments);
    }
}
