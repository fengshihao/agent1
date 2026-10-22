package com.agent1.javaagent.cli.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProductivityUiCommandTest {

    @Test
    void resolveNoBrowserFromFlag() {
        assertTrue(ProductivityUiCommand.resolveNoBrowser(new String[]{"--no-browser"}));
        assertTrue(ProductivityUiCommand.resolveNoBrowser(new String[]{"--port", "9999", "--no-browser"}));
        assertFalse(ProductivityUiCommand.resolveNoBrowser(new String[]{"--port", "9999"}));
        assertFalse(ProductivityUiCommand.resolveNoBrowser(new String[]{}));
        assertFalse(ProductivityUiCommand.resolveNoBrowser(null));
    }
}
