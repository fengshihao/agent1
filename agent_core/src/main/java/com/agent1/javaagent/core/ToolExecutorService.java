package com.agent1.javaagent.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工具执行线程池：有界、daemon、空闲回收。
 *
 * 旧的 cachedThreadPool 无上限——超时中断后不响应取消的慢工具（阻塞 IO / 网络）
 * 会让线程持续累积。这里限制最大并发（超出的任务排队并受自身超时约束），
 * 空闲线程 60s 后退出，不阻塞 JVM 退出。
 */
final class ToolExecutorService extends ThreadPoolExecutor {

    private static final int CORE_THREADS = 2;
    private static final int MAX_THREADS = 8;
    private static final long KEEP_ALIVE_SECONDS = 60L;
    private static final AtomicInteger SEQ = new AtomicInteger();

    ToolExecutorService() {
        super(
            CORE_THREADS,
            MAX_THREADS,
            KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            r -> {
                Thread t = new Thread(r, "agent-tool-" + SEQ.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        );
        allowCoreThreadTimeOut(true);
    }
}