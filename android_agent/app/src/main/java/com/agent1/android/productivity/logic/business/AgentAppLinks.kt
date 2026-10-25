package com.agent1.android.productivity.logic.business

import android.net.Uri

/** App 自定义链接。官网教程第 4 步用同一地址打开模型设置。 */
object AgentAppLinks {
    const val MODEL_SETTINGS = "agent1://model-settings"
    /** `preview/{sessionId}/{path}` 的深链，便于 adb / 自动化打开内置预览。 */
    const val PREVIEW = "agent1://preview/{sessionId}/{path}"
    const val QUERY_KEY = "key"

    fun previewUri(sessionId: String, workspaceRelativePath: String): Uri =
        Uri.parse(
            "agent1://preview/${Uri.encode(sessionId)}/${Uri.encode(workspaceRelativePath)}",
        )

    fun apiKeyFrom(uri: Uri?): String? {
        if (uri == null) return null
        if (uri.scheme != "agent1") return null
        if (uri.host != "model-settings") return null
        return uri.getQueryParameter(QUERY_KEY)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
