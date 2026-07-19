package com.agent1.javaagent.llm;

/** 用户取消模型请求；运行时标为 cancelled，不当成模型故障。 */
public final class LlmCancelledException extends Exception {
    public LlmCancelledException() {
        super("已取消");
    }
}
