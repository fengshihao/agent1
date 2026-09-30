package com.agent1.android.productivity.logic.business

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.min

/** 将用户从系统选择器提供的 content URI 复制进会话 workspace。 */
object WorkspaceFileImport {

    private const val IMPORT_DIR = "imports"

    data class Result(
        val workspaceRelativePath: String,
        val displayName: String,
    )

    fun copyContentUrisToWorkspace(
        context: Context,
        workspaceRoot: Path,
        uris: List<Uri>,
    ): List<Result> {
        if (uris.isEmpty()) return emptyList()
        val importsDir = workspaceRoot.resolve(IMPORT_DIR)
        Files.createDirectories(importsDir)
        val root = workspaceRoot.toAbsolutePath().normalize()
        return uris.mapNotNull { uri ->
            copyOne(context, importsDir, root, uri)
        }
    }

    private fun copyOne(
        context: Context,
        importsDir: Path,
        workspaceRoot: Path,
        uri: Uri,
    ): Result? {
        val displayName = queryDisplayName(context, uri) ?: "import-${System.currentTimeMillis()}"
        val safeBase = sanitizeFileName(displayName)
        val target = uniqueFile(importsDir, safeBase)
        val normalized = target.toAbsolutePath().normalize()
        if (!normalized.startsWith(workspaceRoot)) return null
        context.contentResolver.openInputStream(uri)?.use { input ->
            Files.newOutputStream(target).use { out ->
                input.copyTo(out)
            }
        } ?: return null
        if (!Files.isRegularFile(target) || Files.size(target) <= 0L) {
            Files.deleteIfExists(target)
            return null
        }
        val relative = workspaceRoot.relativize(normalized).toString().replace('\\', '/')
        return Result(relative, displayName)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx)?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
    }

    private fun sanitizeFileName(name: String): String {
        val trimmed = name.trim().ifEmpty { "file" }
        val cleaned = trimmed.map { ch ->
            if (ch.isLetterOrDigit() || ch in "._- ()[]") ch else '_'
        }.joinToString("")
        return cleaned.take(min(cleaned.length, 120))
    }

    private fun uniqueFile(dir: Path, baseName: String): Path {
        var candidate = dir.resolve(baseName)
        if (!Files.exists(candidate)) return candidate
        val dot = baseName.lastIndexOf('.')
        val stem = if (dot > 0) baseName.substring(0, dot) else baseName
        val ext = if (dot > 0) baseName.substring(dot) else ""
        var n = 2
        while (n < 10_000) {
            candidate = dir.resolve("$stem-$n$ext")
            if (!Files.exists(candidate)) return candidate
            n++
        }
        return dir.resolve("$stem-${System.currentTimeMillis()}$ext")
    }
}
