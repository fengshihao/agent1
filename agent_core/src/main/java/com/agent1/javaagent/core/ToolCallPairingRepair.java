package com.agent1.javaagent.core;

import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.model.ToolCall;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 发给模型前补齐缺失的 tool 回执，并按 assistant 的 tool_calls 顺序排列回执。
 *
 * <p>常见坏数据来源：轮次/工具预算中断、进程崩溃、取消、transcript 解码 id 错配、并行 tool 只回来一部分。
 */
public final class ToolCallPairingRepair {

    public static final String MISSING_RESULT_NOTE =
        "本次工具调用没有执行（上一轮在拿到结果前就停了）。请按原计划继续，不要把这条当作已经成功。";

    private ToolCallPairingRepair() {
    }

    public static List<AgentMessage> ensureResults(List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages == null ? List.of() : List.copyOf(messages);
        }
        Set<String> satisfiedGlobally = new LinkedHashSet<>();
        for (AgentMessage message : messages) {
            if (isToolResult(message) && message.getToolCallId() != null) {
                satisfiedGlobally.add(message.getToolCallId());
            }
        }
        List<AgentMessage> repaired = new ArrayList<>();
        int index = 0;
        while (index < messages.size()) {
            AgentMessage message = messages.get(index);
            index += 1;
            if (!AgentMessage.ROLE_ASSISTANT.equals(message.getRole()) || message.getToolCalls().isEmpty()) {
                repaired.add(message);
                continue;
            }
            repaired.add(message);
            List<AgentMessage> block = new ArrayList<>();
            while (index < messages.size() && isToolResult(messages.get(index))) {
                block.add(messages.get(index));
                index += 1;
            }
            Map<String, List<AgentMessage>> byId = groupToolResults(block);
            Set<AgentMessage> consumed = new LinkedHashSet<>();
            for (ToolCall call : message.getToolCalls()) {
                String id = call.getId() == null ? "" : call.getId();
                AgentMessage match = takeFirstUnused(byId.get(id), consumed);
                if (match != null) {
                    repaired.add(match);
                    continue;
                }
                repaired.add(AgentMessage.toolResult(id, MISSING_RESULT_NOTE, true));
                satisfiedGlobally.add(id);
            }
            for (AgentMessage orphan : block) {
                if (!consumed.contains(orphan)) {
                    repaired.add(orphan);
                }
            }
        }
        return listEquals(messages, repaired) ? List.copyOf(messages) : repaired;
    }

    private static boolean listEquals(List<AgentMessage> left, List<AgentMessage> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            AgentMessage a = left.get(i);
            AgentMessage b = right.get(i);
            if (!a.getRole().equals(b.getRole())
                || !a.getContent().equals(b.getContent())
                || !a.getToolCalls().equals(b.getToolCalls())
                || !java.util.Objects.equals(a.getToolCallId(), b.getToolCallId())
                || a.isError() != b.isError()) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, List<AgentMessage>> groupToolResults(List<AgentMessage> block) {
        Map<String, List<AgentMessage>> byId = new HashMap<>();
        for (AgentMessage message : block) {
            String id = message.getToolCallId() == null ? "" : message.getToolCallId();
            byId.computeIfAbsent(id, ignored -> new ArrayList<>()).add(message);
        }
        return byId;
    }

    private static AgentMessage takeFirstUnused(List<AgentMessage> candidates, Set<AgentMessage> consumed) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        for (AgentMessage candidate : candidates) {
            if (!consumed.contains(candidate)) {
                consumed.add(candidate);
                return candidate;
            }
        }
        return null;
    }

    private static boolean isToolResult(AgentMessage message) {
        return AgentMessage.ROLE_TOOL_RESULT.equals(message.getRole());
    }
}
