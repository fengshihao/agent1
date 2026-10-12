package com.agent1.android.productivity.logic.business

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.nio.file.Path

/** 打开 / 分享 workspace 内用户文件（logic.business，无 Compose 依赖）。 */
object WorkspaceFileActions {

    /**
     * 是否改为 app 内置 WebView 预览（不外跳）。
     * 当前仅 html；由 ui 层决定具体预览路由（本类不依赖 Activity/UI 类型）。
     */
    fun isPreviewableInApp(relativePath: String): Boolean {
        val ext = relativePath.substringAfterLast('.', "").lowercase()
        return ext == "html" || ext == "htm"
    }

    fun mimeTypeForRelativePath(relativePath: String): String {
        val ext = relativePath.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc" -> "application/msword"
            "pdf" -> "application/pdf"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "xls" -> "application/vnd.ms-excel"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "ppt" -> "application/vnd.ms-powerpoint"
            "md" -> "text/markdown"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "html", "htm" -> "text/html"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }

    fun openWorkspaceFile(context: Context, workspaceRoot: Path, relativePath: String): Boolean {
        val file = SessionWorkspacePaths.resolveFile(workspaceRoot, relativePath) ?: run {
            // 文件不存在/未写完时此前静默返回，表现为“点击没反应”；给出可见提示。
            Toast.makeText(context, "文件不存在或已被移动：$relativePath", Toast.LENGTH_SHORT).show()
            return false
        }
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeTypeForRelativePath(relativePath))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess.also { ok ->
            if (!ok) {
                Toast.makeText(context, "未找到可打开此文件的应用", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareWorkspaceFile(context: Context, workspaceRoot: Path, relativePath: String): Boolean {
        val file = SessionWorkspacePaths.resolveFile(workspaceRoot, relativePath) ?: run {
            Toast.makeText(context, "文件不存在或已被移动：$relativePath", Toast.LENGTH_SHORT).show()
            return false
        }
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeTypeForRelativePath(relativePath)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "分享 ${file.name}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(chooser) }.isSuccess
    }

    /** 多选分享（产物库）：全部文件扩展名相同时带具体 MIME，否则回退通用二进制。 */
    fun shareFiles(context: Context, files: List<File>): Boolean {
        val regular = files.filter { it.isFile }
        if (regular.isEmpty()) return false
        val authority = "${context.packageName}.fileprovider"
        val uris = ArrayList(
            regular.map { file -> FileProvider.getUriForFile(context, authority, file) },
        )
        val extensions = regular.map { it.extension.lowercase() }.toSet()
        val type = if (extensions.size == 1) {
            mimeTypeForRelativePath(regular.first().name)
        } else {
            "application/octet-stream"
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            setType(type)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "分享 ${regular.size} 个文件").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(chooser) }.isSuccess
    }

    /** 多选分享产物库条目：UI 层只传 ArtifactRef，文件解析留在本层（ui.view 禁 java.io.File）。 */
    fun shareArtifactFiles(
        context: Context,
        refs: List<ArtifactLibraryStore.ArtifactRef>,
    ): Boolean {
        val files = refs.mapNotNull { ref -> ArtifactLibraryStore.resolveArtifactFile(context, ref) }
        if (files.isEmpty()) {
            Toast.makeText(context, "文件不存在或已被移动", Toast.LENGTH_SHORT).show()
            return false
        }
        return shareFiles(context, files)
    }
}
