package com.agent1.javaagent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.config.AgentRuntimeConfig;
import com.agent1.javaagent.core.AgentStateSnapshot;
import com.agent1.javaagent.llm.LlmClient;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.AssistantResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class ProductivityAgentHostAppendUserTest {

    @TempDir
    Path temp;

    @Test
    void appendUserMessageToTranscriptWithoutRun() {
        LlmClient fake = (request, tools, streamListener, cancellationToken) ->
            new AssistantResponse("x", List.of());
        AgentRuntimeConfig config = AgentRuntimeConfig.builder().apiKey("test-key").build();
        try (ProductivityAgentHost host = new ProductivityAgentHost(temp, config, fake)) {
            host.createSession();
            host.setSessionEnvironmentSupplement("- 测试 supplement");
            host.appendUserMessageToTranscript("[已添加附件] imports/x.pdf");

            AgentStateSnapshot snap = host.runtime().getStateSnapshot();
            assertEquals(1, snap.getMessages().size());
            assertEquals(AgentMessage.ROLE_USER, snap.getMessages().get(0).getRole());
            assertTrue(snap.getMessages().get(0).getContent().contains("imports/x.pdf"));
            assertTrue(snap.getSystemPrompt().contains("测试 supplement"));
        }
    }
}
