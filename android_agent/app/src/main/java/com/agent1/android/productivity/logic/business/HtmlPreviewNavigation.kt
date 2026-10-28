package com.agent1.android.productivity.logic.business

import android.content.Context

/**
 * 预览壳里的链接：普通网页留在 WebView；高德 / 百度等自定义 scheme 交给系统打开。
 * 不拦截时，WebView 会把 amapuri:// 当成页面加载，主框架报错变成错误页。
 */
object HtmlPreviewNavigation {

    enum class Action {
        Stay,
        OpenOutside,
        Block,
    }

    fun classify(url: String?): Action {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return Action.Stay
        val lower = raw.lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("vbscript:")) return Action.Block
        if (lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("file:") ||
            lower.startsWith("about:") ||
            lower.startsWith("data:") ||
            lower.startsWith("blob:")
        ) {
            return Action.Stay
        }
        return if (ChatTranscriptFormatting.isExternalOpenableUri(raw)) Action.OpenOutside else Action.Block
    }

    /** @return true 表示 WebView 不要自己加载这个地址。 */
    fun consume(context: Context, url: String?): Boolean {
        return when (classify(url)) {
            Action.Stay -> false
            Action.Block -> true
            Action.OpenOutside -> {
                WorkspaceFileActions.openExternalUri(context, url!!.trim())
                true
            }
        }
    }
}
