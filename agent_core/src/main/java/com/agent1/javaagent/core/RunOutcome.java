package com.agent1.javaagent.core;

/** Run 终态语义（供 {@link AgentRuntime} 与 {@link com.agent1.javaagent.session.ProductivityAgentHost} 对齐）。 */
public final class RunOutcome {

    public static final String PAUSED_PREFIX = "对话回合超过上限";
    public static final String CANCELLED_MESSAGE = "执行已取消";

    private static final String PROGRESS_SUMMARY_USER =
        "【系统】本轮对话回合已达上限。请用纯文字简要总结当前进度、已完成内容与未完成项，不要调用任何工具。";

    private RunOutcome() {
    }

    public static String pausedMessage(int maxTurns) {
        return PAUSED_PREFIX + "（" + maxTurns + "），已暂停本轮；用户可继续对话。";
    }

    public static String progressSummaryUserPrompt() {
        return PROGRESS_SUMMARY_USER;
    }

    public static boolean isPaused(String error) {
        return error != null && error.contains(PAUSED_PREFIX);
    }

    public static boolean isCancelled(String error) {
        return CANCELLED_MESSAGE.equals(error);
    }
}
