package com.agent1.javaagent.core;

import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.tool.AgentTool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AgentState {
    private String systemPrompt;
    private String model;
    private final Map<String, AgentTool> toolsByName = new LinkedHashMap<>();
    private final List<AgentMessage> messages = new ArrayList<>();
    private boolean streaming;
    private AgentMessage streamMessage;
    private final Set<String> pendingToolCalls = new LinkedHashSet<>();
    private String error;

    public AgentState(String systemPrompt, String model, List<AgentTool> tools, List<AgentMessage> initialMessages) {
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
        this.model = model;
        setTools(tools == null ? List.of() : tools);
        this.messages.addAll(initialMessages == null ? List.of() : initialMessages);
    }

    public synchronized void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
    }

    public synchronized void setModel(String model) {
        this.model = model;
    }

    public synchronized void setTools(List<AgentTool> tools) {
        toolsByName.clear();
        for (AgentTool tool : tools) {
            toolsByName.put(tool.name(), tool);
        }
    }

    public synchronized AgentTool getTool(String name) {
        return toolsByName.get(name);
    }

    public synchronized List<AgentTool> getTools() {
        return List.copyOf(toolsByName.values());
    }

    public synchronized void appendMessage(AgentMessage message) {
        messages.add(message);
    }

    public synchronized void replaceMessages(List<AgentMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
    }

    public synchronized List<AgentMessage> getMessages() {
        return List.copyOf(messages);
    }

    public synchronized String getSystemPrompt() {
        return systemPrompt;
    }

    public synchronized String getModel() {
        return model;
    }

    public synchronized void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }

    public synchronized boolean isStreaming() {
        return streaming;
    }

    public synchronized AgentMessage getStreamMessage() {
        return streamMessage;
    }

    public synchronized void setStreamMessage(AgentMessage streamMessage) {
        this.streamMessage = streamMessage;
    }

    public synchronized void clearPendingToolCalls() {
        pendingToolCalls.clear();
    }

    public synchronized void addPendingToolCall(String toolCallId) {
        pendingToolCalls.add(toolCallId);
    }

    public synchronized void removePendingToolCall(String toolCallId) {
        pendingToolCalls.remove(toolCallId);
    }

    public synchronized Set<String> getPendingToolCalls() {
        return Set.copyOf(pendingToolCalls);
    }

    public synchronized void setError(String error) {
        this.error = error;
    }

    public synchronized String getError() {
        return error;
    }

    public synchronized AgentStateSnapshot snapshot() {
        return new AgentStateSnapshot(systemPrompt, model, messages, streaming, streamMessage, pendingToolCalls, error);
    }
}
