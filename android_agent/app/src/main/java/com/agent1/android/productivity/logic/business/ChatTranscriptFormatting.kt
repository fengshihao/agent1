package com.agent1.android.productivity.logic.business

import android.graphics.BitmapFactory
import java.io.File
import java.nio.file.Path
import org.json.JSONObject

data class ToolResultDisplay(
    val summary: String,
    /** 工作区内相对路径，供 UI 加载预览。 */
    val workspaceImagePath: String? = null,
    val imageWarning: String? = null,
    /** 可打开/分享的非图片工作区文件。 */
    val workspaceFilePaths: List<String> = emptyList(),
)

object ChatTranscriptFormatting {

    /** 聊天气泡内正文上限，避免 Compose/Markdown 渲染超大字符串导致 OOM 或 ANR。 */
    const val UI_BUBBLE_MAX_CHARS = 20_000

    private val imageExt = setOf("png", "jpg", "jpeg", "webp", "gif")

    private val attachmentExt = setOf(
        "docx", "doc", "pdf", "xlsx", "xls", "pptx", "ppt", "md", "txt", "csv", "svg",
    )

    fun formatToolResult(raw: String, workspaceRoot: Path?): ToolResultDisplay {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) {
            val paths = extractPlainWorkspacePaths(trimmed)
            return ToolResultDisplay(
                summary = truncatePlain(trimmed),
                workspaceFilePaths = paths,
            )
        }
        return try {
            formatToolJson(JSONObject(trimmed), workspaceRoot)
        } catch (_: Exception) {
            ToolResultDisplay(summary = truncatePlain(trimmed))
        }
    }

    private fun formatToolJson(json: JSONObject, workspaceRoot: Path?): ToolResultDisplay {
        val ok = json.optBoolean("ok", true)
        if (!ok) {
            val error = json.optString("error", "").trim()
            return ToolResultDisplay(summary = truncatePlain(if (error.isNotEmpty()) error else json.toString()))
        }
        val outputPath = normalizeWorkspacePath(json.optString("outputPath", "").trim())
        if (outputPath.isNotEmpty() && isImagePath(outputPath)) {
            val bytes = json.optLong("outputBytes", -1L).takeIf { it >= 0 }
            val elapsed = json.optLong("elapsedMs", -1L).takeIf { it >= 0 }
            val resultType = json.optString("resultType", "").trim()
            val parts = buildList {
                add("已生成图片")
                add(outputPath)
                if (resultType.isNotEmpty()) add(resultType)
                bytes?.let { add("${it} 字节") }
                elapsed?.let { add("${it} ms") }
            }
            val file = workspaceRoot?.resolve(outputPath.removePrefix("./"))?.normalize()?.toFile()
            val warning = file?.let { validateImageFile(it) }
            return ToolResultDisplay(
                summary = parts.joinToString(" · "),
                workspaceImagePath = outputPath,
                imageWarning = warning,
            )
        }
        if (outputPath.isNotEmpty() && isAttachmentPath(outputPath)) {
            val parts = buildList {
                add("已生成文件")
                add(outputPath)
            }
            return ToolResultDisplay(
                summary = parts.joinToString(" · "),
                workspaceFilePaths = listOf(outputPath),
            )
        }
        val pathField = normalizeWorkspacePath(json.optString("path", "").trim())
        if (pathField.isNotEmpty() && isAttachmentPath(pathField)) {
            return ToolResultDisplay(
                summary = "输出: $pathField",
                workspaceFilePaths = listOf(pathField),
            )
        }
        val preview = json.optString("resultPreview", "").trim()
        if (preview.length > 280) {
            return ToolResultDisplay(summary = preview.take(280) + "…")
        }
        if (preview.isNotEmpty()) {
            return ToolResultDisplay(summary = preview)
        }
        return ToolResultDisplay(summary = truncatePlain(json.toString()))
    }

    private fun validateImageFile(file: File): String? {
        if (!file.isFile || file.length() == 0L) {
            return "文件不存在或大小为 0"
        }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (WorkspaceImageBytes.isBase64ImageText(file)) {
            val decoded = WorkspaceImageBytes.decodeBase64Payload(file)
            if (decoded == null) {
                return "无法解码为有效图片（可能空白或损坏）"
            }
            BitmapFactory.decodeByteArray(decoded, 0, decoded.size, opts)
        } else {
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            return "无法解码为有效图片（可能空白或损坏）"
        }
        return null
    }

    private fun isImagePath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext in imageExt) return true
        // webview_exec 自动落盘：tmp/webview_exec/*.b64（UTF-8 纯 Base64，见 WorkspaceImageBytes）
        return ext == "b64" && normalized.contains("tmp/webview_exec/")
    }

    fun truncateForUiDisplay(text: String, maxChars: Int = UI_BUBBLE_MAX_CHARS): String {
        val trimmed = text.trim()
        if (trimmed.length <= maxChars) return trimmed
        return trimmed.take(maxChars) +
            "\n\n…（界面仅展示前 $maxChars 字，完整共 ${trimmed.length} 字，见 workspace 或导出简报）"
    }

    /** 大表格/超长正文不走 Markdown 渲染，降低崩溃风险。 */
    fun shouldRenderAsMarkdown(content: String): Boolean {
        if (content.length > 6_000) return false
        val tableLines = content.lineSequence().count { it.trimStart().startsWith("|") }
        return tableLines <= 40
    }

    private fun truncatePlain(text: String, max: Int = 400): String {
        val oneLine = text.replace('\n', ' ').trim()
        return if (oneLine.length <= max) oneLine else oneLine.take(max) + "…"
    }

    /** 从助手 Markdown 正文中提取 `![](path)` 相对路径。 */
    fun extractMarkdownImagePaths(content: String): List<String> {
        val regex = Regex("""!\[[^\]]*]\(([^)]+)\)""")
        return regex.findAll(content).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
    }

    /** 从 Markdown 链接 `[label](path)` 提取工作区附件路径（非 http）。 */
    fun extractMarkdownFileLinks(content: String): List<String> {
        val regex = Regex("""(?<!!)\[[^\]]*]\(([^)]+)\)""")
        return regex.findAll(content)
            .map { normalizeWorkspacePath(it.groupValues[1].trim()) }
            .filter { it.isNotEmpty() && !it.startsWith("http://") && !it.startsWith("https://") }
            .filter { isWorkspaceFilePath(it) }
            .distinct()
            .toList()
    }

    /** 从正文中的裸路径（如 `out/report.docx`、`已写入: notes.md`）提取工作区文件。 */
    fun extractPlainWorkspacePaths(content: String): List<String> {
        if (content.isBlank()) return emptyList()
        val scan = content.replace('`', ' ')
        val extAlternation = (attachmentExt + imageExt).distinct().joinToString("|")
        val regex = Regex("""(?:\./|workspace/)?[A-Za-z0-9][A-Za-z0-9._/-]*\.(?:$extAlternation)\b""")
        return regex.findAll(scan)
            .map { normalizeWorkspacePath(it.value) }
            .filter { path -> !path.contains("://") }
            .filter { isWorkspaceFilePath(it) }
            .distinct()
            .toList()
    }

    /** 合并工具 JSON、Markdown 链接与正文裸路径，供 UI 展示可打开附件。 */
    fun mergeWorkspaceFilePaths(content: String, extra: List<String>): List<String> {
        return (extra + extractMarkdownFileLinks(content) + extractPlainWorkspacePaths(content))
            .map { normalizeWorkspacePath(it) }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    fun normalizeWorkspacePath(raw: String): String =
        SessionWorkspacePaths.normalizeWorkspaceRelativePath(raw)

    /** 将尚未写成 Markdown 链接的工作区路径改为 `[文件名](path)`，便于渲染可点链接。 */
    fun linkifyBareWorkspacePaths(content: String, paths: List<String>): String {
        if (paths.isEmpty()) return content
        var result = content
        val normalized = paths.map { normalizeWorkspacePath(it) }.distinct()
        for (path in normalized.sortedByDescending { it.length }) {
            if (result.contains("($path)")) continue
            val label = path.substringAfterLast('/').ifEmpty { path }
            val variants = listOf(path, "./$path", "workspace/$path", "/$path").distinct()
            for (variant in variants.sortedByDescending { it.length }) {
                if (result.contains(variant) && !result.contains("($path)")) {
                    result = result.replace(variant, "[$label]($path)")
                }
            }
        }
        return result
    }

    fun isWorkspaceFilePath(path: String): Boolean = isAttachmentPath(path) || isImagePath(path)

    private fun isAttachmentPath(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in attachmentExt
    }

    /** 将正文按已知工作区路径拆成普通文本段与路径段（供聊天气泡内联链接）。 */
    fun splitLinkifiedSegments(content: String, paths: List<String>): List<LinkifiedSegment> {
        if (content.isEmpty()) return emptyList()
        val normalized = paths.filter { it.isNotBlank() }.map { normalizeWorkspacePath(it) }.distinct()
        val aliases = normalized.flatMap { path ->
            listOf(path, "./$path", "workspace/$path")
        }.distinct().sortedByDescending { it.length }
        if (aliases.isEmpty()) return listOf(LinkifiedSegment(text = content))
        val out = mutableListOf<LinkifiedSegment>()
        var index = 0
        while (index < content.length) {
            val matchedAlias = aliases.firstOrNull { alias ->
                content.regionMatches(index, alias, 0, alias.length)
            }
            if (matchedAlias != null) {
                val canonical = normalizeWorkspacePath(matchedAlias)
                out.add(LinkifiedSegment(workspacePath = canonical))
                index += matchedAlias.length
            } else {
                val start = index
                index++
                while (index < content.length) {
                    val hit = aliases.any { alias -> content.regionMatches(index, alias, 0, alias.length) }
                    if (hit) break
                    index++
                }
                out.add(LinkifiedSegment(text = content.substring(start, index)))
            }
        }
        return out
    }
}

/** 聊天气泡内联路径片段。 */
data class LinkifiedSegment(
    val text: String = "",
    val workspacePath: String? = null,
)
