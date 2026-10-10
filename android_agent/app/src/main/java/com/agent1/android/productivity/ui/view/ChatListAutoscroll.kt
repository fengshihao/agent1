package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.lazy.LazyListLayoutInfo

/**
 * 对话列表是否贴底：必须看最后一项的底边是否进入视口，
 * 不能只用「最后一项可见」——思考/流式气泡很高时，看到顶部也会被当成贴底。
 */
object ChatListAutoscroll {
    const val BOTTOM_THRESHOLD_PX = 96

    /**
     * 用户手指下拉（看更早的消息）时 availableY 为正。
     * 必须立刻脱离贴底，否则超高最后一条会让 canScrollForward 一直为 false，
     * 下一帧又被拉回底部，表现为翻不过某条消息。
     */
    fun shouldReleaseStickToBottom(availableY: Float): Boolean = availableY > 0f

    /**
     * 只有往新消息方向滑、且列表物理末端、且最后一条底边真的贴进视口时才重新贴底。
     * 不能用 canScrollForward  alone：超高最后一条内部上滑时 canScrollForward 仍为 false，
     * 会误回贴；也不能用 isAtBottom  alone：在条目中部松手也会被判贴底。
     */
    fun shouldRestickToBottom(
        consumedY: Float,
        canScrollForward: Boolean,
        atBottom: Boolean,
    ): Boolean = consumedY < 0f && !canScrollForward && atBottom

    fun layoutIsAtBottom(layout: LazyListLayoutInfo): Boolean {
        val last = layout.visibleItemsInfo.lastOrNull() ?: return layout.totalItemsCount <= 0
        return isAtBottom(
            totalItems = layout.totalItemsCount,
            lastVisibleIndex = last.index,
            lastVisibleOffset = last.offset,
            lastVisibleSize = last.size,
            viewportEndOffset = layout.viewportEndOffset,
        )
    }

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
