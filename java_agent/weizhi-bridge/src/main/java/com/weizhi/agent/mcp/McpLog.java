package com.weizhi.agent.mcp;

import java.util.logging.Level;
import java.util.logging.Logger;

/** 桌面 JVM 宿主：替代 Android {@code android.util.Log}。 */
final class McpLog {

    private static final Logger LOG = Logger.getLogger("WeizhiMcp");

    private McpLog() {
    }

    static void i(String msg) {
        LOG.log(Level.INFO, msg);
    }

    static void w(String msg) {
        LOG.log(Level.WARNING, msg);
    }

    static void e(String msg, Throwable t) {
        LOG.log(Level.SEVERE, msg, t);
    }

    static void e(String msg) {
        LOG.log(Level.SEVERE, msg);
    }
}
