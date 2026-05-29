package com.agent1.javaagent.core;

import com.agent1.javaagent.model.AgentMessage;
import java.util.ArrayList;
import java.util.List;

/** 按用户消息轮次保留上下文尾部（10-上下文 / 13-配置 maxContextTurns）。 */
public final class ContextTurnLimiter {

    private ContextTurnLimiter() {
    }

    /**
     * 保留最近 {@code maxUserTurns} 条 {@link AgentMessage#ROLE_USER} 消息及其后的全部消息。
     *
     * @param maxUserTurns {@code <= 0} 不裁剪
     */
    public static List<AgentMessage> limitByUserTurns(List<AgentMessage> messages, int maxUserTurns) {
        if (messages == null || messages.isEmpty()) {
            return messages == null ? List.of() : List.copyOf(messages);
        }
        if (maxUserTurns <= 0) {
            return List.copyOf(messages);
        }
        List<Integer> userIndices = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (AgentMessage.ROLE_USER.equals(messages.get(i).getRole())) {
                userIndices.add(i);
            }
        }
        if (userIndices.size() <= maxUserTurns) {
            return List.copyOf(messages);
        }
        int startIdx = userIndices.get(userIndices.size() - maxUserTurns);
        while (startIdx < messages.size() && startIdx > 0
            && AgentMessage.ROLE_TOOL_RESULT.equals(messages.get(startIdx).getRole())) {
            startIdx--;
        }
        return new ArrayList<>(messages.subList(startIdx, messages.size()));
    }
}
