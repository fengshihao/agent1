package com.agent1.android.productivity.logic.business

/**
 * 将超长助手正文拆成多个 Lazy item，避免单条测量高度过大导致滚过边界时锚点补偿跳变。
 * 与 RecyclerView 里「一条消息多个 ViewHolder」同类思路，不是气泡内滚动。
 */
object ChatLazyContentSplit {
    /** 低于此长度不拆（单段 Markdown/Text 一次 layout 可接受）。 */
    const val SPLIT_WHEN_CHARS_EXCEED = 1_800

    /** 每段目标上限（按段落贪心合并）。 */
    const val TARGET_BLOCK_CHARS = 2_400

    /** 合并过短的尾段，避免 Lazy 条目碎片化。 */
    const val MIN_BLOCK_CHARS = 280

    /**
     * 是否值得拆成多 Lazy item（仅助手非工具、非系统提示的正文）。
     */
    fun shouldSplitAssistantBody(content: String, markdown: Boolean): Boolean {
        if (content.isBlank()) return false
        if (!markdown && content.length <= SPLIT_WHEN_CHARS_EXCEED) return false
        if (markdown && content.length <= SPLIT_WHEN_CHARS_EXCEED &&
            content.lineSequence().none { it.length > 400 }
        ) {
            return false
        }
        return content.length > SPLIT_WHEN_CHARS_EXCEED ||
            content.lineSequence().count() > 72
    }

    /** 拆成若干段；不拆时返回单元素列表。 */
    fun splitAssistantBody(content: String): List<String> {
        if (content.isBlank()) return emptyList()
        if (content.length <= SPLIT_WHEN_CHARS_EXCEED &&
            content.lineSequence().count() <= 72
        ) {
            return listOf(content)
        }
        val paragraphs = splitParagraphsRespectingCodeFences(content)
        if (paragraphs.size <= 1) return listOf(content)
        val blocks = mutableListOf<StringBuilder>()
        var current = StringBuilder()
        for (para in paragraphs) {
            val addition = if (current.isEmpty()) para else "\n\n$para"
            if (current.isNotEmpty() &&
                current.length + addition.length > TARGET_BLOCK_CHARS
            ) {
                blocks.add(current)
                current = StringBuilder(para)
            } else {
                current.append(addition)
            }
        }
        if (current.isNotEmpty()) blocks.add(current)
        return mergeTinyBlocks(blocks.map { it.toString() })
    }

    internal fun splitParagraphsRespectingCodeFences(content: String): List<String> {
        val paragraphs = mutableListOf<String>()
        val current = StringBuilder()
        var inFence = false
        var blankRun = 0
        fun flushParagraph() {
            if (current.isNotEmpty()) {
                paragraphs.add(current.toString())
                current.clear()
            }
        }
        for (line in content.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                inFence = !inFence
            }
            if (!inFence && trimmed.isEmpty()) {
                blankRun++
                if (blankRun >= 1 && current.isNotEmpty()) {
                    flushParagraph()
                    blankRun = 0
                }
                continue
            }
            blankRun = 0
            if (current.isNotEmpty()) current.append('\n')
            current.append(line)
        }
        flushParagraph()
        return if (paragraphs.isEmpty()) listOf(content) else paragraphs
    }

    private fun mergeTinyBlocks(blocks: List<String>): List<String> {
        if (blocks.size <= 1) return blocks
        val out = ArrayList<String>(blocks.size)
        var pending = ""
        for (block in blocks) {
            if (pending.isEmpty()) {
                pending = block
            } else if (pending.length < MIN_BLOCK_CHARS) {
                pending = "$pending\n\n$block"
            } else {
                out.add(pending)
                pending = block
            }
        }
        if (pending.isNotEmpty()) out.add(pending)
        return out
    }
}
