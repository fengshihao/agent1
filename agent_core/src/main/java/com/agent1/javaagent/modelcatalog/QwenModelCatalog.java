package com.agent1.javaagent.modelcatalog;

import java.util.List;
import java.util.Optional;

/** Agent1 生产力路径推荐调用的 Qwen 主模型列表（OpenAI 兼容 {@code model} 名）。 */
public final class QwenModelCatalog {

    private static final List<QwenModelInfo> PRIMARY = List.of(
        new QwenModelInfo(
            "qwen3.7-flash",
            "Qwen3.7 Flash",
            "Flash",
            1_000_000L,
            991_808L,
            131_072L,
            262_144L,
            List.of("文本", "图像", "视频"),
            List.of("文本"),
            true,
            "混合思考（enable_thinking）",
            "https://help.aliyun.com/en/model-studio/qwen3-7-flash"
        ),
        new QwenModelInfo(
            "qwen3.7-plus",
            "Qwen3.7 Plus",
            "Plus",
            1_000_000L,
            991_808L,
            131_072L,
            262_144L,
            List.of("文本", "图像", "视频"),
            List.of("文本"),
            true,
            "混合思考（默认开启）",
            "https://www.alibabacloud.com/help/en/model-studio/models"
        ),
        new QwenModelInfo(
            "qwen3.5-flash",
            "Qwen3.5 Flash",
            "Flash",
            1_000_000L,
            991_808L,
            65_536L,
            null,
            List.of("文本"),
            List.of("文本"),
            true,
            "轻量快速",
            "https://www.alibabacloud.com/help/en/model-studio/models"
        ),
        new QwenModelInfo(
            "qwen3.5-plus",
            "Qwen3.5 Plus",
            "Plus",
            1_000_000L,
            991_808L,
            65_536L,
            null,
            List.of("文本", "图像"),
            List.of("文本"),
            true,
            "均衡",
            "https://www.alibabacloud.com/help/en/model-studio/models"
        )
    );

    private QwenModelCatalog() {
    }

    public static List<QwenModelInfo> primaryModels() {
        return PRIMARY;
    }

    public static Optional<QwenModelInfo> findByModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return Optional.empty();
        }
        String normalized = modelId.trim().toLowerCase();
        for (QwenModelInfo info : PRIMARY) {
            if (info.getModelId().equalsIgnoreCase(normalized)
                || normalized.startsWith(info.getModelId().toLowerCase() + "-")) {
                return Optional.of(info);
            }
        }
        return Optional.empty();
    }
}
