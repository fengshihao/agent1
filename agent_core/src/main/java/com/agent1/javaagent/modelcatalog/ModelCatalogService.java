package com.agent1.javaagent.modelcatalog;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** 远程 models.dev + 内置 {@link QwenModelCatalog}，对齐 Android {@code ModelCatalogService}。 */
public final class ModelCatalogService {

    private final ModelsDevCatalogClient catalogClient;

    public ModelCatalogService() {
        this(new ModelsDevCatalogClient());
    }

    ModelCatalogService(ModelsDevCatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    public List<RemoteModelOption> fetchPublicModelOptions(String providerId) throws IOException {
        List<String> keys = ModelProviderIds.modelsDevProviderKeys(providerId);
        return catalogClient.fetch(keys).stream().map(this::mergeKnown).collect(Collectors.toList());
    }

    public List<RemoteModelOption> bundledFallback() {
        return QwenModelCatalog.primaryModels().stream().map(this::fromCatalog).collect(Collectors.toList());
    }

    public List<RemoteModelOption> deepseekFallback() {
        return List.of(
            new RemoteModelOption("deepseek-flash", "DeepSeek Flash", "V4.1 Flash · 推荐"),
            new RemoteModelOption("deepseek-v4-pro", "DeepSeek V4 Pro", "更强推理")
        );
    }

    public List<RemoteModelOption> zhipuCodingFallback() {
        return List.of(
            new RemoteModelOption("glm-5.3", "GLM-5.3", "深度思考 · Coding Plan 推荐"),
            new RemoteModelOption("glm-5.3-flash", "GLM-5.3 Flash", "低延迟 · 思考默认开启"),
            new RemoteModelOption("glm-5.2", "GLM-5.2", "上一代 · 可关闭思考")
        );
    }

    public List<RemoteModelOption> fallbackForProvider(String providerId) {
        if (ModelProviderIds.PROVIDER_ZHIPU_CODING.equals(providerId)) {
            return zhipuCodingFallback();
        }
        if (ModelProviderIds.PROVIDER_DEEPSEEK.equals(providerId)) {
            return deepseekFallback();
        }
        return bundledFallback();
    }

    private RemoteModelOption mergeKnown(ModelsDevCatalogClient.CatalogModel model) {
        Optional<QwenModelInfo> known = QwenModelCatalog.findByModelId(model.id());
        if (known.isPresent()) {
            return fromCatalog(known.get());
        }
        return new RemoteModelOption(model.id(), model.name(), model.releaseDate());
    }

    private RemoteModelOption fromCatalog(QwenModelInfo info) {
        StringBuilder subtitle = new StringBuilder();
        subtitle.append(info.getTier());
        if (!info.getThinkingMode().isBlank()) {
            subtitle.append(" · ").append(info.getThinkingMode());
        }
        subtitle.append(" · 上下文 ").append(info.getContextWindowTokens());
        return new RemoteModelOption(info.getModelId(), info.getDisplayName(), subtitle.toString());
    }
}
