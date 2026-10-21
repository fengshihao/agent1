package com.agent1.javaagent.html.inspect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeckHtmlDetectorTest {

    @Test
    void detectsSlideClassInDoubleQuotes() {
        assertTrue(DeckHtmlDetector.isDeckHtml("<section class=\"slide\">"));
    }

    @Test
    void ignoresUnrelatedHtml() {
        assertFalse(DeckHtmlDetector.isDeckHtml("<div class=\"card\">"));
    }

    @Test
    void scanFile_readsHeadOnly(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("deck.html");
        Files.writeString(file, "<html><section class='slide'></section>");
        assertTrue(DeckHtmlDetector.scanFile(file));
    }
}
