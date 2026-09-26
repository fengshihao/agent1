package com.dynamicui.demo.productivity.logic.business

import com.agent1.javaagent.config.AgentRuntimeDefaults

data class ProviderOption(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    /** 选择该服务商时填入的默认模型 id（空表示不改动当前模型）。 */
    val defaultModelId: String = "",
)

data class ModelSettingsForm(
    val providerId: String = PROVIDER_DASHSCOPE,
    val baseUrl: String = DEFAULT_DASHSCOPE_BASE_URL,
    val apiKey: String = "",
    val modelId: String = "",
    val maxContextTurns: Int = AgentRuntimeDefaults.DEFAULT_MAX_CONTEXT_TURNS,
    val maxContextMessages: Int = 0,
    val maxTurnsPerRun: Int = AgentRuntimeDefaults.DEFAULT_MAX_TURNS_PER_RUN,
    val maxToolCallsPerRun: Int = AgentRuntimeDefaults.DEFAULT_MAX_TOOL_CALLS_PER_RUN,
    val savedInApp: Boolean = false,
)

const val PROVIDER_DASHSCOPE = "dashscope"
const val PROVIDER_ZHIPU_CODING = "zhipu_coding"
const val PROVIDER_OPENAI = "openai"
const val PROVIDER_CUSTOM = "custom"

const val DEFAULT_DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
const val DEFAULT_ZHIPU_CODING_BASE_URL = "https://open.bigmodel.cn/api/coding/paas/v4"
const val DEFAULT_ZHIPU_CODING_MODEL = "glm-5.3"
const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"

fun providerOptions(): List<ProviderOption> = listOf(
    ProviderOption(PROVIDER_DASHSCOPE, "阿里云 DashScope（OpenAI 兼容）", DEFAULT_DASHSCOPE_BASE_URL),
    ProviderOption(
        PROVIDER_ZHIPU_CODING,
        "智谱 GLM Coding Plan",
        DEFAULT_ZHIPU_CODING_BASE_URL,
        DEFAULT_ZHIPU_CODING_MODEL,
    ),
    ProviderOption(PROVIDER_OPENAI, "OpenAI", DEFAULT_OPENAI_BASE_URL),
    ProviderOption(PROVIDER_CUSTOM, "自定义 OpenAI 兼容", ""),
)

fun resolveProvider(id: String?): ProviderOption {
    return providerOptions().firstOrNull { it.id == id?.trim() } ?: providerOptions().first()
}
