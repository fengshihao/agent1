package com.agent1.javaagent.config;

/** 基础能力运行时默认值（13-配置 / 11-模型 的唯一缺省来源）。 */
public final class AgentRuntimeDefaults {

    public static final String DEFAULT_MODEL = "qwen3.7-flash";
    public static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    public static final int DEFAULT_MAX_CONTEXT_TURNS = 6;
    public static final int DEFAULT_MAX_TURNS_PER_RUN = 12;
    public static final int DEFAULT_MAX_TOOL_CALLS_PER_RUN = 24;
    /** 窗口内、非最新一轮 toolResult 超过此字数则换成占位（10-上下文）。 */
    public static final int DEFAULT_TOOL_RESULT_TRUNCATE_CHARS = 280;

    private AgentRuntimeDefaults() {
    }
}
