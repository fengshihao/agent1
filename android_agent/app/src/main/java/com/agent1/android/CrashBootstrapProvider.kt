package com.agent1.android

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.agent1.android.llm.CrashReporter

/**
 * 在 [Application] 之前注册未捕获异常处理器（不读磁盘上的旧崩溃，避免 attachBaseContext 过早 IO）。
 */
class CrashBootstrapProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val ctx = context ?: return true
        CrashReporter.installUncaughtHandler(ctx)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
