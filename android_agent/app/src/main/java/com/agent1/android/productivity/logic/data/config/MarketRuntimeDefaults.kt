package com.agent1.android.productivity.logic.data.config

import android.content.Context
import com.agent1.javaagent.config.AgentRuntimeDefaults

/** 首次安装、尚未在 App 内保存模型配置时的默认服务商与模型。 */
object MarketRuntimeDefaults {

    const val PROVIDER_DASHSCOPE = "dashscope"
    const val PROVIDER_DEEPSEEK = "deepseek"
    private const val DEEPSEEK_BASE_URL = "https://api.deepseek.com"
    private const val DEEPSEEK_MODEL = "deepseek-flash"

    fun defaultProviderId(@Suppress("UNUSED_PARAMETER") context: Context? = null): String =
        PROVIDER_DEEPSEEK

    fun defaultBaseUrl(providerId: String): String = when (providerId) {
        PROVIDER_DEEPSEEK -> DEEPSEEK_BASE_URL
        else -> AgentRuntimeDefaults.DEFAULT_BASE_URL
    }

    fun defaultModelId(@Suppress("UNUSED_PARAMETER") context: Context? = null): String =
        DEEPSEEK_MODEL
}
