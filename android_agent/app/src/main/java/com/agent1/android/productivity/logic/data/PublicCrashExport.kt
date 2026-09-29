package com.agent1.android.productivity.logic.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * 把崩溃报告镜像到用户可见的「下载/Agent1/」，便于主 App 反复启动崩溃时仍可用文件管理器复制。
 * 诊断包 [com.agent1.android.diagnostic] 与主包共用同一路径（按主包 applicationId 命名子目录）。
 */
object PublicCrashExport {
    const val MAIN_APP_ID = "com.agent1.android"
    private const val FOLDER = "Agent1"
    private const val CRASH_FILE_NAME = "last_crash_report.txt"
    private const val STARTUP_FILE_NAME = "startup_trace.txt"

    /** 给用户看的说明（中文路径习惯）。 */
    fun userVisiblePathHint(): String =
        "文件管理器 → 下载 → $FOLDER → $MAIN_APP_ID →（$CRASH_FILE_NAME 或 $STARTUP_FILE_NAME）"

    fun mirrorFromMainApp(context: Context, report: String) {
        mirrorNamed(context, MAIN_APP_ID, CRASH_FILE_NAME, report)
    }

    fun readMainAppMirror(context: Context): String? = readNamedMirror(context, MAIN_APP_ID, CRASH_FILE_NAME)

    fun mirrorStartupTrace(context: Context, report: String) {
        mirrorNamed(context, MAIN_APP_ID, STARTUP_FILE_NAME, report)
    }

    fun readStartupTraceMirror(context: Context): String? =
        readNamedMirror(context, MAIN_APP_ID, STARTUP_FILE_NAME)

    private fun mirrorNamed(context: Context, ownerPackage: String, displayName: String, report: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeViaMediaStore(context, ownerPackage, displayName, report)
            } else {
                writeLegacyDownloads(ownerPackage, displayName, report)
            }
        }
    }

    private fun readNamedMirror(context: Context, ownerPackage: String, displayName: String): String? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                readViaMediaStore(context, ownerPackage, displayName)
            } else {
                readLegacyDownloads(ownerPackage, displayName)
            }
        }.getOrNull()
    }

    private fun relativePath(ownerPackage: String): String {
        return "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/$ownerPackage"
    }

    private fun writeViaMediaStore(
        context: Context,
        ownerPackage: String,
        displayName: String,
        report: String,
    ) {
        val resolver = context.applicationContext.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relative = relativePath(ownerPackage)
        val existing = resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
            arrayOf(displayName, "$relative/"),
            null,
        )
        existing?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                val uri = Uri.withAppendedPath(collection, id.toString())
                resolver.openOutputStream(uri, "wt")?.use { it.write(report.toByteArray(Charsets.UTF_8)) }
                return
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, relative)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values) ?: return
        resolver.openOutputStream(uri)?.use { it.write(report.toByteArray(Charsets.UTF_8)) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }

    private fun readViaMediaStore(context: Context, ownerPackage: String, displayName: String): String? {
        val resolver = context.applicationContext.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relative = relativePath(ownerPackage)
        resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
            arrayOf(displayName, "$relative/"),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val id = cursor.getLong(0)
            val uri = Uri.withAppendedPath(collection, id.toString())
            return resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }
        return null
    }

    private fun legacyFile(ownerPackage: String, displayName: String): File {
        @Suppress("DEPRECATION")
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(File(downloads, FOLDER), ownerPackage).apply { mkdirs() }.resolve(displayName)
    }

    private fun writeLegacyDownloads(ownerPackage: String, displayName: String, report: String) {
        legacyFile(ownerPackage, displayName).writeText(report)
    }

    private fun readLegacyDownloads(ownerPackage: String, displayName: String): String? {
        val file = legacyFile(ownerPackage, displayName)
        return if (file.exists()) file.readText() else null
    }
}
