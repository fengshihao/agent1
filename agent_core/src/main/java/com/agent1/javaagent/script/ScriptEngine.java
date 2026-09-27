package com.agent1.javaagent.script;

import com.agent1.javaagent.core.CancellationToken;

/** 脚本引擎抽象：一次 open 绑定一个工作区，eval 后由调用方 close。 */
public interface ScriptEngine extends AutoCloseable {

    /**
     * @return 成功时 JSON 文本（与 Weizhi {@code runJs} 一致）
     * @throws RuntimeException 失败时完整英文 error（勿吞掉 message）
     */
    String eval(String jsSource, long timeoutMs, CancellationToken cancellationToken);

    /**
     * 生产力路径：先 agent prelude / 宿主 prelude，再对用户脚本 eval（file 模式传 workspace 相对路径作 QuickJS 文件名）。
     */
    default String evalForAgent(
        String userSource,
        String agentArgsPrelude,
        long timeoutMs,
        CancellationToken cancellationToken,
        String workspaceRelativeFile
    ) {
        String combined = userSource == null ? "" : userSource;
        if (agentArgsPrelude != null && !agentArgsPrelude.isBlank()) {
            combined = agentArgsPrelude + (combined.isEmpty() ? "" : "\n") + combined;
        }
        return eval(combined, timeoutMs, cancellationToken);
    }

    /** 宿主在 userSource 前另 eval 的 prelude 行数（如 Weizhi $tools）；默认 0。 */
    default int agentHostPreludeLines() {
        return 0;
    }

    /** 从其他线程请求中止当前 eval；默认无操作。 */
    default void cancel() {
    }

    @Override
    void close();
}
