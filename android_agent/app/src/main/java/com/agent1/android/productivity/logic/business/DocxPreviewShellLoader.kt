package com.agent1.android.productivity.logic.business

import android.content.Context
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** 预览页 HTML：优先 APK 内烘焙副本，避免 WebView / CDN 缓存旧 shell。 */
object DocxPreviewShellLoader {

    const val ASSET_NAME = "docx-preview-shell.html"
    const val BASE_URL = "https://agentone.guoyoutech.cn/host-web/shell/docx-preview/1/"

    private const val TIMEOUT_MS = 20_000

    fun loadHtml(context: Context): String? {
        val bundled = runCatching {
            context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
        if (!bundled.isNullOrBlank() && !bundled.contains("__JSZIP_URL__")) {
            return bundled
        }
        return null
    }

    fun fetchHtml(shellUrl: String): String? {
        val trimmed = shellUrl.trim()
        if (trimmed.isEmpty()) return null
        val conn = (URL(trimmed).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
        }
        return try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** `loadDataWithBaseURL` 的 base，使 shell 内绝对 script URL 与 https 源一致。 */
    fun baseUrlFor(shellUrl: String): String {
        val uri = runCatching { URI(shellUrl.trim()) }.getOrNull() ?: return "https://agentone.guoyoutech.cn/"
        val host = uri.host ?: "agentone.guoyoutech.cn"
        val path = uri.path ?: "/"
        val dir = if (path.endsWith("/")) path else path.substringBeforeLast('/') + "/"
        return "https://$host$dir"
    }
}
