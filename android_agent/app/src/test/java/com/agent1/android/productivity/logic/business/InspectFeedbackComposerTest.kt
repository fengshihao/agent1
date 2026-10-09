package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectFeedbackComposerTest {

    @Test
    fun compose_containsPathSelectorAndProblemLine() {
        val element = InspectedElement(
            tag = "button",
            id = "checkout-btn",
            textPreview = "立即支付",
            outerHtml = "<button id=\"checkout-btn\">立即支付</button>",
        )
        val draft = InspectFeedbackComposer.compose("out/index.html", element)
        assertTrue(draft.contains("out/index.html"))
        assertTrue(draft.contains("#checkout-btn"))
        assertTrue(draft.contains("<button id=\"checkout-btn\">立即支付</button>"))
        assertTrue(draft.contains("立即支付"))
        // 结尾是待补充的问题行，不自动发送任何修改指令
        assertTrue(draft.trimEnd().endsWith("问题:"))
        assertFalse(draft.contains("已修改"))
    }

    @Test
    fun compose_omitsBlankSections() {
        val draft = InspectFeedbackComposer.compose("a.htm", InspectedElement(tag = "br"))
        assertTrue(draft.contains("- 选择器: br"))
        assertFalse(draft.contains("- 元素:"))
        assertFalse(draft.contains("- 可见文本:"))
    }

    @Test
    fun compose_truncatesLongOuterHtml() {
        val longHtml = "<div " + "x".repeat(400) + ">内容</div>"
        val draft = InspectFeedbackComposer.compose("a.html", InspectedElement(tag = "div", outerHtml = longHtml))
        // 200 字上限 + 截断标记
        val line = draft.lineSequence().first { it.startsWith("- 元素:") }
        assertTrue(line.length < 260)
        assertTrue(line.endsWith("…"))
    }

    @Test
    fun mergeDraft_fillsBlankInputDirectly() {
        val merged = InspectFeedbackComposer.mergeDraft("", "草稿A")
        assertEquals("草稿A", merged)
    }

    @Test
    fun mergeDraft_appendsInsteadOfOverwriting() {
        val merged = InspectFeedbackComposer.mergeDraft("我打了一半的话", "草稿A")
        assertEquals("我打了一半的话\n草稿A", merged)
    }

    @Test
    fun mergeDraft_ignoresBlankDraft() {
        assertEquals("原输入", InspectFeedbackComposer.mergeDraft("原输入", ""))
        assertEquals("", InspectFeedbackComposer.mergeDraft("", " "))
    }
}