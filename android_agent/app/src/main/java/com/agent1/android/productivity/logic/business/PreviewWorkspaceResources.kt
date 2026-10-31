package com.agent1.android.productivity.logic.business

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * 预览页用 Android 保留域名 `https://appassets.androidplatform.net/` 打开工作区文件。
 * 该域名没有公网网站，拦截漏掉时请求失败，不会打开别人的页面。
 * 相对图片、样式、脚本都落在这个源上，再由 WebView 拦截读盘；不打开 file://，也不放行跨文件读取。
 */
object PreviewWorkspaceResources {

    const val ORIGIN = "https://appassets.androidplatform.net"
    const val HOST = "appassets.androidplatform.net"

    fun documentUrl(workspaceRelativePath: String): String {
        val relative = normalize(workspaceRelativePath)
        if (relative.isEmpty()) return "$ORIGIN/"
        return "$ORIGIN/${encodePath(relative)}"
    }

    /** Markdown 所在目录，作为 `new URL(src, base)` 的基址，末尾带 `/`。 */
    fun directoryUrl(workspaceRelativeFile: String): String {
        val relative = normalize(workspaceRelativeFile)
        val parent = relative.substringBeforeLast('/', "")
        if (parent.isEmpty()) return "$ORIGIN/"
        return "$ORIGIN/${encodePath(parent)}/"
    }

    fun lookup(workspaceRoot: Path, rawUrl: String): Lookup {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return Lookup.NotOurs
        if (!"https".equals(uri.scheme, ignoreCase = true)) return Lookup.NotOurs
        if (!HOST.equals(uri.host, ignoreCase = true)) return Lookup.NotOurs
        val path = uri.path ?: return Lookup.Missing
        val relative = if (path.startsWith("/")) path.substring(1) else path
        if (relative.isEmpty() || relative.contains('\u0000')) return Lookup.Missing
        val file = SessionWorkspacePaths.resolveFile(workspaceRoot, relative) ?: return Lookup.Missing
        val (mime, encoding) = mimeAndEncoding(relative)
        return Lookup.Found(
            mimeType = mime,
            encoding = encoding,
            bytes = file.readBytes(),
        )
    }

    private fun normalize(raw: String): String {
        return raw.trim().replace('\\', '/').removePrefix("./").trimStart('/')
    }

    private fun encodePath(relative: String): String {
        return relative.split('/').joinToString("/") { segment ->
            URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")
        }
    }

    private fun mimeAndEncoding(relativePath: String): Pair<String, String?> {
        val ext = relativePath.substringAfterLast('.', "").lowercase()
        val mime = when (ext) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js", "mjs" -> "text/javascript"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "json" -> "application/json"
            "md", "markdown" -> "text/markdown"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
        val encoding = if (
            mime.startsWith("text/") ||
            mime == "application/json" ||
            mime == "image/svg+xml"
        ) {
            "utf-8"
        } else {
            null
        }
        return mime to encoding
    }

    sealed class Lookup {
        data object NotOurs : Lookup()
        data object Missing : Lookup()
        data class Found(
            val mimeType: String,
            val encoding: String?,
            val bytes: ByteArray,
        ) : Lookup()
    }
}
