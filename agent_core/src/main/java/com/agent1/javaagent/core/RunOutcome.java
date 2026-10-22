package com.agent1.javaagent.core;

/** Run 终态语义（供 {@link AgentRuntime} 与 {@link com.agent1.javaagent.session.ProductivityAgentHost} 对齐）。 */
public final class RunOutcome {

    public static final String PAUSED_PREFIX = "单轮模型与工具往返超过上限";
    public static final String TOOL_BUDGET_PREFIX = "单轮工具调用次数超过上限";
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

    /** 写入 Run 终态。与 {@link #toolBudgetNotice(int)} 共用前缀，便于界面认出可继续。 */
    public static String toolBudgetMessage(int maxToolCalls) {
        return TOOL_BUDGET_PREFIX + "（" + maxToolCalls + " 次），已暂停本轮；用户可继续对话。";
    }

    /** 落进对话、给用户看的一句。不要以「错误:」开头，避免被收成通用报错。 */
    public static String toolBudgetNotice(int maxToolCalls) {
        return toolBudgetMessage(maxToolCalls) + "点下方「继续」即可接着做完。";
    }

    public static String toolCallSkippedForBudget(int maxToolCalls) {
        return "单轮工具调用已达上限（" + maxToolCalls + "），本次调用未执行。用户继续后可再试。";
    }

    public static boolean isToolBudget(String text) {
        if (text == null) {
            return false;
        }
        return text.contains(TOOL_BUDGET_PREFIX) || text.contains("工具调用次数超过上限");
    }

    /** 轮次用尽、工具次数用尽，或因此留下的未配对 tool_call 被服务端拒绝。 */
    public static boolean isContinuableStop(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return isPaused(text) || isToolBudget(text) || text.contains("insufficient tool messages");
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
