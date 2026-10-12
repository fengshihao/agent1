package com.agent1.android.productivity.logic.business

import android.net.Uri

/** App 自定义链接。官网教程第 4 步用同一地址打开模型设置。 */
object AgentAppLinks {
    const val MODEL_SETTINGS = "agent1://model-settings"
    const val QUERY_KEY = "key"

    fun apiKeyFrom(uri: Uri?): String? {
        if (uri == null) return null
        if (uri.scheme != "agent1") return null
        if (uri.host != "model-settings") return null
        return uri.getQueryParameter(QUERY_KEY)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
