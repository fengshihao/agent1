package com.agent1.android.productivity.ui.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlPreviewWebViewLifecycleTest {

    private class FakeHost : HtmlPreviewWebViewLifecycle.Host {
        val calls = mutableListOf<String>()
        var lastScript: String? = null

        override fun evaluateJavascript(script: String) {
            calls += "js"
            lastScript = script
        }

        override fun pauseWebView() {
            calls += "onPause"
        }

        override fun resumeWebView() {
            calls += "onResume"
        }

        override fun stopLoading() {
            calls += "stopLoading"
        }

        override fun loadBlank() {
            calls += "about:blank"
        }

        override fun destroy() {
            calls += "destroy"
        }
    }

    @Test
    fun pauseJs_stopsAudioVideoAndSpeech() {
        val js = HtmlPreviewWebViewLifecycle.PAUSE_MEDIA_JS
        assertTrue(js.contains("audio,video"))
        assertTrue(js.contains(".pause()"))
        assertTrue(js.contains("speechSynthesis"))
    }

    @Test
    fun pause_runsMediaScriptBeforeWebViewOnPause() {
        val host = FakeHost()
        HtmlPreviewWebViewLifecycle.execute(HtmlPreviewWebViewLifecycle.Command.PAUSE, host)
        assertEquals(listOf("js", "onPause"), host.calls)
        assertEquals(HtmlPreviewWebViewLifecycle.PAUSE_MEDIA_JS, host.lastScript)
    }

    @Test
    fun resume_onlyResumesWebView() {
        val host = FakeHost()
        HtmlPreviewWebViewLifecycle.execute(HtmlPreviewWebViewLifecycle.Command.RESUME, host)
        assertEquals(listOf("onResume"), host.calls)
    }

    @Test
    fun release_pausesThenUnloadsThenDestroys() {
        val host = FakeHost()
        HtmlPreviewWebViewLifecycle.execute(HtmlPreviewWebViewLifecycle.Command.RELEASE, host)
        assertEquals(
            listOf("js", "onPause", "stopLoading", "about:blank", "destroy"),
            host.calls,
        )
    }
}
