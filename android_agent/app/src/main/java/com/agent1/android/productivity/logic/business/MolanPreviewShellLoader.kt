package com.agent1.android.productivity.logic.business

import android.content.Context
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * 墨览 Markdown 预览：APK 内烘焙 embed shell（script URL 指向 guoyoutech CDN），
 * 运行时经 [com.agent1.javaagent.catalog.HostWebRegistrySync] 可升级 shell 地址。
 */
object MolanPreviewShellLoader {

    const val ASSET_NAME = "molan-md-shell.html"

    const val BASE_URL = "https://agentone.guoyoutech.cn/host-web/molan-embed/1/"

    private const val TIMEOUT_MS = 20_000

    fun loadHtml(context: Context): String? {
        val bundled = runCatching {
            context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
        if (!bundled.isNullOrBlank() && !bundled.contains("__MOLAN_EMBED_BASE__")) {
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

    fun baseUrlFor(shellUrl: String): String {
        val uri = runCatching { URI(shellUrl.trim()) }.getOrNull()
            ?: return BASE_URL
        val host = uri.host ?: "agentone.guoyoutech.cn"
        val path = uri.path ?: "/"
        val dir = if (path.endsWith("/")) path else path.substringBeforeLast('/') + "/"
        return "https://$host$dir"
    }
}
