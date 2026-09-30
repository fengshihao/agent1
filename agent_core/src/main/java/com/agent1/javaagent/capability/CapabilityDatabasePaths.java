package com.agent1.javaagent.capability;

import java.nio.file.Path;

/** {@code agentRoot} 下能力索引 SQLite 路径。 */
public final class CapabilityDatabasePaths {

    public static final String DB_FILE_NAME = "capabilities.db";

    private CapabilityDatabasePaths() {
    }

    public static Path databaseFile(Path agentRoot) {
        return agentRoot.toAbsolutePath().normalize()
            .resolve("docs/capabilities")
            .resolve(DB_FILE_NAME);
    }
}
