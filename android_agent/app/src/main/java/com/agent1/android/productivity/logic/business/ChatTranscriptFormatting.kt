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
            return ToolResultDisplay(summary = truncatePlain(trimmed))
        }
        return try {
            formatToolJson(JSONObject(trimmed), workspaceRoot)
        } catch (_: Exception) {
            ToolResultDisplay(summary = truncatePlain(trimmed))
        }
    }

    private fun formatToolJson(json: JSONObject, workspaceRoot: Path?): ToolResultDisplay {
        val outputPath = json.optString("outputPath", "").trim()
        if (outputPath.isNotEmpty() && isImagePath(outputPath)) {
            val bytes = json.optLong("outputBytes", -1L).takeIf { it >= 0 }
            val ok = json.optBoolean("ok", true)
            val elapsed = json.optLong("elapsedMs", -1L).takeIf { it >= 0 }
            val parts = buildList {
                add(if (ok) "已生成图片" else "图片工具返回异常")
                add(outputPath)
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
            val ok = json.optBoolean("ok", true)
            val parts = buildList {
                add(if (ok) "已生成文件" else "文件工具返回异常")
                add(outputPath)
            }
            return ToolResultDisplay(
                summary = parts.joinToString(" · "),
                workspaceFilePaths = listOf(outputPath),
            )
        }
        val pathField = json.optString("path", "").trim()
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
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            return "无法解码为有效图片（可能空白或损坏）"
        }
        return null
    }

    private fun isImagePath(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in imageExt
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
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() && !it.startsWith("http://") && !it.startsWith("https://") }
            .filter { isAttachmentPath(it) || isImagePath(it) }
            .distinct()
            .toList()
    }

    private fun isAttachmentPath(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in attachmentExt
    }
}
