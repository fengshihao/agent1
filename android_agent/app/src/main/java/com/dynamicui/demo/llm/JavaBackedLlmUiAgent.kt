package com.dynamicui.demo.llm

import com.agent1.javaagent.config.AgentRuntimeConfig
import com.agent1.javaagent.core.AgentRuntime
import com.agent1.javaagent.llm.openai.OpenAiCompatibleClient
import com.agent1.javaagent.model.AgentMessage
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class JavaAgentClientConfig(
    val apiKey: String,
    val baseUrl: String,
    val model: String
) {
    companion object {
        /** BuildConfig 优先，缺项用 AgentRuntimeConfig 环境/默认值补全。 */
        fun resolve(buildConfigApiKey: String, buildConfigBaseUrl: String): JavaAgentClientConfig {
            val env = AgentRuntimeConfig.fromEnvironment()
            val key = buildConfigApiKey.trim().ifEmpty { env.apiKey }
            val base = buildConfigBaseUrl.trim().ifEmpty { env.baseUrl }
            return JavaAgentClientConfig(key, base, env.model)
        }
    }
}

class JavaBackedLlmUiAgent(
    private val config: JavaAgentClientConfig,
    private val prompts: LlmPromptBundle,
    private val json: Json = Json {
        explicitNulls = false
    }
) {
    suspend fun generateUiJson(intent: String): Result<String> {
        val userPrompt = """
            用户意图: $intent
            请生成一个可直接渲染的 UiDocument JSON。
            对于表单控件，尽量提供 key 字段，便于收集用户输入。
        """.trimIndent()
        return chat(prompts.generationSystemPrompt, userPrompt).map(::cleanModelJson)
    }

    suspend fun repairUiJson(
        intent: String,
        badJson: String,
        parserErrors: List<String>
    ): Result<String> {
        val userPrompt = """
            你上一次生成的 JSON 无法通过解析校验，请仅返回修复后的 JSON 对象。
            用户意图: $intent
            上一次输出:
            $badJson
            解析错误:
            ${parserErrors.joinToString("\n")}
            请严格遵守系统协议，仅返回可解析 JSON。
        """.trimIndent()
        return chat(prompts.generationSystemPrompt, userPrompt).map(::cleanModelJson)
    }

    suspend fun summarizeSelection(
        intent: String,
        generatedUiJson: String,
        selection: Map<String, String>
    ): Result<String> {
        val userPrompt = """
            初始意图: $intent
            生成的UI JSON: $generatedUiJson
            用户选择结果(JSON): ${json.encodeToString(selection)}
        """.trimIndent()
        return chat(prompts.summarySystemPrompt, userPrompt)
    }

    private suspend fun chat(systemPrompt: String, userPrompt: String): Result<String> = withContext(Dispatchers.IO) {
        val runtimeConfig = AgentRuntimeConfig.builder()
            .apiKey(config.apiKey)
            .baseUrl(config.baseUrl)
            .model(config.model)
            .build()
        val configError = runtimeConfig.configurationError()
        if (configError != null) {
            return@withContext Result.failure(IllegalStateException(configError))
        }
        runCatching {
            val llmClient = OpenAiCompatibleClient(
                runtimeConfig.toOpenAiCompatibleConfig(Duration.ofSeconds(120), 0.2)
            )
            AgentRuntime(
                runtimeConfig.toAgentOptionsBuilder(systemPrompt).build(),
                llmClient
            ).use { runtime ->
                runtime.prompt(userPrompt).join()
                runtime.waitForIdle()
                runtime.stateSnapshot.messages
                    .asReversed()
                    .firstOrNull { it.role == AgentMessage.ROLE_ASSISTANT }
                    ?.content
                    ?.takeIf { it.isNotBlank() }
                    ?: error("模型未返回有效内容")
            }
        }
    }
}
