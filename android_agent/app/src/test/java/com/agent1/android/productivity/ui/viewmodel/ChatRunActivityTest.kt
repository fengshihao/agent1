package com.agent1.android.productivity.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRunActivityTest {

    @Test
    fun bodyTextKeepsGeneratingLabel() {
        assertEquals("正在生成回复…", streamActivityLabel("已经有正文", "", null))
        assertEquals("思考中…", streamActivityLabel("", "还在想", null))
        assertEquals("准备下一步…", streamActivityLabel("", "", "准备下一步…"))
    }
}
