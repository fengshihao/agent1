package com.agent1.javaagent.asr;

import java.util.Objects;

public final class AsrStartRequest {
    private final String taskId;
    private final int sampleRate;
    private final String model;
    private final String apiKey;

    public AsrStartRequest(String taskId, int sampleRate, String model, String apiKey) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.sampleRate = sampleRate;
        this.model = Objects.requireNonNull(model, "model");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
    }

    public String getTaskId() {
        return taskId;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public String getModel() {
        return model;
    }

    public String getApiKey() {
        return apiKey;
    }
}
