package com.agent1.android.productivity.ui.view

import android.view.ViewGroup
import android.webkit.WebView
import com.agent1.android.productivity.logic.business.HtmlInspectScript

/**
 * 内置 HTML 预览 WebView 的媒体与释放顺序。
 *
 * Navigation 把预览从栈里 pop 时，Activity 仍在前台，WebView 收不到 Activity.onPause；
 * Compose 还可能短暂缓存 AndroidView。只调 [WebView.destroy] 时 Chromium 里的
 * audio/video 仍会继续播。离开前台必须先停媒体，再 blank，最后 destroy。
 *
 * 不调用 [WebView.pauseTimers]：它是进程级的，会冻住无头 `webview_exec`。
 */
object HtmlPreviewWebViewLifecycle {

    val PAUSE_MEDIA_JS: String = """
        (function(){
          try {
            document.querySelectorAll('audio,video').forEach(function(m){
              try { m.pause(); } catch (e) {}
            });
            if (window.speechSynthesis) {
              try { window.speechSynthesis.cancel(); } catch (e) {}
            }
          } catch (e) {}
        })();
    """.trimIndent()

    enum class Command {
        PAUSE,
        RESUME,
        RELEASE,
    }

    interface Host {
        fun evaluateJavascript(script: String)
        fun pauseWebView()
        fun resumeWebView()
        fun stopLoading()
        fun loadBlank()
        fun destroy()
    }

    fun execute(command: Command, host: Host) {
        when (command) {
            Command.PAUSE -> pause(host)
            Command.RESUME -> resume(host)
            Command.RELEASE -> release(host)
        }
    }

    fun pause(host: Host) {
        host.evaluateJavascript(PAUSE_MEDIA_JS)
        host.pauseWebView()
    }

    fun resume(host: Host) {
        host.resumeWebView()
    }

    fun release(host: Host) {
        pause(host)
        host.stopLoading()
        host.loadBlank()
        host.destroy()
    }
}

/** 对已 destroy 的 WebView 后续调用全部空操作，避免 Compose dispose 与 onRelease 顺序竞态。 */
internal class HtmlPreviewWebViewHost(webView: WebView) : HtmlPreviewWebViewLifecycle.Host {
    private var target: WebView? = webView

    override fun evaluateJavascript(script: String) {
        target?.evaluateJavascript(script, null)
    }

    override fun pauseWebView() {
        target?.onPause()
    }

    override fun resumeWebView() {
        target?.onResume()
    }

    override fun stopLoading() {
        target?.stopLoading()
    }

    override fun loadBlank() {
        target?.loadUrl("about:blank")
    }

    override fun destroy() {
        val view = target ?: return
        target = null
        view.removeJavascriptInterface(HtmlInspectScript.BRIDGE_NAME)
        (view.parent as? ViewGroup)?.removeView(view)
        view.removeAllViews()
        view.destroy()
    }
}
