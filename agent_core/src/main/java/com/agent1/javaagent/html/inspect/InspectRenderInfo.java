package com.agent1.javaagent.html.inspect;

/** 选中元素的渲染快照（computed style + 尺寸）。 */
public record InspectRenderInfo(
    String color,
    String backgroundColor,
    String fontSize,
    String display,
    Integer width,
    Integer height
) {

    public InspectRenderInfo {
        color = color == null ? "" : color;
        backgroundColor = backgroundColor == null ? "" : backgroundColor;
        fontSize = fontSize == null ? "" : fontSize;
        display = display == null ? "" : display;
    }

    public InspectRenderInfo() {
        this("", "", "", "", null, null);
    }
}
