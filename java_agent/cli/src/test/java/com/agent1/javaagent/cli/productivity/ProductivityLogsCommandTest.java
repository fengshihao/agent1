package com.agent1.javaagent.cli.productivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agent1.javaagent.log.EventJsonlWriter;
import com.agent1.javaagent.log.RunLogContext;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductivityLogsCommandTest {

    @TempDir
    Path temp;

    private Path agentRoot;
    private Path eventsFile;
    private StringWriter out;
    private StringWriter err;

    @BeforeEach
    void setUp() throws Exception {
        agentRoot = temp.resolve(".agent1");
        Files.createDirectories(agentRoot.resolve("logs"));
        eventsFile = agentRoot.resolve("logs").resolve("events.jsonl");
        out = new StringWriter();
        err = new StringWriter();
        seedFailedRun();
    }

    @Test
    void failedSubcommandPrintsToolAndRun() {
        int code = ProductivityLogsCommand.run(
            agentRoot,
            new String[] {"failed"},
            new PrintWriter(out, true),
            new PrintWriter(err, true)
        );

        assertEquals(0, code);
        String text = out.toString();
        assertTrue(text.contains("write_file"));
        assertTrue(text.contains("run-cli-1"));
        assertTrue(err.toString().isBlank());
    }

    @Test
    void childrenSubcommandPrintsChildRunAndTokens() throws Exception {
        EventJsonlWriter writer = new EventJsonlWriter(eventsFile);
        RunLogContext child = new RunLogContext("s1", "child1", "run-cli-1", "explore");
        writer.write(child, "run_started", Map.of());
        writer.write(child, "usage", Map.of("input_tokens", 3L, "output_tokens", 7L));
        writer.write(child, "run_completed", Map.of("status", "ok"));

        int code = ProductivityLogsCommand.run(
            agentRoot,
            new String[] {"children", "--parent-run", "run-cli-1"},
            new PrintWriter(out, true),
            new PrintWriter(err, true)
        );

        assertEquals(0, code);
        assertTrue(out.toString().contains("child1"));
        assertTrue(out.toString().contains("10")); // total tokens or parts
    }

    @Test
    void summarizeFiltersBySession() {
        int code = ProductivityLogsCommand.run(
            agentRoot,
            new String[] {"summarize", "--session", "s1"},
            new PrintWriter(out, true),
            new PrintWriter(err, true)
        );

        assertEquals(0, code);
        assertTrue(out.toString().contains("run-cli-1"));
    }

    private void seedFailedRun() {
        EventJsonlWriter writer = new EventJsonlWriter(eventsFile);
        RunLogContext ctx = new RunLogContext("s1", "run-cli-1", "", "main");
        writer.write(ctx, "run_started", Map.of());
        writer.write(
            ctx,
            "tool_call",
            Map.of("tool_name", "write_file", "tool_call_id", "tc1", "tool_args", "{}")
        );
        writer.write(
            ctx,
            "tool_result",
            Map.of("tool_call_id", "tc1", "is_error", true, "error_message", "denied")
        );
        writer.write(ctx, "run_failed", Map.of("error", "x"));
    }
}
