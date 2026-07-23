package com.agent1.javaagent.cli.productivity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class ProductivityModelsCommandTest {

    @Test
    void printsCatalogAndRuntimeSections() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        ProductivityModelsCommand.run(new PrintWriter(out, true), new PrintWriter(err, true));
        String text = out.toString();
        assertTrue(text.contains("qwen3.7-flash"));
        assertTrue(text.contains("context=1,000,000") || text.contains("context=1000000"));
        assertTrue(text.contains("=== 当前运行时 ==="));
    }
}
