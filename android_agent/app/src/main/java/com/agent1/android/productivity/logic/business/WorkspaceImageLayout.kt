package com.agent1.android.productivity.logic.business

import android.graphics.BitmapFactory
import java.io.File
import java.nio.file.Paths

/** 本地工作区图片尺寸（首帧占位，避免 LazyColumn 条目在 Coil 解码后突然长高）。 */
object WorkspaceImageLayout {

    const val DEFAULT_ASPECT_RATIO = 4f / 3f

    fun aspectRatio(workspaceAbsolutePath: String, workspaceRelativePath: String): Float {
        if (workspaceAbsolutePath.isBlank()) return DEFAULT_ASPECT_RATIO
        val file = SessionWorkspacePaths.resolveFile(
            Paths.get(workspaceAbsolutePath),
            workspaceRelativePath,
        ) ?: return DEFAULT_ASPECT_RATIO
        return aspectRatioFromFile(file)
    }

    fun aspectRatioFromResolvedLink(workspaceAbsolutePath: String, markdownLink: String): Float {
        if (workspaceAbsolutePath.isBlank()) return DEFAULT_ASPECT_RATIO
        val file = SessionWorkspacePaths.resolveFile(Paths.get(workspaceAbsolutePath), markdownLink)
            ?: return DEFAULT_ASPECT_RATIO
        return aspectRatioFromFile(file)
    }

    private fun aspectRatioFromFile(file: File): Float {
        if (!file.isFile || file.length() == 0L) {
            return DEFAULT_ASPECT_RATIO
        }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (WorkspaceImageBytes.isBase64ImageText(file)) {
            val decoded = WorkspaceImageBytes.decodeBase64Payload(file) ?: return DEFAULT_ASPECT_RATIO
            BitmapFactory.decodeByteArray(decoded, 0, decoded.size, opts)
        } else {
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }
        val w = opts.outWidth
        val h = opts.outHeight
        if (w <= 0 || h <= 0) {
            return DEFAULT_ASPECT_RATIO
        }
        return w.toFloat() / h.toFloat()
    }
}
