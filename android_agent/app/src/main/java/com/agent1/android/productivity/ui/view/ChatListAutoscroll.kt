package com.agent1.android.productivity.ui.view

/**
 * 对话列表是否贴底：必须看最后一项的底边是否进入视口，
 * 不能只用「最后一项可见」——思考/流式气泡很高时，看到顶部也会被当成贴底。
 */
object ChatListAutoscroll {
    const val BOTTOM_THRESHOLD_PX = 96

    fun isAtBottom(
        totalItems: Int,
        lastVisibleIndex: Int,
        lastVisibleOffset: Int,
        lastVisibleSize: Int,
        viewportEndOffset: Int,
        thresholdPx: Int = BOTTOM_THRESHOLD_PX,
    ): Boolean {
        if (totalItems <= 0) return true
        if (lastVisibleIndex < totalItems - 1) return false
        val lastBottom = lastVisibleOffset + lastVisibleSize
        return lastBottom <= viewportEndOffset + thresholdPx
    }
}
