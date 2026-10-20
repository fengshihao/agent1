package com.agent1.javaagent.modelcatalog;

import java.util.List;
import java.util.Locale;

/** 与 Android {@code ModelSettingsModels} 对齐的服务商 id 与 models.dev 键。 */
public final class ModelProviderIds {

    public static final String PROVIDER_DASHSCOPE = "dashscope";
    public static final String PROVIDER_DEEPSEEK = "deepseek";
    public static final String PROVIDER_ZHIPU_CODING = "zhipu_coding";

    public static final String DEFAULT_DASHSCOPE_BASE_URL =
        "https://dashscope.aliyuncs.com/compatible-mode/v1";
    public static final String DEFAULT_DEEPSEEK_BASE_URL = "https://api.deepseek.com";
    public static final String DEFAULT_DEEPSEEK_MODEL = "deepseek-flash";
    public static final String DEFAULT_ZHIPU_CODING_BASE_URL =
        "https://open.bigmodel.cn/api/coding/paas/v4";
    public static final String DEFAULT_ZHIPU_CODING_MODEL = "glm-5.3";

    private ModelProviderIds() {
    }

    public static List<String> modelsDevProviderKeys(String providerId) {
        if (PROVIDER_DEEPSEEK.equals(providerId)) {
            return List.of("deepseek");
        }
        if (PROVIDER_ZHIPU_CODING.equals(providerId)) {
            return List.of("zhipuai-coding-plan", "zai-coding-plan");
        }
        return List.of("alibaba-cn");
    }

    public static String inferProviderId(String baseUrl) {
        String u = baseUrl == null ? "" : baseUrl.toLowerCase(Locale.ROOT);
        if (u.contains("deepseek.com")) {
            return PROVIDER_DEEPSEEK;
        }
        if (u.contains("bigmodel.cn")) {
            return PROVIDER_ZHIPU_CODING;
        }
        return PROVIDER_DASHSCOPE;
    }
}
