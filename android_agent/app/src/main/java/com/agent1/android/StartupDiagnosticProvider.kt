package com.agent1.android

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.agent1.android.llm.CrashReporter
import com.agent1.android.productivity.logic.data.StartupTrace

/** 在 Application 之前安装崩溃捕获并写启动轨迹。 */
class StartupDiagnosticProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val ctx = context?.applicationContext ?: return false
        CrashReporter.install(ctx)
        StartupTrace.mark(ctx, "StartupDiagnosticProvider.onCreate")
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

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
