package com.agent1.javaagent.html.inspect;

/**
 * docx 内置预览审查草稿：在 HTML 审查格式上增加块索引，便于 Agent 用 readDocx 定位。
 */
public final class DocxInspectFeedbackComposer {

    private DocxInspectFeedbackComposer() {
    }

    public static String compose(String relativePath, InspectedElement element, String problem) {
        String base = InspectFeedbackComposer.compose(relativePath, element, problem);
        if (element.blockIndex() == null) {
            return base;
        }
        return base + "\n- Word 块索引 blockIndex: " + element.blockIndex()
            + "（请用 readDocx + replaceInBlock / setBlockStyle 修改，勿整文件重写）";
    }
}
