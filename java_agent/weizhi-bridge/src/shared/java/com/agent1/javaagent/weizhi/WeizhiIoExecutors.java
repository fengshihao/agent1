package com.agent1.javaagent.weizhi;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Weizhi 引擎共享 IO 线程池：daemon 线程、空闲自动回收。
 *
 * 不共享时每个 {@link com.weizhi.WeizhiEngine}（即每次 run_js）都会
 * new 一个 fixedThreadPool（默认 16 线程）并在 close 时销毁——高频脚本调用下
 * 线程创建/销毁成为主要开销。共享池保持相同的并发与排队语义
 * （core=max=maxAsyncIo、无界队列），但空闲线程 60s 后自动退出。
 */
public final class WeizhiIoExecutors {

    private static final long KEEP_ALIVE_SECONDS = 60L;
    private static volatile ExecutorService sharedIo;

    private WeizhiIoExecutors() {
    }

    /** 返回进程级共享 IO 池；{@code maxAsyncIo} 仅在首次创建时生效（取较大值）。 */
    public static ExecutorService io(int maxAsyncIo) {
        ExecutorService local = sharedIo;
        if (local != null && !local.isShutdown()) {
            return local;
        }
        synchronized (WeizhiIoExecutors.class) {
            local = sharedIo;
            if (local != null && !local.isShutdown()) {
                return local;
            }
            int threads = Math.max(16, Math.max(1, maxAsyncIo));
            AtomicInteger seq = new AtomicInteger();
            ThreadPoolExecutor pool = new ThreadPoolExecutor(
                threads,
                threads,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "weizhi-io-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            );
            pool.allowCoreThreadTimeOut(true);
            sharedIo = pool;
            return pool;
        }
    }

    /** 测试与宿主关闭时使用。 */
    public static synchronized void shutdown() {
        ExecutorService local = sharedIo;
        sharedIo = null;
        if (local != null) {
            local.shutdownNow();
        }
    }
}