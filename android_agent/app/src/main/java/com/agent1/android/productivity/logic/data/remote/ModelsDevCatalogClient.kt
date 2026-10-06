package com.agent1.android.productivity.logic.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * [models.dev](https://models.dev) 公开目录，按服务商给出当前模型 id。
 * 不需要用户的 API Key。
 */
class ModelsDevCatalogClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val endpoint: String = DEFAULT_ENDPOINT,
) {

    fun fetch(providerKeys: List<String>): Result<List<CatalogModel>> {
        if (providerKeys.isEmpty()) return Result.success(emptyList())
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json")
            .header("User-Agent", "agent1")
            .get()
            .build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("模型目录 HTTP ${response.code}")
                }
                parse(body, providerKeys)
            }
        }
    }

    fun parse(body: String, providerKeys: List<String>): List<CatalogModel> {
        val root = json.parseToJsonElement(body).jsonObject
        val merged = linkedMapOf<String, CatalogModel>()
        providerKeys.forEach { key ->
            val models = root[key]?.jsonObject?.get("models")?.jsonObject ?: return@forEach
            models.values.forEach { element ->
                val obj = element as? JsonObject ?: return@forEach
                val model = toModel(obj) ?: return@forEach
                val existing = merged[model.id]
                if (existing == null || model.releaseDate > existing.releaseDate) {
                    merged[model.id] = model
                }
            }
        }
        return merged.values.sortedWith(
            compareByDescending<CatalogModel> { it.releaseDate }.thenBy { it.id },
        )
    }

    private fun toModel(obj: JsonObject): CatalogModel? {
        if (!outputsText(obj)) return null
        val id = obj.string("id")
        if (id.isEmpty()) return null
        val name = obj.string("name").ifBlank { id }
        return CatalogModel(
            id = id,
            name = name,
            releaseDate = obj.string("release_date"),
        )
    }

    private fun outputsText(obj: JsonObject): Boolean {
        val output = obj["modalities"]?.jsonObject?.get("output") as? JsonArray ?: return true
        if (output.isEmpty()) return true
        return output.any { element ->
            element.jsonPrimitive.contentOrNull == "text"
        }
    }

    private fun JsonObject.string(key: String): String {
        return this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://models.dev/api.json"
    }
}

data class CatalogModel(
    val id: String,
    val name: String,
    val releaseDate: String,
)
