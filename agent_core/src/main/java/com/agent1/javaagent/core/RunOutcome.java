package com.agent1.javaagent.core;

/** Run 终态语义（供 {@link AgentRuntime} 与 {@link com.agent1.javaagent.session.ProductivityAgentHost} 对齐）。 */
public final class RunOutcome {

    public static final String PAUSED_PREFIX = "单轮模型与工具往返超过上限";
    public static final String WAITING_USER_PREFIX = "等待用户输入";
    public static final String CANCELLED_MESSAGE = "执行已取消";

    private static final String PROGRESS_SUMMARY_USER =
        "【系统】本次运行中模型与工具的往返轮次已达上限（单轮任务较大时会触发）。"
        + "请用纯文字简要总结当前进度、已完成内容与未完成项，不要调用任何工具。"
        + "用户回复后可继续；如需减少中断，可调高单轮往返上限设置。";

    private RunOutcome() {
    }

    public static String pausedMessage(int maxTurns) {
        return PAUSED_PREFIX + "（" + maxTurns + " 轮），已暂停本轮；用户可继续对话。";
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

    public static String waitingUserMessage() {
        return WAITING_USER_PREFIX + "，本轮已结束；用户回复后将开启新的 Run。";
    }

    public static boolean isWaitingUser(String error) {
        return error != null && error.contains(WAITING_USER_PREFIX);
    }
}
