package com.dynamicui.demo.productivity.logic.data.config

import com.agent1.javaagent.config.AgentRuntimeDefaults

internal object ModelProviderDefaults {
    const val PROVIDER_DASHSCOPE = "dashscope"
    const val DEFAULT_BASE_URL = AgentRuntimeDefaults.DEFAULT_BASE_URL

    fun normalizeProviderId(id: String?): String {
        return id?.trim()?.takeIf { it.isNotEmpty() } ?: PROVIDER_DASHSCOPE
    }
}
