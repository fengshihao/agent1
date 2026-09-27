package com.agent1.javaagent.tool.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.session.FileSessionStore;
import com.agent1.javaagent.session.SessionMeta;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class ListSessionsToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void listsSessionsAndMarksActive(@TempDir Path agentRoot) {
        AgentHomeBootstrap.ensure(agentRoot);
        FileSessionStore store = new FileSessionStore(agentRoot);
        SessionMeta a = store.createSession();
        SessionMeta b = store.createSession();
        var tool = new ListSessionsTool(store, () -> a.getSessionId());
        String text = tool.execute("l1", MAPPER.createObjectNode(), new CancellationToken(), u -> {}).getText();
        assertTrue(text.contains("sessions: 2"));
        assertTrue(text.contains("* " + a.getSessionId()));
        assertTrue(text.contains(b.getSessionId()));
        assertTrue(text.contains("active: " + a.getSessionId()));
    }
}
