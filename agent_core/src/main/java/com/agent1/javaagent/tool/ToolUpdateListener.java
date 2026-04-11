package com.agent1.javaagent.tool;

@FunctionalInterface
public interface ToolUpdateListener {
    void onUpdate(ToolExecutionUpdate update);
}
