package com.agent1.javaagent.html.inspect;

import java.util.ArrayList;
import java.util.List;

/**
 * 审查模式反馈消息组装（18 号规划 Phase B / REQ-122）。
 * 预览页内直接发送给 AI；用户意见在预览面板输入，随结构化元素信息一并发出。
 */
public final class InspectFeedbackComposer {

    private static final int OUTER_HTML_DRAFT_MAX_CHARS = 200;

    private InspectFeedbackComposer() {
    }

    public static String compose(String relativePath, InspectedElement element, String problem) {
        String selector = ElementSelectorBuilder.build(element);
        String outerHtmlFull = element.outerHtml();
        String outerHtml = outerHtmlFull.substring(0, Math.min(outerHtmlFull.length(), OUTER_HTML_DRAFT_MAX_CHARS)).trim();
        String outerSuffix = outerHtmlFull.length() > OUTER_HTML_DRAFT_MAX_CHARS ? "…" : "";
        String opinion = problem == null ? "" : problem.trim();

        StringBuilder out = new StringBuilder();
        out.append("请修改工作区文件 ").append(relativePath).append(" 中这个元素：\n");
        out.append("- 选择器: ").append(selector).append('\n');
        if (!outerHtml.isEmpty()) {
            out.append("- 元素: ").append(outerHtml).append(outerSuffix).append('\n');
        }
        if (!element.textPreview().isEmpty()) {
            out.append("- 可见文本: ").append(element.textPreview()).append('\n');
        }
        String renderLine = renderInfoLine(element.render());
        if (renderLine != null) {
            out.append("- 当前渲染: ").append(renderLine).append('\n');
        }
        out.append("- 问题: ").append(opinion).append('\n');
        out.append("- 范围: 仅修改该元素及其直接子节点的样式/内容，不要改动页面上其他部分");
        return out.toString().trim();
    }

    private static String renderInfoLine(InspectRenderInfo render) {
        if (render == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (!render.color().isEmpty()) {
            parts.add("color=" + render.color());
        }
        if (!render.backgroundColor().isEmpty()
            && !"rgba(0, 0, 0, 0)".equals(render.backgroundColor())) {
            parts.add("bg=" + render.backgroundColor());
        }
        if (!render.fontSize().isEmpty()) {
            parts.add("font=" + render.fontSize());
        }
        if (render.width() != null && render.height() != null) {
            parts.add(render.width() + "x" + render.height() + "px");
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join(" · ", parts);
    }
}
