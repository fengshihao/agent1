package com.agent1.javaagent.asr.aliyun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;

public final class DashScopeProtocol {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DashScopeProtocol() {}

    public static String buildRunTaskMessageText(String taskId, int sampleRate, String model) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode header = root.putObject("header");
        header.put("action", "run-task");
        header.put("task_id", taskId);
        header.put("streaming", "duplex");

        ObjectNode payload = root.putObject("payload");
        payload.put("task_group", "audio");
        payload.put("task", "asr");
        payload.put("function", "recognition");
        payload.put("model", model);
        ObjectNode parameters = payload.putObject("parameters");
        parameters.put("format", "pcm");
        parameters.put("sample_rate", sampleRate);
        payload.putObject("input");
        return root.toString();
    }

    public static String buildFinishTaskMessageText(String taskId) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode header = root.putObject("header");
        header.put("action", "finish-task");
        header.put("task_id", taskId);
        header.put("streaming", "duplex");
        root.putObject("payload").putObject("input");
        return root.toString();
    }

    public static JsonNode parseJson(String text) throws IOException {
        return MAPPER.readTree(text);
    }

    public static String event(JsonNode root) {
        return root.path("header").path("event").asText("");
    }

    public static String sentenceText(JsonNode root) {
        return root.path("payload").path("output").path("sentence").path("text").asText("");
    }

    public static String errorMessage(JsonNode root) {
        return root.path("header").path("error_message").asText("语音识别失败");
    }
}
