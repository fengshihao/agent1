package com.agent1.javaagent.session;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.log.RunLogContext;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.run.FileRunStore;
import com.agent1.javaagent.run.RunRecord;
import com.agent1.javaagent.run.RunState;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionSummaryServiceTest {

    @TempDir
    Path agentRoot;

    @Test
    void writesRuleBasedSummary() throws Exception {
        FileSessionStore sessions = new FileSessionStore(agentRoot);
        SessionMeta meta = sessions.createSession();
        sessions.appendMessage(meta.getSessionId(), "run1", AgentMessage.user("整理 staging"));
        sessions.appendMessage(meta.getSessionId(), "run1", AgentMessage.assistant("好的", java.util.List.of()));

        FileRunStore runs = new FileRunStore(sessions);
        RunRecord record = new RunRecord(
            "run1abc",
            meta.getSessionId(),
            RunState.SUCCEEDED,
            "2026-01-01T00:00:00Z",
            "2026-01-01T00:01:00Z",
            null
        );
        runs.write(record);

        SessionSummaryService service = new SessionSummaryService(agentRoot);
        Path out = service.writeSummary(meta.getSessionId(), new RunLogContext("test", "t1", "", "test"));

        assertTrue(Files.isRegularFile(out));
        String text = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(text.contains("整理 staging"));
        assertTrue(text.contains("run1abc"));
        assertTrue(text.contains(meta.getSessionId()));
    }
}
