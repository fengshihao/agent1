package com.agent1.javaagent.log.query;

import java.util.Objects;
import java.util.Optional;

/** 05-日志查询：过滤条件（只读）。 */
public final class EventLogFilter {

    private final String sessionId;
    private final String runId;
    private final String parentRunId;
    private final String toolName;
    private final boolean failedOnly;

    private EventLogFilter(Builder builder) {
        this.sessionId = blankToNull(builder.sessionId);
        this.runId = blankToNull(builder.runId);
        this.parentRunId = blankToNull(builder.parentRunId);
        this.toolName = blankToNull(builder.toolName);
        this.failedOnly = builder.failedOnly;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<String> sessionId() {
        return Optional.ofNullable(sessionId);
    }

    public Optional<String> runId() {
        return Optional.ofNullable(runId);
    }

    public Optional<String> parentRunId() {
        return Optional.ofNullable(parentRunId);
    }

    public Optional<String> toolName() {
        return Optional.ofNullable(toolName);
    }

    public boolean failedOnly() {
        return failedOnly;
    }

    public boolean matches(EventLogEntry entry) {
        if (sessionId != null && !sessionId.equals(entry.sessionId())) {
            return false;
        }
        if (runId != null && !runId.equals(entry.runId())) {
            return false;
        }
        if (parentRunId != null && !parentRunId.equals(entry.parentRunId())) {
            return false;
        }
        if (toolName != null && "tool_call".equals(entry.type())) {
            return toolName.equals(entry.fieldText("tool_name"));
        }
        if (toolName != null && !"tool_call".equals(entry.type())) {
            return false;
        }
        if (failedOnly && "tool_result".equals(entry.type())) {
            return entry.fieldBool("is_error");
        }
        if (failedOnly && !"tool_result".equals(entry.type())) {
            return false;
        }
        return true;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static final class Builder {
        private String sessionId;
        private String runId;
        private String parentRunId;
        private String toolName;
        private boolean failedOnly;

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public Builder parentRunId(String parentRunId) {
            this.parentRunId = parentRunId;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder failedOnly(boolean failedOnly) {
            this.failedOnly = failedOnly;
            return this;
        }

        public EventLogFilter build() {
            return new EventLogFilter(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EventLogFilter that)) {
            return false;
        }
        return failedOnly == that.failedOnly
            && Objects.equals(sessionId, that.sessionId)
            && Objects.equals(runId, that.runId)
            && Objects.equals(parentRunId, that.parentRunId)
            && Objects.equals(toolName, that.toolName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, runId, parentRunId, toolName, failedOnly);
    }
}
