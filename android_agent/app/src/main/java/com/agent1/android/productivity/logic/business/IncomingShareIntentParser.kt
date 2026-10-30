package com.agent1.android.productivity.logic.business

import android.content.Intent
import android.net.Uri
import android.os.Build

/** 其他 App 「分享」到 Agent1 的解析结果。 */
data class IncomingSharePayload(
    val fileUris: List<Uri> = emptyList(),
    val sharedText: String? = null,
) {
    fun isEmpty(): Boolean = fileUris.isEmpty() && sharedText.isNullOrBlank()
}

object IncomingShareIntentParser {

    fun parse(intent: Intent?): IncomingSharePayload? {
        if (intent == null) {
            return null
        }
        return when (intent.action) {
            Intent.ACTION_SEND -> parseSend(intent)
            Intent.ACTION_SEND_MULTIPLE -> parseSendMultiple(intent)
            else -> null
        }
    }

    private fun parseSend(intent: Intent): IncomingSharePayload? {
        val stream = parcelableExtra(intent, Intent.EXTRA_STREAM)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
        val type = intent.type.orEmpty()
        val uris = when {
            stream != null -> listOf(stream)
            type.startsWith("text/") && !text.isNullOrBlank() -> emptyList()
            else -> emptyList()
        }
        val payload = IncomingSharePayload(fileUris = uris, sharedText = text)
        return payload.takeUnless { it.isEmpty() }
    }

    private fun parseSendMultiple(intent: Intent): IncomingSharePayload? {
        val streams = parcelableArrayListExtra(intent, Intent.EXTRA_STREAM)
        if (streams.isEmpty()) {
            return null
        }
        return IncomingSharePayload(fileUris = streams)
    }

    private fun parcelableExtra(intent: Intent, key: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key)
        }
    }

    private fun parcelableArrayListExtra(intent: Intent, key: String): List<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(key, Uri::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(key).orEmpty()
        }
    }
}
