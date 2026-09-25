package com.dynamicui.demo.productivity.logic.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 调用 OpenAI 兼容 {@code GET /models} 拉取模型 id 列表。 */
class OpenAiCompatibleModelsClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    fun listModelIds(baseUrl: String, apiKey: String): Result<List<String>> {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return Result.failure(IllegalArgumentException("请先填写 API Key"))
        }
        val root = baseUrl.trim().trimEnd('/')
        if (root.isEmpty()) {
            return Result.failure(IllegalArgumentException("请先填写 Base URL"))
        }
        val url = "$root/models"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $trimmedKey")
            .get()
            .build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        "拉取模型失败 HTTP ${response.code}: ${body.take(280)}",
                    )
                }
                val parsed = json.decodeFromString(ModelsListResponse.serializer(), body)
                parsed.data
                    .mapNotNull { it.id?.trim()?.takeIf { id -> id.isNotEmpty() } }
                    .distinct()
                    .sorted()
            }
        }
    }

    @Serializable
    private data class ModelsListResponse(
        val data: List<ModelRow> = emptyList(),
    )

    @Serializable
    private data class ModelRow(
        val id: String? = null,
        @SerialName("object") val objectType: String? = null,
    )
}
