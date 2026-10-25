package com.agent1.javaagent.html.inspect;

import java.util.List;

/** 审查模式选中的元素（桥回传结构，见 {@link HtmlInspectScript}）。 */
public record InspectedElement(
    String tag,
    String id,
    List<String> classes,
    String textPreview,
    String outerHtml,
    InspectRenderInfo render,
    List<InspectAncestor> ancestors,
    /** docx 预览：{@code data-agent1-block-index}，HTML 审查为 null。 */
    Integer blockIndex
) {

    public InspectedElement {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("tag required");
        }
        id = id == null ? "" : id;
        classes = classes == null ? List.of() : List.copyOf(classes);
        textPreview = textPreview == null ? "" : textPreview;
        outerHtml = outerHtml == null ? "" : outerHtml;
        ancestors = ancestors == null ? List.of() : List.copyOf(ancestors);
    }

    public InspectedElement(String tag) {
        this(tag, "", List.of(), "", "", null, List.of(), null);
    }

    public InspectedElement(
        String tag,
        String id,
        List<String> classes,
        String textPreview,
        String outerHtml,
        InspectRenderInfo render,
        List<InspectAncestor> ancestors
    ) {
        this(tag, id, classes, textPreview, outerHtml, render, ancestors, null);
    }
}
