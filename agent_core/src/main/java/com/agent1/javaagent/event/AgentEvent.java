package com.agent1.javaagent.event;

public final class AgentEvent {
    private final AgentEventType type;
    private final long timestampMs;
    private final Object payload;

    public AgentEvent(AgentEventType type, Object payload) {
        this.type = type;
        this.payload = payload;
        this.timestampMs = System.currentTimeMillis();
    }

    public AgentEventType getType() {
        return type;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public Object getPayload() {
        return payload;
    }
}
