package com.dynamicui.demo.productivity.logic.business

import com.agent1.javaagent.modelcatalog.QwenModelCatalog
import com.agent1.javaagent.modelcatalog.QwenModelInfo
import com.dynamicui.demo.productivity.logic.data.remote.OpenAiCompatibleModelsClient

/** 远程模型 id + 内置规格表合并，供设置页展示。 */
class ModelCatalogService(
    private val modelsClient: OpenAiCompatibleModelsClient = OpenAiCompatibleModelsClient(),
) {

    fun fetchRemoteModelOptions(baseUrl: String, apiKey: String): Result<List<RemoteModelOption>> {
        return modelsClient.listModelIds(baseUrl, apiKey).map { ids ->
            ids.map { id -> toOption(id) }
        }
    }

    fun bundledFallback(): List<RemoteModelOption> {
        return QwenModelCatalog.primaryModels().map { info -> fromCatalog(info) }
    }

    private fun toOption(modelId: String): RemoteModelOption {
        val catalog = QwenModelCatalog.findByModelId(modelId).orElse(null)
        return if (catalog != null) {
            fromCatalog(catalog)
        } else {
            RemoteModelOption(
                modelId = modelId,
                title = modelId,
                subtitle = "远程列表",
            )
        }
    }

    private fun fromCatalog(info: QwenModelInfo): RemoteModelOption {
        val subtitle = buildString {
            append(info.tier)
            if (info.thinkingMode.isNotBlank()) {
                append(" · ")
                append(info.thinkingMode)
            }
            append(" · 上下文 ")
            append(info.contextWindowTokens)
        }
        return RemoteModelOption(
            modelId = info.modelId,
            title = info.displayName,
            subtitle = subtitle,
        )
    }
}

data class RemoteModelOption(
    val modelId: String,
    val title: String,
    val subtitle: String,
)
