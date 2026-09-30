package com.agent1.javaagent.capability;

import java.nio.file.Path;
import java.sql.SQLException;

@FunctionalInterface
public interface CapabilityDatabaseOpener {

    CapabilityDatabase open(Path databaseFile) throws SQLException;
}
