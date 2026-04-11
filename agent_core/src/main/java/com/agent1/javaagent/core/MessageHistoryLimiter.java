package com.agent1.javaagent.core;

import com.agent1.javaagent.model.AgentMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * Truncates message lists sent to the LLM while avoiding a leading orphaned {@code toolResult}
 * (which would break OpenAI-compatible tool protocol).
 */
public final class MessageHistoryLimiter {

    private MessageHistoryLimiter() {
    }

    /**
     * Keeps at most the last {@code maxMessages} entries. If the cut would start inside a tool-result
     * block, the start index is moved earlier to include the assistant message that issued the tool calls.
     * The returned list may contain slightly more than {@code maxMessages} in that case.
     *
     * @param maxMessages maximum messages to target; {@code <= 0} means no limit (returns a copy of the input)
     */
    public static List<AgentMessage> limitTail(List<AgentMessage> messages, int maxMessages) {
        if (messages == null || messages.isEmpty()) {
            return messages == null ? List.of() : List.copyOf(messages);
        }
        if (maxMessages <= 0 || messages.size() <= maxMessages) {
            return List.copyOf(messages);
        }
        int n = messages.size();
        int start = n - maxMessages;
        while (start < n && start > 0 && AgentMessage.ROLE_TOOL_RESULT.equals(messages.get(start).getRole())) {
            start--;
        }
        return new ArrayList<>(messages.subList(start, n));
    }
}
