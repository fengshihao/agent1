package com.weizhi.agent.web;

import com.agent1.android.llm.AppFileLog;

final class WebLog {

    private static final String TAG = "WeizhiWebView";

    private WebLog() {
    }

    static void i(String msg) {
        AppFileLog.INSTANCE.i(TAG, msg);
    }

    static void w(String msg) {
        AppFileLog.INSTANCE.w(TAG, msg, null);
    }

    static void e(String msg, Throwable t) {
        AppFileLog.INSTANCE.e(TAG, msg, t);
    }
}
