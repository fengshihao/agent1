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

    /** Markdown 渲染器用此前缀，避免相对路径链接被当成纯文本。 */
    const val WORKSPACE_FILE_HREF_PREFIX = "agent1-file:"

    /** Markdown 链接/图片结构（`[label](dest)` / `![label](dest)`），提取与替换时整体保护。 */
    private val markdownLinkSpan = Regex("""!?\[[^\]]*]\([^)]*\)""")

    private val imageExt = setOf("png", "jpg", "jpeg", "webp", "gif")

    private val attachmentExt = setOf(
        "docx", "doc", "pdf", "xlsx", "xls", "pptx", "ppt", "md", "txt", "csv", "svg",
        // html 走 app 内置预览（见 doc/规划/自进化Agent/18-内置HTML预览与点选审查.md）
        "html", "htm",
    )

    fun formatToolResult(raw: String, workspaceRoot: Path?): ToolResultDisplay {
        val trimmed = SessionWorkspacePaths.scrubWorkspaceAbsolute(raw.trim(), workspaceRoot)
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
            .map {
                normalizeWorkspacePath(
                    it.groupValues[1].trim().removePrefix(WORKSPACE_FILE_HREF_PREFIX),
                )
            }
            .filter { it.isNotEmpty() && !it.startsWith("http://") && !it.startsWith("https://") }
            .filter { isWorkspaceFilePath(it) }
            .distinct()
            .toList()
    }

    /** 从正文中的裸路径（如 `out/report.docx`、`已写入: notes.md`）提取工作区文件。 */
    fun extractPlainWorkspacePaths(content: String): List<String> {
        if (content.isBlank()) return emptyList()
        val extAlternation = (attachmentExt + imageExt).distinct().joinToString("|")
        val regex = Regex(
            """(?:\./|workspace/)?[\p{L}\p{N}][\p{L}\p{N}._/-]*\.(?:$extAlternation)\b""",
        )
        // 只扫普通文本段：Markdown 链接 label 是显示文字，不是路径
        return splitMarkdownProtectedSegments(content)
            .filterNot { it.isMarkdownLink }
            .flatMap { seg -> regex.findAll(seg.text.replace('`', ' ')) }
            .map { normalizeWorkspacePath(it.value) }
            .filter { path -> !path.contains("://") }
            .filter { isWorkspaceFilePath(it) }
            .distinct()
            .toList()
    }

    /** 合并工具 JSON、Markdown 链接与正文裸路径，供 UI 展示可打开附件。 */
    fun mergeWorkspaceFilePaths(
        content: String,
        extra: List<String>,
        workspaceRoot: Path? = null,
    ): List<String> {
        return (extra + extractMarkdownFileLinks(content) +
            extractMarkdownImagePaths(content) +
            extractPlainWorkspacePaths(content))
            .map { normalizeWorkspacePath(it, workspaceRoot) }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    fun normalizeWorkspacePath(raw: String, workspaceRoot: Path? = null): String =
        SessionWorkspacePaths.canonicalWorkspaceRelative(raw, workspaceRoot)

    /** 将尚未写成 Markdown 链接的工作区路径改为 `[文件名](path)`，便于渲染可点链接。 */
    fun linkifyBareWorkspacePaths(content: String, paths: List<String>): String {
        if (paths.isEmpty()) return content
        val normalized = paths.map { normalizeWorkspacePath(it) }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedByDescending { it.length }
        if (normalized.isEmpty()) return content
        val aliases = normalized
            .flatMap { path -> listOf(path, "./$path", "workspace/$path", "/$path") }
            .distinct()
            .sortedByDescending { it.length }
        // 已有的 Markdown 链接整体保护，避免把 `[x](out/x.docx)` 的目标路径再替换撕碎
        return splitMarkdownProtectedSegments(content).joinToString("") { seg ->
            if (seg.isMarkdownLink) seg.text else linkifyPlainText(seg.text, aliases)
        }
    }

    /** 普通文本段内逐位匹配裸路径；带左边界，避免命中更长路径的尾部（如 `out/` 里的 `/x.docx`）。 */
    private fun linkifyPlainText(text: String, aliases: List<String>): String {
        if (text.isEmpty()) return text
        val out = StringBuilder()
        var index = 0
        outer@ while (index < text.length) {
            for (alias in aliases) {
                if (!text.regionMatches(index, alias, 0, alias.length)) continue
                if (index > 0 && isPathChar(text[index - 1])) continue
                val path = normalizeWorkspacePath(alias)
                if (path.isEmpty() || !isWorkspaceFilePath(path)) continue
                val label = path.substringAfterLast('/').ifEmpty { path }
                out.append("[$label]($path)")
                index += alias.length
                continue@outer
            }
            out.append(text[index])
            index++
        }
        return out.toString()
    }

    /** 切成普通文本段与 Markdown 链接/图片结构段；结构段在提取/替换中原样保留。 */
    private fun splitMarkdownProtectedSegments(content: String): List<ProtectedSegment> {
        if (content.isEmpty()) return emptyList()
        val out = mutableListOf<ProtectedSegment>()
        var index = 0
        while (index < content.length) {
            val match = markdownLinkSpan.find(content, index)
            if (match == null) {
                out.add(ProtectedSegment(content.substring(index), isMarkdownLink = false))
                break
            }
            if (match.range.first > index) {
                out.add(ProtectedSegment(content.substring(index, match.range.first), isMarkdownLink = false))
            }
            out.add(ProtectedSegment(match.value, isMarkdownLink = true))
            index = match.range.last + 1
        }
        return out
    }

    private data class ProtectedSegment(val text: String, val isMarkdownLink: Boolean)

    private fun isPathChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '.' || c == '_' || c == '-' || c == '/'

    /**
     * 把工作区文件 Markdown 链接改成带 scheme 的 href，供 Markdown 组件画成超链接。
     * 图片 `![](path)` 保持相对路径，交给 ImageTransformer。
     */
    fun rewriteWorkspaceMarkdownHrefs(content: String): String {
        if (content.isEmpty()) return content
        val regex = Regex("""(?<!!)\[[^\]]*]\(([^)]+)\)""")
        return regex.replace(content) { match ->
            val raw = match.groupValues[1].trim()
            if (raw.startsWith("http://") ||
                raw.startsWith("https://") ||
                raw.startsWith(WORKSPACE_FILE_HREF_PREFIX)
            ) {
                return@replace match.value
            }
            val path = normalizeWorkspacePath(raw)
            if (path.isEmpty() || !isWorkspaceFilePath(path)) return@replace match.value
            val label = match.value.substringAfter('[').substringBefore(']')
            "[$label]($WORKSPACE_FILE_HREF_PREFIX$path)"
        }
    }

    /** 正文下方单独预览的图片（非 Markdown 内联 `![](path)`）。 */
    fun trailingImagePreviewPaths(
        content: String,
        workspaceFilePaths: List<String>,
        workspaceRoot: java.nio.file.Path?,
    ): List<String> {
        if (content.isBlank() && workspaceFilePaths.isEmpty()) return emptyList()
        val logical = SessionWorkspacePaths.scrubWorkspaceAbsolute(content, workspaceRoot)
        val paths = mergeWorkspaceFilePaths(logical, workspaceFilePaths, workspaceRoot)
        val inline = extractMarkdownImagePaths(logical)
            .map { normalizeWorkspacePath(it, workspaceRoot) }
            .filter { it.isNotEmpty() && isImagePath(it) }
            .toSet()
        return paths.filter { isImageWorkspacePath(it) && it !in inline }
    }

    fun isWorkspaceFilePath(path: String): Boolean = isAttachmentPath(path) || isImagePath(path)

    fun isImageWorkspacePath(path: String): Boolean = isImagePath(path)

    private fun isAttachmentPath(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in attachmentExt
    }

    /** 将正文按已知工作区路径拆成普通文本段与路径段（供聊天气泡内联链接）。 */
    fun splitLinkifiedSegments(
        content: String,
        paths: List<String>,
        workspaceRoot: Path? = null,
    ): List<LinkifiedSegment> {
        if (content.isEmpty()) return emptyList()
        val normalized = (
            paths +
                extractMarkdownFileLinks(content) +
                extractMarkdownImagePaths(content).map { normalizeWorkspacePath(it, workspaceRoot) }
            )
            .filter { it.isNotBlank() }
            .map { normalizeWorkspacePath(it, workspaceRoot) }
            .distinct()
        val markdownImagePaths = extractMarkdownImagePaths(content)
            .map { normalizeWorkspacePath(it, workspaceRoot) }
            .filter { it.isNotEmpty() && isImagePath(it) }
            .toSet()
        val aliases = normalized.flatMap { path ->
            listOf(path, "./$path", "workspace/$path")
        }.distinct().sortedByDescending { it.length }
        val bareMatchAliases = aliases.filter { alias ->
            val canonical = normalizeWorkspacePath(alias, workspaceRoot)
            !(canonical in markdownImagePaths && isImagePath(canonical))
        }
        val out = mutableListOf<LinkifiedSegment>()
        var index = 0
        while (index < content.length) {
            if (content[index] == '!' &&
                index + 1 < content.length &&
                content[index + 1] == '['
            ) {
                val imageLink = parseWorkspaceMarkdownLink(
                    content,
                    index + 1,
                    workspaceRoot,
                    allowImageBang = true,
                )
                if (imageLink != null) {
                    out.add(
                        LinkifiedSegment(
                            text = imageLink.label.ifBlank {
                                imageLink.path.substringAfterLast('/').ifEmpty { imageLink.path }
                            },
                            workspacePath = imageLink.path,
                        ),
                    )
                    index += 1 + imageLink.span
                    continue
                }
            }
            val markdownLink = parseWorkspaceMarkdownLink(content, index, workspaceRoot)
            if (markdownLink != null) {
                out.add(
                    LinkifiedSegment(
                        text = markdownLink.label,
                        workspacePath = markdownLink.path,
                    ),
                )
                index += markdownLink.span
                continue
            }
            val matchedAlias = bareMatchAliases.firstOrNull { alias ->
                content.regionMatches(index, alias, 0, alias.length) &&
                    (index == 0 || !isPathChar(content[index - 1]))
            }
            if (matchedAlias != null) {
                val canonical = normalizeWorkspacePath(matchedAlias, workspaceRoot)
                out.add(LinkifiedSegment(workspacePath = canonical))
                index += matchedAlias.length
            } else {
                val start = index
                index++
                while (index < content.length) {
                    if (parseWorkspaceMarkdownLink(content, index, workspaceRoot) != null) break
                    if (content[index] == '!' &&
                        index + 1 < content.length &&
                        content[index + 1] == '[' &&
                        parseWorkspaceMarkdownLink(
                            content,
                            index + 1,
                            workspaceRoot,
                            allowImageBang = true,
                        ) != null
                    ) {
                        break
                    }
                    val hit = bareMatchAliases.any { alias ->
                        content.regionMatches(index, alias, 0, alias.length) &&
                            (index == 0 || !isPathChar(content[index - 1]))
                    }
                    if (hit) break
                    index++
                }
                out.add(LinkifiedSegment(text = content.substring(start, index)))
            }
        }
        return out.ifEmpty { listOf(LinkifiedSegment(text = content)) }
    }

    private fun parseWorkspaceMarkdownLink(
        content: String,
        index: Int,
        workspaceRoot: Path? = null,
        allowImageBang: Boolean = false,
    ): ParsedWorkspaceMarkdownLink? {
        if (index >= content.length || content[index] != '[') return null
        if (!allowImageBang && index > 0 && content[index - 1] == '!') return null
        val closeBracket = content.indexOf(']', startIndex = index + 1)
        if (closeBracket < 0 || closeBracket + 1 >= content.length || content[closeBracket + 1] != '(') {
            return null
        }
        val closeParen = content.indexOf(')', startIndex = closeBracket + 2)
        if (closeParen < 0) return null
        val rawHref = content.substring(closeBracket + 2, closeParen).trim()
            .removePrefix(WORKSPACE_FILE_HREF_PREFIX)
        if (rawHref.startsWith("http://") || rawHref.startsWith("https://")) return null
        val path = normalizeWorkspacePath(rawHref, workspaceRoot)
        if (path.isEmpty() || !isWorkspaceFilePath(path)) return null
        val label = content.substring(index + 1, closeBracket).ifBlank {
            path.substringAfterLast('/').ifEmpty { path }
        }
        return ParsedWorkspaceMarkdownLink(
            label = label,
            path = path,
            span = closeParen + 1 - index,
        )
    }

    private data class ParsedWorkspaceMarkdownLink(
        val label: String,
        val path: String,
        val span: Int,
    )
}

/** 聊天气泡内联路径片段。 */
data class LinkifiedSegment(
    val text: String = "",
    val workspacePath: String? = null,
)
