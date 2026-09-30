package com.agent1.android.productivity.logic.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import com.agent1.javaagent.capability.CapabilityDatabase;
import com.agent1.javaagent.capability.CapabilityRowCursor;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

/**
 * 系统 SQLite（framework），与桌面同一套 capability / FTS5 SQL。
 * 不使用 sqlite-jdbc，避免把桌面 .so 打进 APK。
 */
public final class AndroidCapabilityDatabase implements CapabilityDatabase {

    private final SQLiteDatabase db;

    private AndroidCapabilityDatabase(SQLiteDatabase db) {
        this.db = db;
    }

    public static AndroidCapabilityDatabase open(Path databaseFile) {
        java.io.File file = databaseFile.toFile();
        java.io.File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        return new AndroidCapabilityDatabase(SQLiteDatabase.openOrCreateDatabase(file, null));
    }

    @Override
    public void execute(String sql) throws SQLException {
        try {
            db.execSQL(sql);
        } catch (SQLiteException error) {
            throw new SQLException(error.getMessage(), error);
        }
    }

    @Override
    public void execute(String sql, List<Object> params) throws SQLException {
        try {
            db.execSQL(sql, params.toArray());
        } catch (SQLiteException error) {
            throw new SQLException(error.getMessage(), error);
        }
    }

    @Override
    public CapabilityRowCursor query(String sql, List<Object> params) throws SQLException {
        try {
            String[] args = new String[params.size()];
            for (int i = 0; i < params.size(); i++) {
                Object value = params.get(i);
                args[i] = value == null ? "" : value.toString();
            }
            return new AndroidRowCursor(db.rawQuery(sql, args));
        } catch (SQLiteException error) {
            throw new SQLException(error.getMessage(), error);
        }
    }

    @Override
    public void close() {
        db.close();
    }

    private static final class AndroidRowCursor implements CapabilityRowCursor {
        private final Cursor cursor;

        private AndroidRowCursor(Cursor cursor) {
            this.cursor = cursor;
        }

        @Override
        public boolean next() {
            return cursor.moveToNext();
        }

        @Override
        public String getString(String column) throws SQLException {
            int index = indexOf(column);
            return cursor.isNull(index) ? "" : cursor.getString(index);
        }

        @Override
        public double getDouble(String column) throws SQLException {
            int index = indexOf(column);
            return cursor.isNull(index) ? 0.0 : cursor.getDouble(index);
        }

        @Override
        public void close() {
            cursor.close();
        }

        private int indexOf(String column) throws SQLException {
            int direct = cursor.getColumnIndex(column);
            if (direct >= 0) {
                return direct;
            }
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                String name = cursor.getColumnName(i);
                if (column.equals(name) || name.endsWith("." + column)) {
                    return i;
                }
            }
            throw new SQLException("missing column " + column);
        }
    }
}
