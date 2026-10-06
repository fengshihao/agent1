package com.agent1.android.productivity.logic.business

import com.agent1.android.productivity.logic.data.remote.ModelsDevCatalogClient
import com.agent1.javaagent.modelcatalog.QwenModelCatalog
import com.agent1.javaagent.modelcatalog.QwenModelInfo

/** 远程模型 id + 内置规格表合并，供设置页展示。 */
class ModelCatalogService(
    private val catalogClient: ModelsDevCatalogClient = ModelsDevCatalogClient(),
) {

    /** 从 models.dev 拉该服务商的最新文本模型；失败时由调用方退回本地候选。 */
    fun fetchPublicModelOptions(providerId: String): Result<List<RemoteModelOption>> {
        return catalogClient.fetch(modelsDevProviderKeys(providerId)).map { models ->
            models.map { model ->
                val known = QwenModelCatalog.findByModelId(model.id).orElse(null)
                if (known != null) {
                    fromCatalog(known)
                } else {
                    RemoteModelOption(
                        modelId = model.id,
                        title = model.name,
                        subtitle = model.releaseDate,
                    )
                }
            }
        }
    }

    fun bundledFallback(): List<RemoteModelOption> {
        return QwenModelCatalog.primaryModels().map { info -> fromCatalog(info) }
    }

    /** DeepSeek OpenAI 兼容常用模型（远程列表失败或未拉取时的本地候选）。 */
    fun deepseekFallback(): List<RemoteModelOption> = listOf(
        RemoteModelOption(
            modelId = "deepseek-flash",
            title = "DeepSeek Flash",
            subtitle = "V4.1 Flash · 推荐",
        ),
        RemoteModelOption(
            modelId = "deepseek-v4-pro",
            title = "DeepSeek V4 Pro",
            subtitle = "更强推理",
        ),
    )

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
