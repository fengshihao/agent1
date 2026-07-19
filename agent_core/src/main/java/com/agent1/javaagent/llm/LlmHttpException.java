package com.agent1.javaagent.llm;

/** 带 HTTP 状态的模型调用失败，供 429/5xx 重试判断。 */
public final class LlmHttpException extends IllegalStateException {
    private final int statusCode;

    public LlmHttpException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public LlmHttpException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
