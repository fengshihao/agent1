package com.agent1.android.productivity.logic.business

import java.io.File
import java.util.Base64

/**
 * webview_exec 把 return 值按 UTF-8 写入 output_path。图片路径上常见的是纯 Base64 文本，
 * 不是 PNG/JPEG 二进制。
 */
object WorkspaceImageBytes {

    private const val PEEK_BYTES = 80

    fun isBase64ImageText(path: String): Boolean = isBase64ImageText(File(path))

    fun decodeBase64Payload(path: String): ByteArray? = decodeBase64Payload(File(path))

    fun isBase64ImageText(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) {
            return false
        }
        val prefix = peekPrefix(file) ?: return false
        if (isBinaryImage(prefix)) {
            return false
        }
        val body = prefix.substringAfter("base64,", prefix).trimStart()
        return body.startsWith("iVBOR") ||
            body.startsWith("/9j/") ||
            body.startsWith("R0lGOD") ||
            body.startsWith("UklGR")
    }

    fun decodeBase64Payload(file: File): ByteArray? {
        if (!isBase64ImageText(file)) {
            return null
        }
        val raw = file.readText().trim()
        val payload = raw.substringAfter("base64,", raw).trim()
        return try {
            Base64.getMimeDecoder().decode(payload)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun peekPrefix(file: File): String? {
        return file.inputStream().use { input ->
            val buffer = ByteArray(PEEK_BYTES)
            val read = input.read(buffer)
            if (read <= 0) {
                null
            } else {
                String(buffer, 0, read, Charsets.ISO_8859_1).trimStart()
            }
        }
    }

    private fun isBinaryImage(prefix: String): Boolean {
        return prefix.startsWith("\u0089PNG") ||
            prefix.startsWith("\u00FF\u00D8") ||
            prefix.startsWith("GIF8") ||
            prefix.startsWith("RIFF")
    }
}
