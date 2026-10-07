package com.agent1.javaagent.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.log.RunAuditScope;
import com.agent1.javaagent.log.RunLogContext;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentToolsScriptBridgeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static AgentTool recordingTool(AtomicReference<CancellationToken> seenToken, String text) {
        return new AgentTool() {
            @Override
            public String name() {
                return "probe";
            }

            @Override
            public String description() {
                return "probe";
            }

            @Override
            public JsonNode parametersSchema() {
                ObjectNode schema = MAPPER.createObjectNode();
                schema.put("type", "object");
                return schema;
            }

            @Override
            public ToolExecutionResult execute(
                String toolCallId,
                JsonNode parameters,
                CancellationToken cancellationToken,
                ToolUpdateListener onUpdate
            ) {
                seenToken.set(cancellationToken);
                return ToolExecutionResult.text(text);
            }
        };
    }

    @Test
    void call_usesRunLevelTokenFromContext() {
        AtomicReference<CancellationToken> seen = new AtomicReference<>();
        AgentToolsScriptBridge bridge = new AgentToolsScriptBridge(List.of(recordingTool(seen, "ok")));

        CancellationToken runToken = new CancellationToken();
        ScriptToolRunContext.bind(runToken);
        try {
            assertEquals("ok", bridge.call("probe", Map.of()));
        } finally {
            ScriptToolRunContext.clear();
        }
        assertNotNull(seen.get());
        assertEquals(runToken, seen.get(), "脚本内 $tools 调用必须复用当前 Run 的取消令牌");
    }

    @Test
    void call_withoutContext_fallsBackToFreshToken() {
        AtomicReference<CancellationToken> seen = new AtomicReference<>();
        AgentToolsScriptBridge bridge = new AgentToolsScriptBridge(List.of(recordingTool(seen, "ok")));

        assertEquals("ok", bridge.call("probe", Map.of()));
        assertNotNull(seen.get());
        assertFalse(seen.get().isCancelled());
    }

    @Test
    void call_writesAgentToolCallAuditEvent(@TempDir Path tmp) throws Exception {
        AtomicReference<CancellationToken> seen = new AtomicReference<>();
        AgentToolsScriptBridge bridge = new AgentToolsScriptBridge(List.of(recordingTool(seen, "done")));

        Path agentRoot = tmp.resolve("agent");
        Files.createDirectories(agentRoot.resolve("logs"));
        RunLogContext ctx = new RunLogContext("s1", "r1", "", "test");
        RunAuditScope.bind(agentRoot, ctx);
        ScriptToolRunContext.bind(new CancellationToken());
        try {
            assertEquals("done", bridge.call("probe", Map.of("path", "a.txt")));
        } finally {
            ScriptToolRunContext.clear();
            RunAuditScope.clear();
        }

        Path events = agentRoot.resolve("logs").resolve("events.jsonl");
        assertTrue(Files.isRegularFile(events), "events.jsonl 未生成");
        String content = Files.readString(events);
        assertTrue(content.contains("\"type\":\"agent_tool_call\"") || content.contains("\"type\": \"agent_tool_call\""),
            content);
        assertTrue(content.contains("probe"), content);
        assertTrue(content.contains("a.txt"), content);
    }

    @Test
    void call_failure_writesErrorAuditEvent(@TempDir Path tmp) throws Exception {
        AgentTool failing = new AgentTool() {
            @Override
            public String name() {
                return "boom";
            }

            @Override
            public String description() {
                return "boom";
            }

            @Override
            public JsonNode parametersSchema() {
                ObjectNode schema = MAPPER.createObjectNode();
                schema.put("type", "object");
                return schema;
            }

            @Override
            public ToolExecutionResult execute(
                String toolCallId,
                JsonNode parameters,
                CancellationToken cancellationToken,
                ToolUpdateListener onUpdate
            ) {
                throw new IllegalStateException("network unreachable");
            }
        };
        AgentToolsScriptBridge bridge = new AgentToolsScriptBridge(List.of(failing));

        Path agentRoot = tmp.resolve("agent");
        Files.createDirectories(agentRoot.resolve("logs"));
        RunAuditScope.bind(agentRoot, new RunLogContext("s1", "r2", "", "test"));
        try {
            try {
                bridge.call("boom", Map.of());
            } catch (IllegalArgumentException expected) {
                // 预期失败
            }
        } finally {
            RunAuditScope.clear();
        }

        String content = Files.readString(agentRoot.resolve("logs").resolve("events.jsonl"));
        assertTrue(content.contains("agent_tool_call"), content);
        assertTrue(content.contains("network unreachable"), content);
        assertTrue(content.contains("\"is_error\":true") || content.contains("\"is_error\": true"), content);
    }
}