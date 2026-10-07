package com.agent1.javaagent.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentAuditEventsTest {

    @Test
    void parseCoachHookIdAndAdvice() {
        String text = "tool ok\n\n---\n[coach] catalog.pending: 有 2 条待安装";
        assertEquals("catalog.pending", AgentAuditEvents.parseCoachHookId(text));
        assertTrue(AgentAuditEvents.parseCoachAdvice(text).contains("待安装"));
    }

    @Test
    void writesCoachFiredWithRunContext(@TempDir Path agentRoot) throws Exception {
        AgentHomeBootstrap.ensure(agentRoot);
        RunLogContext ctx = new RunLogContext("sess-a", "run-b", "", "main");
        AgentAuditEvents.coachFired(agentRoot, ctx, "script.fail_repeat", "run_js", "tc1", "retry hint");

        String line = Files.readString(AgentDataPaths.eventsJsonl(agentRoot)).trim();
        assertTrue(line.contains("\"type\":\"coach_fired\""));
        assertTrue(line.contains("\"sessionId\":\"sess-a\""));
        assertTrue(line.contains("\"runId\":\"run-b\""));
        assertTrue(line.contains("\"hook_id\":\"script.fail_repeat\""));
    }
}
