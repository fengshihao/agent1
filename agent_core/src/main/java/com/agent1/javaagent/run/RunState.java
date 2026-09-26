package com.agent1.javaagent.run;

/** Run 生命周期状态（见 doc/基础能力/02）。 */
public enum RunState {
    QUEUED("queued"),
    RUNNING("running"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    PAUSED("paused"),
    WAITING_USER("waiting_user");

    private final String wireValue;

    RunState(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static RunState fromWire(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("run state required");
        }
        String normalized = value.trim().toLowerCase();
        for (RunState state : values()) {
            if (state.wireValue.equals(normalized)) {
                return state;
            }
        }
        throw new IllegalArgumentException("unknown run state: " + value);
    }
}
