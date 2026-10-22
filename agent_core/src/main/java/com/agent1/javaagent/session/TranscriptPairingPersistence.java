package com.agent1.javaagent.session;

import com.agent1.javaagent.core.ToolCallPairingRepair;
import com.agent1.javaagent.model.AgentMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 将 {@link ToolCallPairingRepair} 结果写回 transcript.jsonl，避免仅请求时内存修补、磁盘仍坏。 */
public final class TranscriptPairingPersistence {

    static final String REPAIR_RUN_ID = "transcript-pairing-repair";

    private TranscriptPairingPersistence() {
    }

    /**
     * @return 修复后的消息列表（与 {@code loadTranscript} 一致）；若已配对则原样返回。
     */
    public static List<AgentMessage> loadRepaired(SessionStore store, String sessionId) {
        List<AgentMessage> loaded = store.loadTranscript(sessionId);
        List<AgentMessage> repaired = ToolCallPairingRepair.ensureResults(loaded);
        if (messagesEqual(loaded, repaired)) {
            return loaded;
        }
        store.replaceTranscript(sessionId, repaired, REPAIR_RUN_ID);
        return repaired;
    }

    public static List<AgentMessage> repairInMemory(List<AgentMessage> messages) {
        return ToolCallPairingRepair.ensureResults(messages);
    }

    public static boolean persistIfRepaired(
        SessionStore store,
        String sessionId,
        List<AgentMessage> before,
        List<AgentMessage> after
    ) {
        if (messagesEqual(before, after)) {
            return false;
        }
        store.replaceTranscript(sessionId, after, REPAIR_RUN_ID);
        return true;
    }

    static boolean messagesEqual(List<AgentMessage> left, List<AgentMessage> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!messageEqual(left.get(i), right.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean messageEqual(AgentMessage a, AgentMessage b) {
        return Objects.equals(a.getRole(), b.getRole())
            && Objects.equals(a.getContent(), b.getContent())
            && Objects.equals(a.getReasoningContent(), b.getReasoningContent())
            && Objects.equals(a.getToolCallId(), b.getToolCallId())
            && a.isError() == b.isError()
            && a.getToolCalls().equals(b.getToolCalls());
    }

    static List<String> runIdsForRewrite(int messageCount, List<String> existingRunIds, String defaultRunId) {
        List<String> out = new ArrayList<>(messageCount);
        for (int i = 0; i < messageCount; i++) {
            if (existingRunIds != null && i < existingRunIds.size()) {
                out.add(existingRunIds.get(i));
            } else {
                out.add(defaultRunId);
            }
        }
        return out;
    }
}
