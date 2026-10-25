package com.agent1.javaagent.html.inspect;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class DocxInspectFeedbackComposerTest {

    @Test
    public void compose_includesBlockIndexAndPackageGuide() {
        var element = new InspectedElement(
            "p",
            "",
            List.of(),
            "摘要",
            "<p>摘要</p>",
            null,
            List.of(),
            3
        );
        String draft = DocxInspectFeedbackComposer.compose("out/r.docx", element, "改字号");
        assertTrue(draft.contains("blockIndex=3"));
        assertTrue(draft.contains("weizhi:docx"));
        assertTrue(draft.contains("find_caps"));
        assertTrue(draft.contains("docx.readEdit"));
        assertTrue(draft.contains("readDocx(\"out/r.docx\")"));
        assertFalse(draft.contains("仅修改该元素及其直接子节点"));
    }

    @Test
    public void compose_withoutBlockIndex_stillIncludesFindCaps() {
        var element = new InspectedElement("p", "", List.of(), "标题", "<p>标题</p>", null, List.of());
        String draft = DocxInspectFeedbackComposer.compose("合同.docx", element, "改成红色标题");
        assertTrue(draft.contains("find_caps"));
        assertTrue(draft.contains("listBlocks"));
        assertTrue(draft.contains("点选文本参考：\"标题\""));
    }
}
