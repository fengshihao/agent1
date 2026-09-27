package com.agent1.javaagent.llm.scripted;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.agent1.javaagent.core.AgentOptions;
import com.agent1.javaagent.core.AgentRuntime;
import com.agent1.javaagent.core.AgentStateSnapshot;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.ToolCall;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScriptedLlmClientTest {

    @Test
    void sequenceDrivesToolLoopWithoutNetwork() {
        ScriptedLlmClient client = ScriptedLlmClient.sequence(
            ScriptedResponses.toolCall("echo", "{\"text\":\"hi\"}"),
            ScriptedResponses.text("完成")
        );

        AgentRuntime runtime = new AgentRuntime(
            AgentOptions.builder("mock-model").build(),
            client
        );
        runtime.setTools(List.of(new com.agent1.javaagent.tool.DelegatingAgentTool(
            "echo",
            "echo",
            null,
            (params, token) -> "pong"
        )));

        runtime.prompt("任意用户话").join();
        AgentStateSnapshot snapshot = runtime.getStateSnapshot();
        assertEquals(AgentMessage.ROLE_ASSISTANT, snapshot.getMessages().get(3).getRole());
        assertEquals("完成", snapshot.getMessages().get(3).getContent());
        runtime.close();
    }

    @Test
    void taskScriptMatchesUserMessageSubstring() {
        ScriptedLlmClient client = ScriptedLlmClient.builder()
            .whenUserMessageContains(
                "画圆",
                ScriptedResponses.toolCall("draw", "{\"x\":1}"),
                ScriptedResponses.text("画好了")
            )
            .build();

        ToolCall call = client.streamChat(
            new com.agent1.javaagent.model.ChatRequest(
                "mock",
                List.of(AgentMessage.user("请画圆"))
            ),
            List.of(),
            delta -> {
            },
            new com.agent1.javaagent.core.CancellationToken()
        ).getToolCalls().get(0);

        assertEquals("draw", call.getName());
        assertEquals("画好了", client.streamChat(
            new com.agent1.javaagent.model.ChatRequest(
                "mock",
                List.of(
                    AgentMessage.user("请画圆"),
                    AgentMessage.toolResult("call_draw_1", "ok", false)
                )
            ),
            List.of(),
            delta -> {
            },
            new com.agent1.javaagent.core.CancellationToken()
        ).getContent());
    }

    @Test
    void toolResultBranchTakesPriorityOverUserTaskQueue() throws Exception {
        ScriptedLlmClient client = ScriptedLlmClient.builder()
            .whenUserMessageContains(
                "task-a",
                ScriptedResponses.toolCall("t1", "{}"),
                ScriptedResponses.text("错误：不应在用户队列取成功文案")
            )
            .whenToolResultFailed(ScriptedResponses.text("已识别工具失败"))
            .build();

        client.streamChat(
            new com.agent1.javaagent.model.ChatRequest("m", List.of(AgentMessage.user("task-a"))),
            List.of(),
            d -> {
            },
            new com.agent1.javaagent.core.CancellationToken()
        );
        String reply = client.streamChat(
            new com.agent1.javaagent.model.ChatRequest(
                "m",
                List.of(
                    AgentMessage.user("task-a"),
                    AgentMessage.toolResult("call_t1_1", "{\"ok\":false,\"error\":\"x\"}", false)
                )
            ),
            List.of(),
            d -> {
            },
            new com.agent1.javaagent.core.CancellationToken()
        ).getContent();
        assertEquals("已识别工具失败", reply);
    }

    @Test
    void throwsWhenScriptExhausted() {
        ScriptedLlmClient client = ScriptedLlmClient.sequence(ScriptedResponses.text("once"));
        client.streamChat(
            new com.agent1.javaagent.model.ChatRequest("m", List.of(AgentMessage.user("a"))),
            List.of(),
            d -> {
            },
            new com.agent1.javaagent.core.CancellationToken()
        );
        assertThrows(IllegalStateException.class, () -> client.streamChat(
            new com.agent1.javaagent.model.ChatRequest("m", List.of(AgentMessage.user("b"))),
            List.of(),
            d -> {
            },
            new com.agent1.javaagent.core.CancellationToken()
        ));
    }
}
