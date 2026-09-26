package com.agent1.javaagent.run;

import java.util.Objects;
import java.util.Optional;

/** 单次 Run 的持久化快照（{@code runs/<runId>.json}）。 */
public final class RunRecord {

    private final String runId;
    private final String sessionId;
    private final RunState state;
    private final String startedAt;
    private final String updatedAt;
    private final String lastError;
    private final int completedTurns;
    private final int messageCount;

    public RunRecord(
        String runId,
        String sessionId,
        RunState state,
        String startedAt,
        String updatedAt,
        String lastError
    ) {
        this(runId, sessionId, state, startedAt, updatedAt, lastError, 0, 0);
    }

    public RunRecord(
        String runId,
        String sessionId,
        RunState state,
        String startedAt,
        String updatedAt,
        String lastError,
        int completedTurns,
        int messageCount
    ) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.state = Objects.requireNonNull(state, "state");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.lastError = lastError;
        this.completedTurns = Math.max(0, completedTurns);
        this.messageCount = Math.max(0, messageCount);
    }

    public String getRunId() {
        return runId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public RunState getState() {
        return state;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public Optional<String> getLastError() {
        return lastError == null || lastError.isBlank()
            ? Optional.empty()
            : Optional.of(lastError);
    }

    public RunRecord withState(RunState newState, String newUpdatedAt) {
        return new RunRecord(runId, sessionId, newState, startedAt, newUpdatedAt, lastError);
    }

    public RunRecord withTerminal(RunState terminalState, String newUpdatedAt, String error) {
        return new RunRecord(
            runId, sessionId, terminalState, startedAt, newUpdatedAt, error, completedTurns, messageCount
        );
    }

    public RunRecord withCheckpoint(int turns, int messages, String newUpdatedAt) {
        return new RunRecord(runId, sessionId, state, startedAt, newUpdatedAt, lastError, turns, messages);
    }

    public int getCompletedTurns() {
        return completedTurns;
    }

    public int getMessageCount() {
        return messageCount;
    }
}
