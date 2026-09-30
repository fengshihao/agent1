package com.agent1.javaagent.capability;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** 桌面：sqlite-jdbc。 */
final class JdbcCapabilityDatabase implements CapabilityDatabase {

    private final Connection connection;

    JdbcCapabilityDatabase(Path databaseFile) throws SQLException {
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
    }

    @Override
    public void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Override
    public void execute(String sql, List<Object> params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            statement.executeUpdate();
        }
    }

    @Override
    public CapabilityRowCursor query(String sql, List<Object> params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            bind(statement, params);
            return new JdbcRowCursor(statement, statement.executeQuery());
        } catch (SQLException e) {
            statement.close();
            throw e;
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    private static void bind(PreparedStatement statement, List<Object> params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.size(); i++) {
            statement.setObject(i + 1, params.get(i));
        }
    }

    private static final class JdbcRowCursor implements CapabilityRowCursor {
        private final PreparedStatement statement;
        private final ResultSet rows;

        private JdbcRowCursor(PreparedStatement statement, ResultSet rows) {
            this.statement = statement;
            this.rows = rows;
        }

        @Override
        public boolean next() throws SQLException {
            return rows.next();
        }

        @Override
        public String getString(String column) throws SQLException {
            return rows.getString(column);
        }

        @Override
        public double getDouble(String column) throws SQLException {
            return rows.getDouble(column);
        }

        @Override
        public void close() throws SQLException {
            rows.close();
            statement.close();
        }
    }
}
