package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentWorkKeepAliveTest {

    @Test
    fun idleProducesNoNotice() {
        assertNull(
            AgentWorkNoticeCopy.from(
                AgentWorkStatus(active = false, sessionTitle = "火箭", activityLabel = "思考中…"),
            ),
        )
    }

    @Test
    fun noticeUsesSessionTitleAndLabel() {
        val notice = AgentWorkNoticeCopy.from(
            AgentWorkStatus(active = true, sessionTitle = "火箭科普", activityLabel = "调用工具 · run_js"),
        )
        assertEquals("火箭科普", notice?.title)
        assertEquals("调用工具 · run_js", notice?.text)
        assertEquals("调用工具 · run_js", notice?.activityLabel)
    }

    @Test
    fun blankTitleAndLabelFallBack() {
        val notice = AgentWorkNoticeCopy.from(
            AgentWorkStatus(active = true, sessionTitle = "  ", activityLabel = null),
        )
        assertEquals("新对话", notice?.title)
        assertEquals("正在工作…", notice?.text)
    }

    @Test
    fun streamingExcerptAppendedAndCapped() {
        val long = "甲".repeat(80)
        assertEquals("…" + "甲".repeat(41), AgentWorkNoticeCopy.streamingExcerpt(long))
        assertEquals("推进剂 混合", AgentWorkNoticeCopy.streamingExcerpt(" 推进剂\n混合 "))
        assertEquals("", AgentWorkNoticeCopy.streamingExcerpt("  \n"))

        val notice = AgentWorkNoticeCopy.from(
            AgentWorkStatus(
                active = true,
                sessionTitle = "火箭",
                activityLabel = "正在生成回复…",
                detail = "燃烧室里混合",
            ),
        )
        assertEquals("正在生成回复… 燃烧室里混合", notice?.text)
        assertEquals("正在生成回复…", notice?.activityLabel)
    }

    @Test
    fun duplicateDetailIsNotRepeated() {
        val notice = AgentWorkNoticeCopy.from(
            AgentWorkStatus(
                active = true,
                sessionTitle = "火箭",
                activityLabel = "思考中…",
                detail = "思考中…",
            ),
        )
        assertEquals("思考中…", notice?.text)
    }

    @Test
    fun noticeTextIsCapped() {
        val notice = AgentWorkNoticeCopy.from(
            AgentWorkStatus(
                active = true,
                sessionTitle = "火箭",
                activityLabel = "工".repeat(200),
            ),
        )
        assertEquals(120, notice?.text?.length)
    }

    @Test
    fun showWhenRunStarts() {
        val decision = AgentWorkKeepAlive.decide(
            holding = false,
            ownerSessionId = null,
            sessionId = "a",
            previous = null,
            status = active("正在连接模型…"),
            millisSinceLastShow = 0L,
        )
        assertEquals(KeepAliveAction.SHOW, decision.action)
        assertEquals("正在连接模型…", decision.notice?.text)
        assertFalse(decision.throttled)
    }

    @Test
    fun stopOnlyTheOwningSession() {
        val stop = AgentWorkKeepAlive.decide(
            holding = true,
            ownerSessionId = "a",
            sessionId = "a",
            previous = notice("思考中…"),
            status = AgentWorkStatus(active = false, sessionTitle = "火箭", activityLabel = null),
            millisSinceLastShow = 10L,
        )
        assertEquals(KeepAliveAction.STOP, stop.action)

        val otherIdle = AgentWorkKeepAlive.decide(
            holding = true,
            ownerSessionId = "a",
            sessionId = "b",
            previous = notice("思考中…"),
            status = AgentWorkStatus(active = false, sessionTitle = "别的", activityLabel = null),
            millisSinceLastShow = 10L,
        )
        assertEquals(KeepAliveAction.SKIP, otherIdle.action)
    }

    @Test
    fun labelChangeShowsImmediatelyExcerptIsThrottled() {
        val previous = AgentWorkNotice(
            title = "火箭",
            text = "正在生成回复… 第一句",
            activityLabel = "正在生成回复…",
        )
        val throttled = AgentWorkKeepAlive.decide(
            holding = true,
            ownerSessionId = "a",
            sessionId = "a",
            previous = previous,
            status = AgentWorkStatus(
                active = true,
                sessionTitle = "火箭",
                activityLabel = "正在生成回复…",
                detail = "第二句",
            ),
            millisSinceLastShow = 200L,
        )
        assertEquals(KeepAliveAction.SKIP, throttled.action)
        assertTrue(throttled.throttled)
        assertEquals("正在生成回复… 第二句", throttled.notice?.text)

        val due = AgentWorkKeepAlive.decide(
            holding = true,
            ownerSessionId = "a",
            sessionId = "a",
            previous = previous,
            status = AgentWorkStatus(
                active = true,
                sessionTitle = "火箭",
                activityLabel = "正在生成回复…",
                detail = "第二句",
            ),
            millisSinceLastShow = 1_000L,
        )
        assertEquals(KeepAliveAction.SHOW, due.action)
        assertFalse(due.throttled)

        val tool = AgentWorkKeepAlive.decide(
            holding = true,
            ownerSessionId = "a",
            sessionId = "a",
            previous = previous,
            status = active("调用工具 · run_js"),
            millisSinceLastShow = 50L,
        )
        assertEquals(KeepAliveAction.SHOW, tool.action)
        assertEquals("调用工具 · run_js", tool.notice?.activityLabel)
    }

    @Test
    fun identicalNoticeIsSkipped() {
        val previous = notice("思考中…")
        val decision = AgentWorkKeepAlive.decide(
            holding = true,
            ownerSessionId = "a",
            sessionId = "a",
            previous = previous,
            status = active("思考中…"),
            millisSinceLastShow = 5_000L,
        )
        assertEquals(KeepAliveAction.SKIP, decision.action)
        assertFalse(decision.throttled)
    }

    @Test
    fun otherSessionCanTakeOverWhenItStartsWork() {
        val decision = AgentWorkKeepAlive.decide(
            holding = true,
            ownerSessionId = "a",
            sessionId = "b",
            previous = notice("思考中…"),
            status = AgentWorkStatus(active = true, sessionTitle = "新的", activityLabel = "正在连接模型…"),
            millisSinceLastShow = 10L,
        )
        assertEquals(KeepAliveAction.SHOW, decision.action)
        assertEquals("新的", decision.notice?.title)
    }

    private fun active(label: String) = AgentWorkStatus(
        active = true,
        sessionTitle = "火箭",
        activityLabel = label,
    )

    private fun notice(label: String) = AgentWorkNotice(
        title = "火箭",
        text = label,
        activityLabel = label,
    )
}
