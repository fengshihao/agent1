package com.agent1.javaagent.tool.agent;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.session.FileSessionStore;
import com.agent1.javaagent.session.SessionMeta;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/** 列出 agentRoot 下所有 Session 元数据（只读；跨 Session 自省 API 初版）。 */
public final class ListSessionsTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SUMMARY_NAME = "session.summary.md";

    private final FileSessionStore sessionStore;
    private final Supplier<String> activeSessionId;

    public ListSessionsTool(FileSessionStore sessionStore, Supplier<String> activeSessionId) {
        this.sessionStore = sessionStore;
        this.activeSessionId = activeSessionId;
    }

    @Override
    public String name() {
        return "list_sessions";
    }

    @Override
    public String description() {
        return "List all sessions under agentRoot (id, title, updatedAt). "
            + "Marks the active session. Does not read full transcripts.";
    }

    @Override
    public JsonNode parametersSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", MAPPER.createObjectNode());
        return schema;
    }

    @Override
    public ToolExecutionResult execute(
        String toolCallId,
        JsonNode parameters,
        CancellationToken cancellationToken,
        ToolUpdateListener onUpdate
    ) {
        if (cancellationToken.isCancelled()) {
            return ToolExecutionResult.text("错误：执行已取消");
        }
        String active = activeSessionId.get();
        List<SessionMeta> sessions = sessionStore.listSessions();
        StringBuilder out = new StringBuilder();
        out.append("sessions: ").append(sessions.size()).append('\n');
        for (SessionMeta meta : sessions) {
            boolean isActive = meta.getSessionId().equals(active);
            out.append(isActive ? "* " : "  ");
            out.append(meta.getSessionId())
                .append(" | ")
                .append(meta.getTitle())
                .append(" | updated ")
                .append(meta.getUpdatedAt());
            Path summary = sessionStore.sessionDir(meta.getSessionId()).resolve(SUMMARY_NAME);
            if (Files.isRegularFile(summary)) {
                out.append(" | summary=yes");
            }
            out.append('\n');
        }
        out.append("active: ").append(active == null ? "(none)" : active);
        return ToolExecutionResult.text(out.toString().trim());
    }
}
