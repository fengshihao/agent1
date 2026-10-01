package com.agent1.android.productivity.logic.business

/** 当前会话发给模型的系统提示词，以及已注册工具的名称、说明和参数。 */
data class SystemPromptSnapshot(
    val prompt: String,
    val tools: List<RegisteredToolSnapshot>,
)

data class RegisteredToolSnapshot(
    val name: String,
    val description: String,
    val parametersSchema: String,
)
