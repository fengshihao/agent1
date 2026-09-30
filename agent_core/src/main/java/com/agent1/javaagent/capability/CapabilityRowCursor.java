package com.agent1.javaagent.capability;

import java.sql.SQLException;

public interface CapabilityRowCursor extends AutoCloseable {

    boolean next() throws SQLException;

    String getString(String column) throws SQLException;

    double getDouble(String column) throws SQLException;

    @Override
    void close() throws SQLException;
}
