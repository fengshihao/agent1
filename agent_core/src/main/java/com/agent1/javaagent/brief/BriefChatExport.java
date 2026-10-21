package com.agent1.javaagent.brief;

import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.ToolCall;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 供粘贴到 Cursor / 云端 Agent 的简略会话文本（对齐 Android {@code BriefChatExport}）。 */
public final class BriefChatExport {

    private static final int ASSISTANT_MAX_CHARS = 1_600;
    private static final int ASSISTANT_HEAD_CHARS = 900;
    private static final int ASSISTANT_TAIL_CHARS = 450;
    private static final int REASONING_MAX_CHARS = 400;
    private static final int TOOL_ARG_CHARS = 360;
    private static final int TOOL_ERROR_RAW_CHARS = 500;

    private static final Pattern CODE_FENCE = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern MARKDOWN_TABLE = Pattern.compile("(?m)^\\|.+\\|\\s*$");

    private BriefChatExport() {
    }

    public record Context(
        String sessionId,
        String modelLabel,
        String appVersion,
        String deviceLabel,
        String limits,
        String configError
    ) {
        public Context() {
            this("", "", "", "", "", "");
        }
    }

    public static String format(List<AgentMessage> messages, String sessionTitle, Context context) {
        if (messages == null || messages.isEmpty()) {
            return "（空会话）";
        }
        Context ctx = context == null ? new Context() : context;
        Map<String, ToolCall> toolCallsById = indexToolCalls(messages);
        StringBuilder sb = new StringBuilder();
        sb.append("以下是 agent1 桌面 Web UI 会话简报，按时间顺序排列，供调试 AI 直接阅读。").append('\n');
        sb.append("工具失败、参数和结果摘要都在正文里；大段代码和表格已压缩。请根据异常摘要和对应工具 id 定位问题。")
            .append('\n')
            .append('\n');
        appendContextHeader(sb, ctx);
        appendFailureSummary(sb, messages, toolCallsById);
        String title = sessionTitle == null ? "" : sessionTitle.trim();
        if (!title.isEmpty()) {
            sb.append("# ").append(title).append('\n').append('\n');
        }
        for (AgentMessage msg : messages) {
            if (AgentMessage.ROLE_USER.equals(msg.getRole())) {
                appendUser(sb, msg);
            } else if (AgentMessage.ROLE_ASSISTANT.equals(msg.getRole())) {
                appendAssistant(sb, msg);
            } else if (AgentMessage.ROLE_TOOL_RESULT.equals(msg.getRole())) {
                appendTool(sb, msg, toolCallsById);
            }
        }
        String out = sb.toString();
        int end = out.length();
        while (end > 0 && (out.charAt(end - 1) == '\n' || out.charAt(end - 1) == '\r')) {
            end--;
        }
        return out.substring(0, end);
    }

    private static Map<String, ToolCall> indexToolCalls(List<AgentMessage> messages) {
        Map<String, ToolCall> map = new LinkedHashMap<>();
        for (AgentMessage msg : messages) {
            if (!AgentMessage.ROLE_ASSISTANT.equals(msg.getRole())) {
                continue;
            }
            for (ToolCall call : msg.getToolCalls()) {
                map.put(call.getId(), call);
            }
        }
        return map;
    }

    private static void appendContextHeader(StringBuilder sb, Context context) {
        List<String> lines = new java.util.ArrayList<>();
        if (isNotBlank(context.sessionId())) {
            lines.add("session: " + context.sessionId());
        }
        if (isNotBlank(context.modelLabel())) {
            lines.add("model: " + context.modelLabel());
        }
        if (isNotBlank(context.appVersion())) {
            lines.add("app: " + context.appVersion());
        }
        if (isNotBlank(context.deviceLabel())) {
            lines.add("device: " + context.deviceLabel());
        }
        if (isNotBlank(context.limits())) {
            lines.add("limits: " + context.limits());
        }
        if (isNotBlank(context.configError())) {
            lines.add("configError: " + context.configError());
        }
        if (lines.isEmpty()) {
            return;
        }
        sb.append("## 环境").append('\n');
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        sb.append('\n');
    }

    private static void appendFailureSummary(
        StringBuilder sb,
        List<AgentMessage> messages,
        Map<String, ToolCall> toolCallsById
    ) {
        List<AgentMessage> errors = messages.stream()
            .filter(m -> AgentMessage.ROLE_TOOL_RESULT.equals(m.getRole()) && m.isError())
            .toList();
        if (errors.isEmpty()) {
            return;
        }
        sb.append("## 异常摘要").append('\n');
        sb.append("工具失败 ").append(errors.size()).append(" 次。").append('\n');
        AgentMessage last = errors.get(errors.size() - 1);
        ToolCall matched = last.getToolCallId() == null ? null : toolCallsById.get(last.getToolCallId());
        String name = matched != null && isNotBlank(matched.getName()) ? matched.getName() : "tool";
        String summary = BriefToolResultSummary.summarize(last.getContent());
        sb.append("最近一次：").append(name).append(" id=").append(last.getToolCallId() == null ? "-" : last.getToolCallId())
            .append(" — ").append(summary).append('\n').append('\n');
    }

