package com.dynamicui.demo.llm

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class HttpResult(
    val code: Int,
    val body: String
)

interface HttpTransport {
    suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        body: String
    ): HttpResult
}

class UrlConnectionTransport : HttpTransport {
    override suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        body: String
    ): HttpResult = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }

        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.use {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText()
            }.orEmpty()
            HttpResult(code = code, body = responseBody)
        } finally {
            connection.disconnect()
        }
    }
}

data class QwenClientConfig(
    val apiKey: String,
    val baseUrl: String = "https://dashscope.aliyuncs.com/compatible-mode/v1",
    val model: String = "qwen3.5-flash"
)

class QwenClient(
    private val config: QwenClientConfig,
    private val transport: HttpTransport = UrlConnectionTransport(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
) {
    private val logTag = "QwenClient"

    private fun logDebug(message: String) {
        runCatching { Log.d(logTag, message) }
            .getOrElse { println("D/$logTag: $message") }
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        runCatching {
            if (throwable == null) {
                Log.e(logTag, message)
            } else {
                Log.e(logTag, message, throwable)
            }
        }.getOrElse {
            println("E/$logTag: $message")
            throwable?.printStackTrace()
        }
    }

    suspend fun chat(systemPrompt: String, userPrompt: String): Result<String> {
        if (config.apiKey.isBlank()) {
            return Result.failure(IllegalStateException("缺少 API Key，请配置 DASHSCOPE_API_KEY"))
        }

        val request = ChatCompletionRequest(
            model = config.model,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userPrompt)
            ),
            temperature = 0.2
        )

        val payload = json.encodeToString(ChatCompletionRequest.serializer(), request)
        val url = "${config.baseUrl.trimEnd('/')}/chat/completions"
        logDebug("chat request model=${config.model}, url=$url")
        logDebug("systemPrompt=${systemPrompt.take(200)}")
        logDebug("userPrompt=${userPrompt.take(400)}")
        val httpResult = runCatching {
            transport.postJson(
                url = url,
                headers = mapOf(
                    "Authorization" to "Bearer ${config.apiKey}",
                    "Content-Type" to "application/json"
                ),
                body = payload
            )
        }.onFailure {
            logError("http transport failed", it)
        }.getOrElse { return Result.failure(it) }

        if (httpResult.code !in 200..299) {
            logError("chat failed code=${httpResult.code}, body=${httpResult.body.take(500)}")
            return Result.failure(
                IllegalStateException("请求失败(${httpResult.code}): ${httpResult.body.take(500)}")
            )
        }
        logDebug("chat success code=${httpResult.code}, body=${httpResult.body.take(500)}")

        return runCatching {
            val response = json.decodeFromString(ChatCompletionResponse.serializer(), httpResult.body)
            response.choices.firstOrNull()?.message?.contentText()
                ?.takeIf { it.isNotBlank() }
                ?: error("模型未返回有效内容")
        }.onFailure {
            logError("response parse failed, body=${httpResult.body.take(500)}", it)
        }
    }
}

class LlmUiAgent(
    private val qwenClient: QwenClient,
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

        return qwenClient.chat(prompts.generationSystemPrompt, userPrompt).map(::cleanModelJson)
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
        return qwenClient.chat(prompts.generationSystemPrompt, userPrompt).map(::cleanModelJson)
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
        return qwenClient.chat(prompts.summarySystemPrompt, userPrompt)
    }
}

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.2
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<ChatChoice> = emptyList()
)

@Serializable
data class ChatChoice(
    val message: ChatMessageResponse
)

@Serializable
data class ChatMessageResponse(
    val role: String? = null,
    @SerialName("content") val rawContent: JsonElement? = null
) {
    fun contentText(): String {
        val content = rawContent ?: return ""
        return when (content) {
            is JsonPrimitive -> content.contentOrNull.orEmpty()
            is JsonArray -> content.joinToString("\n") { item ->
                if (item is JsonPrimitive) {
                    item.contentOrNull.orEmpty()
                } else {
                    item.toString()
                }
            }
            else -> content.toString()
        }
    }
}
