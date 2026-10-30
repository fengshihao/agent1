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

    /** 系统选择器返回后、复制进 workspace 前，用于展示待添加列表。 */
    fun resolveDisplayName(context: Context, uri: Uri): String {
        return queryDisplayName(context, uri)
            ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "file" }
    }

    data class BatchResult(
        val imported: List<Result>,
        val rejectedOversized: Int,
    )

    fun copyContentUrisToWorkspace(
        context: Context,
        workspaceRoot: Path,
        uris: List<Uri>,
        maxBytes: Long = SessionAttachmentLimits.MAX_FILE_BYTES,
    ): BatchResult {
        if (uris.isEmpty()) return BatchResult(emptyList(), 0)
        val importsDir = workspaceRoot.resolve(IMPORT_DIR)
        Files.createDirectories(importsDir)
        val root = workspaceRoot.toAbsolutePath().normalize()
        var rejectedOversized = 0
        val imported = uris.mapNotNull { uri ->
            when (val outcome = copyOne(context, importsDir, root, uri, maxBytes)) {
                is CopyOutcome.Imported -> outcome.result
                CopyOutcome.Oversized -> {
                    rejectedOversized++
                    null
                }
                CopyOutcome.Failed -> null
            }
        }
        return BatchResult(imported, rejectedOversized)
    }

    /** 分享纯文本时写入 `imports/shared-text-<ts>.txt`。 */
    fun writeSharedTextToImports(workspaceRoot: Path, text: String): Result? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        val importsDir = workspaceRoot.resolve(IMPORT_DIR)
        Files.createDirectories(importsDir)
        val root = workspaceRoot.toAbsolutePath().normalize()
        val baseName = "shared-text-${System.currentTimeMillis()}.txt"
        val target = uniqueFile(importsDir, sanitizeFileName(baseName))
        val normalized = target.toAbsolutePath().normalize()
        if (!normalized.startsWith(root)) {
            return null
        }
        val bytes = trimmed.toByteArray(Charsets.UTF_8)
        if (bytes.size > SessionAttachmentLimits.MAX_FILE_BYTES) {
            return null
        }
        Files.write(normalized, bytes)
        val relative = root.relativize(normalized).toString().replace('\\', '/')
        return Result(relative, target.fileName.toString())
    }

    /**
     * 从本地已存在的文件（如其他会话的 AI 产物）复制进 `workspace/imports/`，
     * 与系统选择器导入同一落点与命名规则，使「插入会话」与底部附件按钮效果一致。
     */
    fun copyLocalFileToImports(
        workspaceRoot: Path,
        source: Path,
        displayName: String,
        maxBytes: Long = SessionAttachmentLimits.MAX_FILE_BYTES,
    ): Result? {
        if (!Files.isRegularFile(source)) return null
        val sourceSize = Files.size(source)
        if (sourceSize <= 0L || sourceSize > maxBytes) return null
        val importsDir = workspaceRoot.resolve(IMPORT_DIR)
        Files.createDirectories(importsDir)
        val root = workspaceRoot.toAbsolutePath().normalize()
        val name = displayName.ifBlank {
            source.fileName?.toString().orEmpty().ifBlank { "file" }
        }
        val target = uniqueFile(importsDir, sanitizeFileName(name))
        val normalized = target.toAbsolutePath().normalize()
        if (!normalized.startsWith(root)) return null
        Files.copy(source, normalized)
        if (!Files.isRegularFile(normalized) || Files.size(normalized) <= 0L) {
            Files.deleteIfExists(normalized)
            return null
        }
        val relative = root.relativize(normalized).toString().replace('\\', '/')
        return Result(relative, name)
    }

    private sealed class CopyOutcome {
        data class Imported(val result: Result) : CopyOutcome()
        data object Oversized : CopyOutcome()
        data object Failed : CopyOutcome()
    }

    private fun copyOne(
        context: Context,
        importsDir: Path,
        workspaceRoot: Path,
        uri: Uri,
        maxBytes: Long,
    ): CopyOutcome {
        val knownSize = queryContentLength(context, uri)
        if (knownSize != null) {
            if (knownSize <= 0L) return CopyOutcome.Failed
            if (knownSize > maxBytes) return CopyOutcome.Oversized
        }
        val displayName = queryDisplayName(context, uri) ?: "import-${System.currentTimeMillis()}"
        val safeBase = sanitizeFileName(displayName)
        val target = uniqueFile(importsDir, safeBase)
        val normalized = target.toAbsolutePath().normalize()
        if (!normalized.startsWith(workspaceRoot)) return CopyOutcome.Failed
        val copyResult = context.contentResolver.openInputStream(uri)?.use { input ->
            copyStreamUpToLimit(input, target, maxBytes)
        } ?: LimitedCopyResult.FAILED
        if (copyResult != LimitedCopyResult.OK) {
            Files.deleteIfExists(target)
            return when (copyResult) {
                LimitedCopyResult.TOO_LARGE -> CopyOutcome.Oversized
                LimitedCopyResult.FAILED,
                LimitedCopyResult.OK,
                -> CopyOutcome.Failed
            }
        }
        if (!Files.isRegularFile(target) || Files.size(target) <= 0L) {
            Files.deleteIfExists(target)
            return CopyOutcome.Failed
        }
        val relative = workspaceRoot.relativize(normalized).toString().replace('\\', '/')
        return CopyOutcome.Imported(Result(relative, displayName))
    }

    private enum class LimitedCopyResult {
        OK,
        TOO_LARGE,
        FAILED,
    }

    private fun copyStreamUpToLimit(input: InputStream, target: Path, maxBytes: Long): LimitedCopyResult {
        val buffer = ByteArray(8192)
        var total = 0L
        try {
            Files.newOutputStream(target).use { out ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) {
                        return LimitedCopyResult.TOO_LARGE
                    }
                    out.write(buffer, 0, read)
                }
            }
        } catch (_: Exception) {
            return LimitedCopyResult.FAILED
        }
        return if (total > 0L) LimitedCopyResult.OK else LimitedCopyResult.FAILED
    }

    private fun queryContentLength(context: Context, uri: Uri): Long? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst()) {
                if (cursor.isNull(idx)) return null
                return cursor.getLong(idx)
            }
        }
        return null
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
