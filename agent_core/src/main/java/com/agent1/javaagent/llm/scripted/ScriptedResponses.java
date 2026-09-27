package com.agent1.javaagent.llm.scripted;

import com.agent1.javaagent.model.AssistantResponse;
import com.agent1.javaagent.model.ToolCall;
import java.util.List;

/** 构造 {@link ScriptedLlmClient} 用的固定助手轮次。 */
public final class ScriptedResponses {

    private ScriptedResponses() {
    }

    public static AssistantResponse text(String content) {
        return new AssistantResponse(content, List.of());
    }

    public static AssistantResponse toolCall(String callId, String toolName, String argumentsJson) {
        return new AssistantResponse("", List.of(new ToolCall(callId, toolName, argumentsJson)));
    }

    public static AssistantResponse toolCall(String toolName, String argumentsJson) {
        return toolCall("call_" + toolName + "_1", toolName, argumentsJson);
    }
}
