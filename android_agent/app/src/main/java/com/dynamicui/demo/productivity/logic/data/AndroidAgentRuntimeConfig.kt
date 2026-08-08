package com.dynamicui.demo.productivity.logic.data

import com.agent1.javaagent.config.AgentRuntimeConfig
import com.dynamicui.demo.BuildConfig
import java.util.Properties

/** 将 BuildConfig / 环境注入的 Qwen 参数转为 {@link AgentRuntimeConfig}。 */
object AndroidAgentRuntimeConfig {

    fun load(): AgentRuntimeConfig {
        val props = Properties()
        val apiKey = firstNonBlank(
            BuildConfig.QWEN_API_KEY,
            BuildConfig.DASHSCOPE_API_KEY,
        )
        if (!apiKey.isNullOrBlank()) {
            props.setProperty("apiKey", apiKey)
        }
        val baseUrl = firstNonBlank(
            BuildConfig.QWEN_BASE_URL,
            BuildConfig.DASHSCOPE_BASE_URL,
        )
        if (!baseUrl.isNullOrBlank()) {
            props.setProperty("baseUrl", baseUrl)
        }
        val model = firstNonBlank(BuildConfig.QWEN_MODEL, BuildConfig.DEFAULT_MODEL)
        if (!model.isNullOrBlank()) {
            props.setProperty("model", model)
        }
        return AgentRuntimeConfig.fromProperties(props)
    }

    private fun firstNonBlank(vararg values: String?): String? {
        for (v in values) {
            if (!v.isNullOrBlank()) {
                return v.trim()
            }
        }
        return null
    }
}
