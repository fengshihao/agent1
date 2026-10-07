package com.agent1.javaagent.log;

import com.agent1.javaagent.catalog.sync.CatalogSyncService;
import com.agent1.javaagent.promote.PromotionScanner;
import com.agent1.javaagent.todo.TodoList;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** P.4：catalog / promote / coach 审计事件（追加 {@code logs/events.jsonl}）。 */
public final class AgentAuditEvents {

    private static final int ADVICE_MAX = 240;

    private AgentAuditEvents() {
    }

    public static void catalogSyncChecked(
        Path agentRoot,
        RunLogContext context,
        CatalogSyncService.SyncCheckResult result,
        String source
    ) {
        if (agentRoot == null || result == null) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("source", source == null ? "" : source);
        fields.put("manifest_url", result.manifestUrl());
        fields.put("catalog_id", result.catalogId());
        fields.put("pending_count", result.pending().size());
        fields.put("pending_by_kind", result.pendingByKind());
        fields.put(
            "pending_ids",
            result.pending().stream().map(p -> p.item().id()).limit(64).toList()
        );
        write(agentRoot, context, "catalog_sync_checked", fields);
    }

    public static void catalogSyncCompleted(
        Path agentRoot,
        RunLogContext context,
        CatalogSyncService.SyncApplyResult result,
        String source
    ) {
        if (agentRoot == null || result == null) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("source", source == null ? "" : source);
        fields.put("manifest_url", result.manifestUrl());
        fields.put("applied_ids", result.appliedIds());
        fields.put("errors", result.errors());
        write(agentRoot, context, "catalog_sync_completed", fields);
    }

    public static void catalogNativeAutoInstalled(
        Path agentRoot,
        RunLogContext context,
        String pluginName,
        List<String> appliedIds
    ) {
        if (agentRoot == null || pluginName == null || pluginName.isBlank()) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("source", "execute_script_auto_native");
        fields.put("plugin_name", pluginName.trim());
        fields.put("applied_ids", appliedIds == null ? List.of() : appliedIds);
        write(agentRoot, context, "catalog_sync_completed", fields);
    }

    public static void promotionCompleted(
        Path agentRoot,
        RunLogContext context,
        List<String> items,
        String note,
        String workspace
    ) {
        writePromotion(agentRoot, context, "promotion_completed", items, note, workspace);
    }

    public static void promotionRejected(
        Path agentRoot,
        RunLogContext context,
        List<String> rejections,
        String note,
        String workspace
    ) {
        writePromotion(agentRoot, context, "promotion_rejected", rejections, note, workspace);
    }

