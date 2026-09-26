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

    /** 智谱 Coding Plan 常用模型（远程列表失败或未拉取时的本地候选）。 */
    fun zhipuCodingFallback(): List<RemoteModelOption> = listOf(
        RemoteModelOption(
            modelId = "glm-5.3",
            title = "GLM-5.3",
            subtitle = "深度思考 · Coding Plan 推荐",
        ),
        RemoteModelOption(
            modelId = "glm-5.3-flash",
            title = "GLM-5.3 Flash",
            subtitle = "低延迟 · 思考默认开启",
        ),
        RemoteModelOption(
            modelId = "glm-5.2",
            title = "GLM-5.2",
            subtitle = "上一代 · 可关闭思考",
        ),
    )

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
