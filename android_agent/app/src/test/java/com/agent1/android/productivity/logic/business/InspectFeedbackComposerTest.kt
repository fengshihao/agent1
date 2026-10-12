package com.agent1.android.productivity.logic.business

import org.junit.Assert.assertEquals
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
        // 范围约束固定在末尾，防止 AI 顺手改坏页面其他部分
        assertTrue(
            draft.endsWith("- 范围: 仅修改该元素及其直接子节点的样式/内容，不要改动页面上其他部分"),
        )
    }

    @Test
    fun compose_includesRenderInfoLine() {
        val element = InspectedElement(
            tag = "button",
            id = "checkout-btn",
            render = InspectRenderInfo(
                color = "rgb(91, 98, 112)",
                backgroundColor = "rgb(250, 251, 252)",
                fontSize = "11px",
                display = "block",
                width = 302,
                height = 18,
            ),
        )
        val draft = InspectFeedbackComposer.compose("out/index.html", element, "颜色太浅")
        assertTrue(draft.contains("- 当前渲染: color=rgb(91, 98, 112) · bg=rgb(250, 251, 252) · font=11px · 302x18px"))
    }

    @Test
    fun compose_skipsTransparentBackgroundAndPartialSize() {
        // 透明背景不回传；宽高缺一时跳过尺寸段
        val draft = InspectFeedbackComposer.compose(
            "a.html",
            InspectedElement(
                tag = "span",
                render = InspectRenderInfo(
                    color = "rgb(51, 51, 51)",
                    backgroundColor = "rgba(0, 0, 0, 0)",
                    fontSize = "14px",
                    width = 100,
                    height = null,
                ),
            ),
            "字太小",
        )
        val renderLine = draft.lineSequence().first { it.startsWith("- 当前渲染:") }
        assertTrue(renderLine.contains("color=rgb(51, 51, 51)"))
        assertTrue(renderLine.contains("font=14px"))
        assertFalse(renderLine.contains("bg="))
        // 宽高缺一时跳过尺寸段（font 的 px 不算尺寸）
        assertFalse(renderLine.contains("x100"))
        assertFalse(renderLine.contains("100x"))
    }

    @Test
    fun compose_omitsRenderLineWhenAbsentOrEmpty() {
        val withNull = InspectFeedbackComposer.compose("a.html", InspectedElement(tag = "p"), "改样式")
        assertFalse(withNull.contains("- 当前渲染:"))
        val withEmpty = InspectFeedbackComposer.compose(
            "a.html",
            InspectedElement(tag = "p", render = InspectRenderInfo()),
            "改样式",
        )
        assertFalse(withEmpty.contains("- 当前渲染:"))
    }

    @Test
    fun compose_omitsBlankSections() {
        val draft = InspectFeedbackComposer.compose("a.htm", InspectedElement(tag = "br"), "间距太挤")
        assertTrue(draft.contains("- 选择器: br"))
        assertFalse(draft.contains("- 元素:"))
        assertFalse(draft.contains("- 可见文本:"))
        assertFalse(draft.contains("- 当前渲染:"))
        assertTrue(draft.endsWith("范围: 仅修改该元素及其直接子节点的样式/内容，不要改动页面上其他部分"))
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
        // 意见行之后紧跟范围行，不再位于末尾
        assertTrue(draft.contains("问题: 字有点小"))
    }
}