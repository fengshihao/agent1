package com.weizhi.agent.web;

import android.content.Context;

import com.agent1.javaagent.workspace.WorkspaceSandbox;

/** Android 系统 WebView 上的 {@code webview_exec}。 */
public final class WebViewAgentExtension {

    private final Context context;

    public WebViewAgentExtension(Context context) {
        this.context = context.getApplicationContext();
    }

    public WebViewExecTool create(WorkspaceSandbox sandbox) {
        WebViewRuntime runtime = WebViewRuntime.getInstance(context, new HandlerUiExecutor());
        WebLog.i("registered webview_exec");
        return new WebViewExecTool(runtime, sandbox);
    }
}
