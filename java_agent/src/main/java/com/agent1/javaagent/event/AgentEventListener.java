package com.agent1.javaagent.event;

@FunctionalInterface
public interface AgentEventListener {
    void onEvent(AgentEvent event);
}
