package com.agent1.javaagent.core;

import com.agent1.javaagent.model.AgentMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class AgentStateSnapshot {
    private final String systemPrompt;
    private final String model;
    private final List<AgentMessage> messages;
    private final boolean streaming;
    private final AgentMessage streamMessage;
    private final Set<String> pendingToolCalls;
    private final String error;

    public AgentStateSnapshot(
        String systemPrompt,
        String model,
        List<AgentMessage> messages,
        boolean streaming,
        AgentMessage streamMessage,
        Set<String> pendingToolCalls,
        String error
    ) {
        this.systemPrompt = systemPrompt;
        this.model = model;
        this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
        this.streaming = streaming;
        this.streamMessage = streamMessage;
        this.pendingToolCalls = Set.copyOf(pendingToolCalls);
        this.error = error;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getModel() {
        return model;
    }

    public List<AgentMessage> getMessages() {
        return messages;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public AgentMessage getStreamMessage() {
        return streamMessage;
    }

    public Set<String> getPendingToolCalls() {
        return pendingToolCalls;
    }

    public String getError() {
        return error;
    }
}
