package com.agent1.javaagent.llm;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.model.AssistantResponse;
import com.agent1.javaagent.model.ChatRequest;
import com.agent1.javaagent.tool.AgentTool;
import java.util.List;

public interface LlmClient {
    AssistantResponse streamChat(
        ChatRequest request,
        List<AgentTool> tools,
        LlmStreamListener streamListener,
        CancellationToken cancellationToken
    ) throws Exception;

    default void close() throws Exception {
        // optional cleanup
    }
}