    /** 压缩助手长文：去掉大块代码/表格细节，必要时保留首尾摘要。 */
    public static String compressAssistantContent(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return text;
        }

        Matcher fenceMatcher = CODE_FENCE.matcher(text);
        StringBuffer fenced = new StringBuffer();
        while (fenceMatcher.find()) {
            String match = fenceMatcher.group();
            int lines = match.length() - match.replace("\n", "").length() + 1;
            fenceMatcher.appendReplacement(fenced, "[省略代码块 · " + lines + "行]");
        }
        fenceMatcher.appendTail(fenced);
        text = fenced.toString();

        int tableLines = 0;
        Matcher tableMatcher = MARKDOWN_TABLE.matcher(text);
        while (tableMatcher.find()) {
            tableLines++;
        }
        if (tableLines >= 4) {
            text = MARKDOWN_TABLE.matcher(text).replaceAll("");
            text = text.replaceAll("\n{3,}", "\n\n").trim();
            text = text + "\n[表格 " + tableLines + " 行已省略，保留上文说明]";
        }

        if (text.length() <= ASSISTANT_MAX_CHARS) {
            return text.trim();
        }

        String head = text.substring(0, ASSISTANT_HEAD_CHARS).trim();
        String tail = text.substring(text.length() - ASSISTANT_TAIL_CHARS).trim();
        int omitted = text.length() - ASSISTANT_HEAD_CHARS - ASSISTANT_TAIL_CHARS;
        return (head + "\n\n…（助手回复略去约 " + Math.max(0, omitted) + " 字）\n\n" + tail).trim();
    }

    private static void appendUser(StringBuilder sb, AgentMessage msg) {
        sb.append("**用户**").append('\n');
        sb.append(msg.getContent().trim()).append('\n').append('\n');
    }

    private static void appendAssistant(StringBuilder sb, AgentMessage msg) {
        String text = compressAssistantContent(msg.getContent());
        String reasoning = compressAssistantContent(msg.getReasoningContent());
        if (reasoning.length() > REASONING_MAX_CHARS) {
            reasoning = reasoning.substring(0, REASONING_MAX_CHARS).trim();
        }
        List<String> toolDigests = msg.getToolCalls().stream().map(BriefChatExport::debugToolCallLine).toList();
        if (text.isEmpty() && reasoning.isEmpty() && toolDigests.isEmpty()) {
            return;
        }
        sb.append("**助手**").append('\n');
        if (!text.isEmpty()) {
            sb.append(text).append('\n');
        }
        if (!reasoning.isEmpty()) {
            if (!text.isEmpty()) {
                sb.append('\n');
            }
            sb.append("思考：").append(reasoning).append('\n');
        }
        for (String digest : toolDigests) {
            sb.append('\n').append(digest).append('\n');
        }
        sb.append('\n');
    }

    private static String debugToolCallLine(ToolCall call) {
        if (BriefAskUserSupport.TOOL_NAME.equals(call.getName())) {
            BriefAskUserSupport.Request req = BriefAskUserSupport.parseRequestFromToolCall(call);
            int count = req == null ? 0 : req.questions().size();
            return "工具调用 ask_user id=" + call.getId() + " 问题数=" + count;
        }
        String args = call.getArgumentsJson().trim().replace('\n', ' ');
        String preview = args.length() <= TOOL_ARG_CHARS ? args : args.substring(0, TOOL_ARG_CHARS) + "…";
        return "工具调用 " + call.getName() + " id=" + call.getId() + " args=" + preview;
    }

    private static void appendTool(
        StringBuilder sb,
        AgentMessage msg,
        Map<String, ToolCall> toolCallsById
    ) {
        ToolCall call = msg.getToolCallId() == null ? null : toolCallsById.get(msg.getToolCallId());
        String toolName = call == null || call.getName() == null || call.getName().isEmpty() ? "tool" : call.getName();
        if (call != null && BriefAskUserSupport.TOOL_NAME.equals(call.getName())) {
            BriefAskUserSupport.Request req = BriefAskUserSupport.parseRequestFromToolCall(call);
            if (req != null) {
                sb.append(BriefAskUserSupport.formatForBrief(req, msg.getContent().trim())).append('\n').append('\n');
                return;
            }
        }
        String summary = BriefToolResultSummary.summarize(msg.getContent());
        if (msg.isError()) {
            sb.append("**工具异常 · ").append(toolName).append("** id=")
                .append(msg.getToolCallId() == null ? "-" : msg.getToolCallId())
                .append(' ').append(summary).append('\n');
            String raw = msg.getContent().trim().replace('\n', ' ');
            String clipped = raw.length() <= TOOL_ERROR_RAW_CHARS ? raw : raw.substring(0, TOOL_ERROR_RAW_CHARS) + "…";
            if (!clipped.isEmpty() && !summary.equals(clipped) && !summary.contains(clipped.substring(0, Math.min(80, clipped.length())))) {
                sb.append("原始：").append(clipped).append('\n');
            }
            sb.append('\n');
            return;
        }
        if (summary.isBlank()) {
            return;
        }
        sb.append("_[工具 · ").append(toolName).append("]_ ").append(summary).append('\n').append('\n');
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
