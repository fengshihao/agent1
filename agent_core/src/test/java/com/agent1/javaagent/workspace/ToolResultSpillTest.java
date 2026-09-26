package com.agent1.javaagent.workspace;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.tool.ToolExecutionResult;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class ToolResultSpillTest {

    @TempDir
    Path temp;

    @Test
    void spillsLargeResultsToWorkspace() throws Exception {
        Path root = temp.resolve("ws");
        Files.createDirectories(root);
        WorkspaceSandbox sandbox = new WorkspaceSandbox(root);
        String big = "x".repeat(ToolResultSpill.MAX_INLINE_BYTES + 100);
        ToolExecutionResult spilled = ToolResultSpill.maybeSpill(
            sandbox,
            "write_file",
            ToolExecutionResult.text(big)
        );
        assertTrue(spilled.getText().contains(".spill/"));
        assertTrue(Files.exists(root.resolve(".spill")));
        long spillFiles = Files.list(root.resolve(".spill")).count();
        assertTrue(spillFiles >= 1);
        byte[] onDisk = Files.readAllBytes(
            Files.list(root.resolve(".spill")).findFirst().orElseThrow()
        );
        assertTrue(onDisk.length > ToolResultSpill.MAX_INLINE_BYTES);
    }

    @Test
    void readFileSkipsSpill() {
        WorkspaceSandbox sandbox = new WorkspaceSandbox(temp);
        String big = "y".repeat(5000);
        ToolExecutionResult same = ToolResultSpill.maybeSpill(
            sandbox,
            "read_file",
            ToolExecutionResult.text(big)
        );
        assertTrue(same.getText().equals(big));
    }
}
