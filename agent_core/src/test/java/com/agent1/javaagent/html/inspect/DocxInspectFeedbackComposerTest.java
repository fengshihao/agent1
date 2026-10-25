package com.agent1.javaagent.html.inspect;

import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class DocxInspectFeedbackComposerTest {

    @Test
    public void compose_includesBlockIndexWhenPresent() {
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
        assertTrue(draft.contains("blockIndex: 3"));
        assertTrue(draft.contains("readDocx"));
    }
}
