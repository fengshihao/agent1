package com.agent1.javaagent.log;

import java.nio.file.Path;

/** 当前线程内的 Run 审计上下文（工具 / Coach 写 events.jsonl 时用）。 */
public final class RunAuditScope {

    private static final ThreadLocal<Binding> CURRENT = new ThreadLocal<>();

    private RunAuditScope() {
    }

    public record Binding(Path agentRoot, RunLogContext logContext) {
    }

    public static void bind(Path agentRoot, RunLogContext logContext) {
        if (agentRoot == null || logContext == null) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(new Binding(agentRoot.toAbsolutePath().normalize(), logContext));
    }

    public static Binding get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
