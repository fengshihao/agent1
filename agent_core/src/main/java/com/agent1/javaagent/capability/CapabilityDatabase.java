package com.agent1.javaagent.capability;

import java.sql.SQLException;
import java.util.List;

/** 能力索引库会话。桌面用 JDBC，Android 用系统 SQLiteDatabase。 */
public interface CapabilityDatabase extends AutoCloseable {

    void execute(String sql) throws SQLException;

    void execute(String sql, List<Object> params) throws SQLException;

    CapabilityRowCursor query(String sql, List<Object> params) throws SQLException;

    @Override
    void close() throws SQLException;
}
