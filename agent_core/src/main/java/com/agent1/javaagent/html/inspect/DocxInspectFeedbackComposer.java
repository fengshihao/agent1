package com.agent1.javaagent.html.inspect;

/**
 * docx 内置预览审查草稿：结构化元素信息 + Agent 改 docx 的执行指引（包、find_caps、块索引）。
 */
public final class DocxInspectFeedbackComposer {

    private static final String HTML_SCOPE_LINE =
        "- 范围: 仅修改该元素及其直接子节点的样式/内容，不要改动页面上其他部分";

    private static final int TEXT_PREVIEW_HINT_MAX = 120;

    private DocxInspectFeedbackComposer() {
    }

    public static String compose(String relativePath, InspectedElement element, String problem) {
        String base = stripHtmlScopeLine(InspectFeedbackComposer.compose(relativePath, element, problem));
        return base + "\n\n" + agentEditGuide(relativePath, element);
    }

    private static String stripHtmlScopeLine(String base) {
        if (base == null || base.isEmpty()) {
            return "";
        }
        String trimmed = base.trim();
        if (trimmed.endsWith(HTML_SCOPE_LINE)) {
            return trimmed.substring(0, trimmed.length() - HTML_SCOPE_LINE.length()).trim();
        }
        return trimmed.replace("\n" + HTML_SCOPE_LINE, "").trim();
    }

    private static String agentEditGuide(String relativePath, InspectedElement element) {
        String path = relativePath == null ? "" : relativePath.trim();
        StringBuilder g = new StringBuilder();
        g.append("- 执行指引（修改本 Word，请严格遵循）：\n");
        g.append("  - 工具 run_js；官方包 weizhi:docx：readDocx → grep/textView/listBlocks 定位 → ")
            .append("setBlockText / replaceInBlock / setBlockStyle → save。\n");
        g.append("  - 写脚本前请先 find_caps（query 建议「docx 修改」「readEdit」或「阅读并修改已有 Word」），")
            .append("按返回条目 docx.readEdit 的调用示例实现；勿凭记忆猜字段。\n");
        g.append("  - 目标文件：\"").append(path).append("\"；readDocx(\"").append(path)
            .append("\") 打开，默认 save 写回同一路径。\n");
        g.append("  - 勿用 webview_exec 手改 zip；勿 markdownToDocx / weizhi:docx-build 整份覆盖")
            .append("（除非用户明确要求新建或重写全文）。高级 OOXML 才考虑 weizhi:docx-raw。\n");
        Integer idx = element.blockIndex();
        if (idx != null) {
            g.append("  - 用户点选块 blockIndex=").append(idx)
                .append("：优先改该块；表格/图片块不支持 setBlockStyle。\n");
        } else {
            g.append("  - 未带上 blockIndex：用 doc.grep(片段) 或 doc.listBlocks({ type: \"heading\" }) 定位。\n");
        }
        String preview = element.textPreview() == null ? "" : element.textPreview().trim();
        if (!preview.isEmpty()) {
            g.append("  - 点选文本参考：").append(quoteHint(preview)).append('\n');
        }
        return g.toString().trim();
    }

    private static String quoteHint(String text) {
        String t = text.length() > TEXT_PREVIEW_HINT_MAX
            ? text.substring(0, TEXT_PREVIEW_HINT_MAX) + "…"
            : text;
        return "\"" + t.replace("\"", "'") + "\"";
    }
}
