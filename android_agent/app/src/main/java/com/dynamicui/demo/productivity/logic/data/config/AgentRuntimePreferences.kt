package com.dynamicui.demo.productivity.logic.data.config

/** 用户在 App 内保存的运行时配置（不含密钥明文日志）。 */
data class AgentRuntimePreferences(
    val providerId: String = ModelProviderDefaults.PROVIDER_DASHSCOPE,
    val apiKey: String = "",
    val baseUrl: String = ModelProviderDefaults.DEFAULT_BASE_URL,
    val modelId: String = "",
    val maxContextTurns: Int = 0,
    val maxContextMessages: Int = 0,
    val maxTurnsPerRun: Int = 0,
    val maxToolCallsPerRun: Int = 0,
    /** 为 true 时表示用户已在 App 内保存过，BuildConfig 仅作缺省回填。 */
    val savedInApp: Boolean = false,
)
