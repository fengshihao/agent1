package com.agent1.android.productivity.logic.business

/** 助手请用户选文件时的标记（通道 A：UI 显示「选择文件」）。 */
object UserFileRequestMarkers {

    data class Hint(
        val kinds: String? = null,
        val maxCount: Int? = null,
    )

    private val markerRegex = Regex(
        """\[(?:需要用户选文件|user_pick_files)(?::[^\]]*)?]\s*""",
        RegexOption.IGNORE_CASE,
    )

    private val markerCaptureRegex = Regex(
        """\[(?:需要用户选文件|user_pick_files)(?::([^\]]*))?\]""",
        RegexOption.IGNORE_CASE,
    )

    fun containsRequest(text: String): Boolean = markerRegex.containsMatchIn(text)

    /** 聊天 UI 展示用：去掉标记行，保留说明文字。 */
    fun stripForDisplay(text: String): String = text.replace(markerRegex, "").trim()

    /** 解析标记中的类型/数量提示，供 CTA 副标题展示。 */
    fun parseHint(text: String): Hint? {
        if (!containsRequest(text)) return null
        val spec = markerCaptureRegex.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (spec.isEmpty()) return Hint()
        val parts = spec.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }
        var kinds: String? = null
        var maxCount: Int? = null
        for (part in parts) {
            val maxMatch = Regex("""最多\s*(\d+)\s*个""").find(part)
            if (maxMatch != null) {
                maxCount = maxMatch.groupValues[1].toIntOrNull()
                continue
            }
            if (kinds == null) kinds = part
        }
        return Hint(kinds = kinds, maxCount = maxCount)
    }
}
