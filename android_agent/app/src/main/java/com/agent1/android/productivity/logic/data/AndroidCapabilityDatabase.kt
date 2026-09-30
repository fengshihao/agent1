package com.agent1.android.productivity.logic.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.agent1.javaagent.capability.CapabilityDatabase
import com.agent1.javaagent.capability.CapabilityRowCursor
import java.nio.file.Path
import java.sql.SQLException

/**
 * 系统 SQLite（framework），与桌面同一套 capability / FTS5 SQL。
 * 不使用 sqlite-jdbc，避免把桌面 .so 打进 APK。
 */
class AndroidCapabilityDatabase private constructor(
    private val db: SQLiteDatabase,
) : CapabilityDatabase {

    override fun execute(sql: String) {
        try {
            db.execSQL(sql)
        } catch (error: SQLiteException) {
            throw SQLException(error.message, error)
        }
    }

    override fun execute(sql: String, params: List<Any?>) {
        try {
            db.execSQL(sql, params.map { it ?: "" }.toTypedArray())
        } catch (error: SQLiteException) {
            throw SQLException(error.message, error)
        }
    }

    override fun query(sql: String, params: List<Any?>): CapabilityRowCursor {
        try {
            val cursor = db.rawQuery(sql, params.map { it?.toString().orEmpty() }.toTypedArray())
            return AndroidRowCursor(cursor)
        } catch (error: SQLiteException) {
            throw SQLException(error.message, error)
        }
    }

    override fun close() {
        db.close()
    }

    private class AndroidRowCursor(
        private val cursor: Cursor,
    ) : CapabilityRowCursor {
        override fun next(): Boolean = cursor.moveToNext()

        override fun getString(column: String): String {
            val index = indexOf(column)
            return if (cursor.isNull(index)) "" else cursor.getString(index).orEmpty()
        }

        override fun getDouble(column: String): Double {
            val index = indexOf(column)
            return if (cursor.isNull(index)) 0.0 else cursor.getDouble(index)
        }

        override fun close() {
            cursor.close()
        }

        private fun indexOf(column: String): Int {
            val direct = cursor.getColumnIndex(column)
            if (direct >= 0) {
                return direct
            }
            for (i in 0 until cursor.columnCount) {
                val name = cursor.getColumnName(i)
                if (name == column || name.endsWith(".$column")) {
                    return i
                }
            }
            throw SQLException("missing column $column")
        }
    }

    companion object {
        @JvmStatic
        fun open(databaseFile: Path): AndroidCapabilityDatabase {
            val file = databaseFile.toFile()
            file.parentFile?.mkdirs()
            return AndroidCapabilityDatabase(SQLiteDatabase.openOrCreateDatabase(file, null))
        }
    }
}
