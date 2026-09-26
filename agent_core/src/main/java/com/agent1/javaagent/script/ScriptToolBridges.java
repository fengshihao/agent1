package com.agent1.javaagent.script;

import java.util.Map;
import java.util.Set;

public final class ScriptToolBridges {

    private static final ScriptToolBridge EMPTY = new ScriptToolBridge() {
        @Override
        public Set<String> exposedNames() {
            return Set.of();
        }

        @Override
        public String call(String toolName, Map<String, Object> arguments) {
            throw new IllegalArgumentException("unsupported: $tools." + toolName);
        }
    };

    private ScriptToolBridges() {
    }

    public static ScriptToolBridge empty() {
        return EMPTY;
    }
}
