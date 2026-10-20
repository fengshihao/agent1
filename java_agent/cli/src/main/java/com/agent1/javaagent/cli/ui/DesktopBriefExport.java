package com.agent1.javaagent.cli.ui;

import com.agent1.javaagent.model.AgentMessage;
import java.util.List;

/** 桌面会话简报（对齐 Android {@code BriefChatExport} 的轻量子集，供复制到 Cursor）。 */
public final class DesktopBriefExport {

    private static final int ASSISTANT_MAX = 1_600;

    private DesktopBriefExport() {
    }

    public static String format(List<AgentMessage> messages, String sessionId, String model) {
        if (messages.isEmpty()) {
            return "（空会话）";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Agent1 桌面 Web UI 会话简报\n");
        sb.append("session: ").append(sessionId).append('\n');
        if (model != null && !model.isBlank()) {
            sb.append("model: ").append(model).append('\n');
        }
        sb.append("\n");
        for (AgentMessage message : messages) {
            String role = message.getRole();
            if (AgentMessage.ROLE_USER.equals(role)) {
                sb.append("## 用户\n").append(nullToEmpty(message.getContent())).append("\n\n");
            } else if (AgentMessage.ROLE_ASSISTANT.equals(role)) {
                sb.append("## 助手\n").append(trimAssistant(nullToEmpty(message.getContent()))).append("\n\n");
            } else if (AgentMessage.ROLE_TOOL_RESULT.equals(role)) {
                sb.append("## 工具结果");
                if (message.getToolCallId() != null && !message.getToolCallId().isBlank()) {
                    sb.append(" (").append(message.getToolCallId()).append(')');
                }
                sb.append('\n').append(trimAssistant(nullToEmpty(message.getContent()))).append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private static String trimAssistant(String text) {
        if (text.length() <= ASSISTANT_MAX) {
            return text;
        }
        return text.substring(0, ASSISTANT_MAX) + "\n…（已截断）";
    }
}
