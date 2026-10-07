package com.agent1.javaagent.script;

import com.agent1.javaagent.core.CancellationToken;
import com.agent1.javaagent.log.AgentAuditEvents;
import com.agent1.javaagent.log.RunAuditScope;
import com.agent1.javaagent.tool.AgentTool;
import com.agent1.javaagent.tool.ToolExecutionResult;
import com.agent1.javaagent.tool.ToolUpdateListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把当前 Run 的 {@link AgentTool} 暴露给脚本。{@code execute_script} 不暴露，避免递归。
 *
 * 调用发生在工具线程上（与 {@code execute_script} 同线程），因此：
 * - 取消令牌取自 {@link ScriptToolRunContext}（AgentRuntime 在派发工具任务时绑定），
 *   用户停止 Run 后脚本内的工具调用同样可感知取消；
 * - 审计经 {@link RunAuditScope} 绑定的上下文写 {@code agent_tool_call} 事件，
 *   保证 events.jsonl 能回放脚本内部都调了哪些工具。
 */
public final class AgentToolsScriptBridge implements ScriptToolBridge {

    static final String EXCLUDED_TOOL = "execute_script";
    static final String EXCLUDED_ASK_USER = "ask_user";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ToolUpdateListener NO_UPDATE = update -> {
        // 脚本内同步调用，不向前端推送工具进度。
    };
    private static final int ARGS_SUMMARY_MAX = 220;
    private static final int ERROR_SUMMARY_MAX = 280;

    private final List<AgentTool> tools;
    private final Set<String> exposedNames;

    public AgentToolsScriptBridge(List<AgentTool> tools) {
        this.tools = List.copyOf(tools);
        Set<String> names = new LinkedHashSet<>();
        for (AgentTool tool : this.tools) {
            if (!EXCLUDED_TOOL.equals(tool.name()) && !EXCLUDED_ASK_USER.equals(tool.name())) {
                names.add(tool.name());
            }
        }
        this.exposedNames = Set.copyOf(names);
    }

    @Override
    public Set<String> exposedNames() {
        return exposedNames;
    }

    @Override
    public String call(String toolName, Map<String, Object> arguments) {
        if (toolName == null || toolName.isBlank() || !exposedNames.contains(toolName)) {
            throw new IllegalArgumentException("unsupported: $tools." + toolName);
        }
        AgentTool tool = null;
        for (AgentTool candidate : tools) {
            if (toolName.equals(candidate.name())) {
                tool = candidate;
                break;
            }
        }
        if (tool == null) {
            throw new IllegalArgumentException("unsupported: $tools." + toolName);
        }
        JsonNode params = arguments == null || arguments.isEmpty()
            ? MAPPER.createObjectNode()
            : MAPPER.valueToTree(arguments);
        CancellationToken token = ScriptToolRunContext.current();
        if (token == null) {
            // 非托管路径（测试 / 直连桥）：回退到不可取消令牌
            token = new CancellationToken();
        }
        RunAuditScope.Binding auditBinding = RunAuditScope.get();
        String argsSummary = summarizeArgs(arguments);
        long startedAt = System.currentTimeMillis();
        try {
            ToolExecutionResult result = tool.execute(toolName, params, token, NO_UPDATE);
            writeAudit(auditBinding, toolName, argsSummary, true, startedAt, null);
            return result.getText() == null ? "" : result.getText();
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            writeAudit(auditBinding, toolName, argsSummary, false, startedAt, message);
            throw new IllegalArgumentException(message, e);
        }
    }

    private static void writeAudit(
        RunAuditScope.Binding binding,
        String toolName,
        String argsSummary,
        boolean ok,
        long startedAt,
        String error
    ) {
        if (binding == null) {
            return;
        }
        AgentAuditEvents.agentToolCall(
            binding.agentRoot(),
            binding.logContext(),
            toolName,
            argsSummary,
            ok,
            Math.max(0L, System.currentTimeMillis() - startedAt),
            error
        );
    }

    private static String summarizeArgs(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }
        try {
            return truncate(MAPPER.writeValueAsString(arguments), ARGS_SUMMARY_MAX);
        } catch (Exception ignored) {
            return truncate(String.valueOf(arguments), ARGS_SUMMARY_MAX);
        }
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
}