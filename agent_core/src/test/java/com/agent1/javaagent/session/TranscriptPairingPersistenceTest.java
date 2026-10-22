package com.agent1.javaagent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.ToolCall;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class TranscriptPairingPersistenceTest {

    @TempDir
    Path temp;

    @Test
    void loadRepairedRewritesTranscriptWhenPairingBroken() {
        FileSessionStore store = new FileSessionStore(temp);
        SessionMeta meta = store.createSession();
        store.appendMessage(
            meta.getSessionId(),
            "r1",
            AgentMessage.assistant("", List.of(new ToolCall("tc1", "edit_file", "{}")))
        );
        store.appendMessage(
            meta.getSessionId(),
            "r1",
            AgentMessage.assistant("错误: 工具调用次数超过上限", List.of())
        );

        List<AgentMessage> repaired = TranscriptPairingPersistence.loadRepaired(store, meta.getSessionId());
        assertEquals(3, repaired.size());
        assertEquals(AgentMessage.ROLE_TOOL_RESULT, repaired.get(1).getRole());
        assertEquals("tc1", repaired.get(1).getToolCallId());
        assertTrue(repaired.get(1).isError());

        List<AgentMessage> secondLoad = store.loadTranscript(meta.getSessionId());
        assertEquals(3, secondLoad.size());
        assertEquals("tc1", secondLoad.get(1).getToolCallId());
    }
}
