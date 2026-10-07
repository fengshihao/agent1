package com.agent1.javaagent.script;

import com.agent1.javaagent.core.CancellationToken;

/**
 * 当前线程上「脚本内工具调用」应使用的 Run 级取消令牌。
 *
 * {@code run_js} 在工具线程上同步执行，脚本经 {@code __caps} 回调
 * {@code $tools.*} 时也在同一线程；AgentRuntime 在派发工具任务时把当前 Run 的
 * 令牌绑到这里，桥接层即可取到真令牌，而不是新建一个永远无人取消的令牌。
 */
public final class ScriptToolRunContext {

    private static final ThreadLocal<CancellationToken> CURRENT = new ThreadLocal<>();

    private ScriptToolRunContext() {
    }

    public static void bind(CancellationToken token) {
        if (token == null) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(token);
    }

    /** 当前线程绑定的令牌；未绑定时为 {@code null}，调用方需自行兜底。 */
    public static CancellationToken current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}