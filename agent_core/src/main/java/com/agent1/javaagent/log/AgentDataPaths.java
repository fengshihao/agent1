package com.agent1.javaagent.log;

import java.nio.file.Path;

/** {@code agentRoot} 下布局见 doc/基础能力/README.md（sessions、logs/events.jsonl）。 */
public final class AgentDataPaths {

    private static final String ENV_AGENT_ROOT = "AGENT1_AGENT_ROOT";
    private static final String ENV_EVENTS_LOG = "AGENT1_EVENTS_LOG_FILE";

    private AgentDataPaths() {
    }

    public static Path agentRoot() {
        String fromEnv = System.getenv(ENV_AGENT_ROOT);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Path.of(fromEnv).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home")).resolve("files").resolve("agent");
    }

    public static Path logsDir() {
        return logsDir(agentRoot());
    }

    public static Path logsDir(Path agentRoot) {
        return agentRoot.toAbsolutePath().normalize().resolve("logs");
    }

    public static Path eventsJsonl() {
        return eventsJsonl(agentRoot());
    }

    public static Path eventsJsonl(Path agentRoot) {
        String fromEnv = System.getenv(ENV_EVENTS_LOG);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Path.of(fromEnv).toAbsolutePath().normalize();
        }
        return logsDir(agentRoot).resolve("events.jsonl");
    }
}
