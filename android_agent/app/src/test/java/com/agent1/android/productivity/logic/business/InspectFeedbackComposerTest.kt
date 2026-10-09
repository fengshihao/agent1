package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectFeedbackComposerTest {

    @Test
    fun compose_containsPathSelectorOpinionAndProblemLine() {
        val element = InspectedElement(
            tag = "button",
            id = "checkout-btn",
            textPreview = "立即支付",
            outerHtml = "<button id=\"checkout-btn\">立即支付</button>",
        )
        val draft = InspectFeedbackComposer.compose(
            "out/index.html",
            element,
            "按钮颜色太浅，改成深蓝",
        )
        assertTrue(draft.contains("out/index.html"))
        assertTrue(draft.contains("#checkout-btn"))
        assertTrue(draft.contains("<button id=\"checkout-btn\">立即支付</button>"))
        assertTrue(draft.contains("立即支付"))
        assertTrue(draft.contains("按钮颜色太浅，改成深蓝"))
        // 用户意见必须落在「问题」行，AI 据此修改
        assertTrue(draft.contains("- 问题: 按钮颜色太浅，改成深蓝"))
    }

    @Test
    fun compose_omitsBlankSections() {
        val draft = InspectFeedbackComposer.compose("a.htm", InspectedElement(tag = "br"), "间距太挤")
        assertTrue(draft.contains("- 选择器: br"))
        assertFalse(draft.contains("- 元素:"))
        assertFalse(draft.contains("- 可见文本:"))
        assertTrue(draft.endsWith("问题: 间距太挤"))
    }

    @Test
    fun compose_truncatesLongOuterHtml() {
        val longHtml = "<div " + "x".repeat(400) + ">内容</div>"
        val draft = InspectFeedbackComposer.compose(
            "a.html",
            InspectedElement(tag = "div", outerHtml = longHtml),
            "改样式",
        )
        // 200 字上限 + 截断标记
        val line = draft.lineSequence().first { it.startsWith("- 元素:") }
        assertTrue(line.length < 260)
        assertTrue(line.endsWith("…"))
    }

    @Test
    fun compose_trimsOpinionWhitespace() {
        val draft = InspectFeedbackComposer.compose(
            "a.html",
            InspectedElement(tag = "p"),
            "  字有点小  ",
        )
        assertTrue(draft.endsWith("问题: 字有点小"))
    }
}