package com.agent1.android.productivity.logic.business

/** 助手请用户选文件时的标记（通道 A：UI 显示「选择文件」）。 */
object UserFileRequestMarkers {

    private val markerRegex = Regex(
        """\[(?:需要用户选文件|user_pick_files)(?::[^\]]*)?]\s*""",
        RegexOption.IGNORE_CASE,
    )

    fun containsRequest(text: String): Boolean = markerRegex.containsMatchIn(text)

    /** 聊天 UI 展示用：去掉标记行，保留说明文字。 */
    fun stripForDisplay(text: String): String = text.replace(markerRegex, "").trim()
}
