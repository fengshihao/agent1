package com.weizhi.agent.web;

/** 桌面 CDP 宿主访问 {@link WebViewTask} 包级字段（与 Android 模块同包）。 */
public final class WebViewTaskAccess {

    private WebViewTaskAccess() {
    }

    public static long timeoutMs(WebViewTask task) {
        return task.timeoutMs;
    }
}
