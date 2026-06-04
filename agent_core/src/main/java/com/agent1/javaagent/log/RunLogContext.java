package com.agent1.javaagent.log;

import java.util.Objects;

/** 单次 Run 的 JSONL 上下文；{@code seq} 在该 {@code runId} 内从 1 递增。 */
public final class RunLogContext {

    private final String sessionId;
    private final String runId;
    private final String parentRunId;
    private final String agentId;
    private int seq;

    public RunLogContext(String sessionId, String runId, String parentRunId, String agentId) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.runId = Objects.requireNonNull(runId, "runId");
        this.parentRunId = parentRunId == null ? "" : parentRunId;
        this.agentId = agentId == null ? "" : agentId;
        this.seq = 0;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getRunId() {
        return runId;
    }

    public String getParentRunId() {
        return parentRunId;
    }

    public String getAgentId() {
        return agentId;
    }

    /** 返回递增后的序号（从 1 开始）。 */
    public int nextSeq() {
        seq += 1;
        return seq;
    }

    public int currentSeq() {
        return seq;
    }
}
