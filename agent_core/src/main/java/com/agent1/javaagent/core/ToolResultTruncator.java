package com.agent1.javaagent.core;

import com.agent1.javaagent.model.AgentMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * 窗口内、非最新一轮的 toolResult 超长时换成占位（10-上下文）。
 * 只改发给模型的视图，不改会话 transcript。
 */
public final class ToolResultTruncator {

    public static final int DEFAULT_MAX_CHARS = 280;

    private ToolResultTruncator() {
    }

    /**
     * @param maxChars {@code <= 0} 不截断
     */
    public static List<AgentMessage> truncateOldTurns(List<AgentMessage> messages, int maxChars) {
        if (messages == null || messages.isEmpty() || maxChars <= 0) {
            return messages == null ? List.of() : List.copyOf(messages);
        }
        int latestUser = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (AgentMessage.ROLE_USER.equals(messages.get(i).getRole())) {
                latestUser = i;
            }
        }
        if (latestUser <= 0) {
            return List.copyOf(messages);
        }
        List<AgentMessage> out = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            AgentMessage message = messages.get(i);
            if (i < latestUser
                && AgentMessage.ROLE_TOOL_RESULT.equals(message.getRole())
                && message.getContent().length() > maxChars) {
                out.add(placeholder(message));
            } else {
                out.add(message);
            }
        }
        return out;
    }

    private static AgentMessage placeholder(AgentMessage original) {
        int chars = original.getContent().length();
        String text = "（工具结果已省略，共 " + chars + " 字。完整内容仍在会话记录中。）";
        return new AgentMessage(
            AgentMessage.ROLE_TOOL_RESULT,
            text,
            original.getTimestampMs(),
            original.getToolCallId(),
            original.isError(),
            List.of()
        );
    }
}
