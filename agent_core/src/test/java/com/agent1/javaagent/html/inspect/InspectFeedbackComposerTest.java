package com.agent1.javaagent.html.inspect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InspectFeedbackComposerTest {

    @Test
    void compose_containsPathSelectorOpinionAndProblemLine() {
        var element = new InspectedElement(
            "button",
            "checkout-btn",
            List.of(),
            "立即支付",
            "<button id=\"checkout-btn\">立即支付</button>",
            null,
            List.of()
        );
        String draft = InspectFeedbackComposer.compose("out/index.html", element, "按钮颜色太浅，改成深蓝");
        assertTrue(draft.contains("out/index.html"));
        assertTrue(draft.contains("#checkout-btn"));
        assertTrue(draft.contains("<button id=\"checkout-btn\">立即支付</button>"));
        assertTrue(draft.contains("立即支付"));
        assertTrue(draft.contains("按钮颜色太浅，改成深蓝"));
        assertTrue(draft.contains("- 问题: 按钮颜色太浅，改成深蓝"));
        assertTrue(
            draft.endsWith("- 范围: 仅修改该元素及其直接子节点的样式/内容，不要改动页面上其他部分")
        );
    }

    @Test
    void compose_includesRenderInfoLine() {
        var element = new InspectedElement(
            "button",
            "checkout-btn",
            List.of(),
            "",
            "",
            new InspectRenderInfo(
                "rgb(91, 98, 112)",
                "rgb(250, 251, 252)",
                "11px",
                "block",
                302,
                18
            ),
            List.of()
        );
        String draft = InspectFeedbackComposer.compose("out/index.html", element, "颜色太浅");
        assertTrue(
            draft.contains("- 当前渲染: color=rgb(91, 98, 112) · bg=rgb(250, 251, 252) · font=11px · 302x18px")
        );
    }

    @Test
    void compose_skipsTransparentBackgroundAndPartialSize() {
        String draft = InspectFeedbackComposer.compose(
            "a.html",
            new InspectedElement(
                "span",
                "",
                List.of(),
                "",
                "",
                new InspectRenderInfo("rgb(51, 51, 51)", "rgba(0, 0, 0, 0)", "14px", "", 100, null),
                List.of()
            ),
            "字太小"
        );
        String renderLine = draft.lines().filter(l -> l.startsWith("- 当前渲染:")).findFirst().orElse("");
        assertTrue(renderLine.contains("color=rgb(51, 51, 51)"));
        assertTrue(renderLine.contains("font=14px"));
        assertFalse(renderLine.contains("bg="));
        assertFalse(renderLine.contains("x100"));
        assertFalse(renderLine.contains("100x"));
    }

    @Test
    void compose_omitsRenderLineWhenAbsentOrEmpty() {
        String withNull = InspectFeedbackComposer.compose("a.html", new InspectedElement("p"), "改样式");
        assertFalse(withNull.contains("- 当前渲染:"));
        String withEmpty = InspectFeedbackComposer.compose(
            "a.html",
            new InspectedElement("p", "", List.of(), "", "", new InspectRenderInfo(), List.of()),
            "改样式"
        );
        assertFalse(withEmpty.contains("- 当前渲染:"));
    }

    @Test
    void compose_omitsBlankSections() {
        String draft = InspectFeedbackComposer.compose("a.htm", new InspectedElement("br"), "间距太挤");
        assertTrue(draft.contains("- 选择器: br"));
        assertFalse(draft.contains("- 元素:"));
        assertFalse(draft.contains("- 可见文本:"));
        assertFalse(draft.contains("- 当前渲染:"));
        assertTrue(draft.endsWith("范围: 仅修改该元素及其直接子节点的样式/内容，不要改动页面上其他部分"));
    }

    @Test
    void compose_truncatesLongOuterHtml() {
        String longHtml = "<div " + "x".repeat(400) + ">内容</div>";
        String draft = InspectFeedbackComposer.compose(
            "a.html",
            new InspectedElement("div", "", List.of(), "", longHtml, null, List.of()),
            "改样式"
        );
        String line = draft.lines().filter(l -> l.startsWith("- 元素:")).findFirst().orElse("");
        assertTrue(line.length() < 260);
        assertTrue(line.endsWith("…"));
    }

    @Test
    void compose_trimsOpinionWhitespace() {
        String draft = InspectFeedbackComposer.compose("a.html", new InspectedElement("p"), "  字有点小  ");
        assertTrue(draft.contains("问题: 字有点小"));
    }
}
