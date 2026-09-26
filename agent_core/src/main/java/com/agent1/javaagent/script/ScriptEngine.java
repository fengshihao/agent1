package com.agent1.javaagent.script;

import com.agent1.javaagent.core.CancellationToken;

/** 脚本引擎抽象：一次 open 绑定一个工作区，eval 后由调用方 close。 */
public interface ScriptEngine extends AutoCloseable {

    /**
     * @return 成功时 JSON 文本（与 Weizhi {@code runJs} 一致）
     * @throws RuntimeException 失败时完整英文 error（勿吞掉 message）
     */
    String eval(String jsSource, long timeoutMs, CancellationToken cancellationToken);

    /** 从其他线程请求中止当前 eval；默认无操作。 */
    default void cancel() {
    }

    @Override
    void close();
}