    private static void writePromotion(
        Path agentRoot,
        RunLogContext context,
        String type,
        List<String> items,
        String note,
        String workspace
    ) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("items", items == null ? List.of() : items);
        fields.put("note", note == null ? "" : note);
        fields.put("workspace", workspace == null ? "" : workspace);
        write(agentRoot, context, type, fields);
    }

    public static void sessionSummaryWritten(
        Path agentRoot,
        RunLogContext context,
        String sessionId,
        Path summaryPath,
        PromotionScanner.ScanResult staging
    ) {
        if (agentRoot == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("session_id", sessionId.trim());
        fields.put("summary_path", summaryPath == null ? "" : summaryPath.toString());
        if (staging != null) {
            fields.put("staging_skill_count", staging.skills().size());
            fields.put("staging_script_count", staging.scripts().size());
            fields.put("staging_rejection_count", staging.rejections().size());
        }
        write(agentRoot, context, "session_summary_written", fields);
    }

    /** 会话清单整表替换后的快照。状态未变时不写。 */
    public static void todoUpdated(Path agentRoot, RunLogContext context, TodoList list) {
        if (list == null) {
            return;
        }
        Path root = resolveAgentRoot(agentRoot);
        Map<String, Object> fields = new LinkedHashMap<>();
        List<Map<String, String>> todos = new ArrayList<>();
        for (TodoList.Item item : list.items()) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("id", item.id());
            row.put("content", item.content());
            row.put("status", item.status());
            todos.add(row);
        }
        fields.put("todos", todos);
        fields.put("open_count", list.openCount());
        write(root, context, "todo_updated", fields);
    }

    public static void coachFired(
        Path agentRoot,
        RunLogContext context,
        String hookId,
        String toolName,
        String toolCallId,
        String advice
    ) {
        if (agentRoot == null || hookId == null || hookId.isBlank()) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("hook_id", hookId.trim());
        fields.put("tool_name", toolName == null ? "" : toolName);
        fields.put("tool_call_id", toolCallId == null ? "" : toolCallId);
        fields.put("advice", truncate(advice, ADVICE_MAX));
        write(agentRoot, context, "coach_fired", fields);
    }

    /**
     * 脚本内 {@code $tools.*} 调用的审计事件（与外层 tool_call/tool_result 对应）。
     * 外层只记 execute_script 一条整体事件；不记这个的话脚本内部行为不可回放。
     */
    public static void agentToolCall(
        Path agentRoot,
        RunLogContext context,
        String toolName,
        String argsSummary,
        boolean ok,
        long durationMs,
        String error
    ) {
        if (agentRoot == null || toolName == null || toolName.isBlank()) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("tool_name", toolName.trim());
        fields.put("args", argsSummary == null ? "" : argsSummary);
        fields.put("is_error", !ok);
        fields.put("duration_ms", durationMs);
        if (!ok && error != null && !error.isBlank()) {
            fields.put("error_message", truncate(error, ADVICE_MAX));
        }
        write(agentRoot, context, "agent_tool_call", fields);
    }

    public static RunLogContext resolveContext(RunLogContext explicit) {
        if (explicit != null) {
            return explicit;
        }
        RunAuditScope.Binding binding = RunAuditScope.get();
        if (binding != null && binding.logContext() != null) {
            return binding.logContext();
        }
        return cliFallback("audit");
    }

    public static Path resolveAgentRoot(Path explicit) {
        if (explicit != null) {
            return explicit.toAbsolutePath().normalize();
        }
        RunAuditScope.Binding binding = RunAuditScope.get();
        if (binding != null && binding.agentRoot() != null) {
            return binding.agentRoot();
        }
        return AgentDataPaths.agentRoot();
    }

    private static RunLogContext cliFallback(String op) {
        return new RunLogContext("cli", op + "-" + Instant.now().toEpochMilli(), "", "cli");
    }

    private static void write(Path agentRoot, RunLogContext context, String type, Map<String, Object> fields) {
        Path root = agentRoot.toAbsolutePath().normalize();
        RunLogContext ctx = resolveContext(context);
        new EventJsonlWriter(AgentDataPaths.eventsJsonl(root)).write(ctx, type, fields);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...(truncated)";
    }

    /** 从 tool result 文本解析 {@code [coach] hookId: …}。 */
    public static String parseCoachHookId(String toolResultText) {
        if (toolResultText == null) {
            return "";
        }
        String marker = "[coach] ";
        int i = toolResultText.indexOf(marker);
        if (i < 0) {
            return "";
        }
        int start = i + marker.length();
        int colon = toolResultText.indexOf(':', start);
        if (colon <= start) {
            return "";
        }
        return toolResultText.substring(start, colon).trim();
    }

    public static String parseCoachAdvice(String toolResultText) {
        if (toolResultText == null) {
            return "";
        }
        String marker = "[coach] ";
        int i = toolResultText.indexOf(marker);
        if (i < 0) {
            return "";
        }
        int colon = toolResultText.indexOf(':', i + marker.length());
        if (colon < 0 || colon + 1 >= toolResultText.length()) {
            return "";
        }
        return toolResultText.substring(colon + 1).trim();
    }
}
