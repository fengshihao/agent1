package com.agent1.javaagent.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ChatRequest {
    private final String model;
    private final List<AgentMessage> messages;

    public ChatRequest(String model, List<AgentMessage> messages) {
        this.model = Objects.requireNonNull(model, "model");
        this.messages = Collections.unmodifiableList(new ArrayList<>(messages == null ? List.of() : messages));
    }

    public String getModel() {
        return model;
    }

    public List<AgentMessage> getMessages() {
        return messages;
    }
}
