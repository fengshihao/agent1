package com.agent1.android.productivity.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentWorkStatusMappingTest {

    @Test
    fun idleRunDoesNotRequestKeepAlive() {
        val status = ChatUiState(
            title = "火箭",
            isRunning = false,
            runActivityLabel = "思考中…",
            streamingText = "已经说完",
        ).toAgentWorkStatus()
        assertFalse(status.active)
        assertEquals("火箭", status.sessionTitle)
    }

    @Test
    fun runningMapsLabelAndStreamingExcerpt() {
        val status = ChatUiState(
            title = "火箭科普",
            isRunning = true,
            runActivityLabel = "正在生成回复…",
            streamingText = "推进剂\n在燃烧室混合",
        ).toAgentWorkStatus()
        assertTrue(status.active)
        assertEquals("火箭科普", status.sessionTitle)
        assertEquals("正在生成回复…", status.activityLabel)
        assertEquals("推进剂 在燃烧室混合", status.detail)
    }
}
