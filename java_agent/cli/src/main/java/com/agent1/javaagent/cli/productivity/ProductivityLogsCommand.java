package com.agent1.javaagent.cli.productivity;

import com.agent1.javaagent.log.AgentDataPaths;
import com.agent1.javaagent.log.query.EventLogFilter;
import com.agent1.javaagent.log.query.EventLogQueryService;
import com.agent1.javaagent.log.query.FailedToolCall;
import com.agent1.javaagent.log.query.RunAggregate;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 生产力 CLI：{@code logs} 子命令（05-日志查询）。 */
public final class ProductivityLogsCommand {

    private ProductivityLogsCommand() {
    }

    public static int run(Path agentRoot, String[] args, PrintWriter out, PrintWriter err) {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printUsage(out);
            return 0;
        }
        Path eventsFile = AgentDataPaths.eventsJsonl(agentRoot);
        if (!Files.isRegularFile(eventsFile)) {
            err.println("未找到事件日志: " + eventsFile);
            return 1;
        }

        EventLogQueryService query = new EventLogQueryService();
        String sub = args[0].toLowerCase();
        ParsedFlags flags = ParsedFlags.parse(subcommandArgs(args));

        return switch (sub) {
            case "failed" -> printFailed(query, eventsFile, flags, out);
            case "children" -> printChildren(query, eventsFile, flags, out, err);
            case "summarize", "summary" -> printSummarize(query, eventsFile, flags, out);
            default -> {
                err.println("未知子命令: " + sub);
                printUsage(err);
                yield 1;
            }
        };
    }

    private static int printFailed(
        EventLogQueryService query,
        Path eventsFile,
        ParsedFlags flags,
        PrintWriter out
    ) {
        List<FailedToolCall> failed = query.listFailedToolCalls(eventsFile, flags.toFilter());
        if (failed.isEmpty()) {
            out.println("(无失败工具调用)");
            return 0;
        }
        for (FailedToolCall item : failed) {
            out.printf(
                "run=%s session=%s tool=%s call=%s error=%s%n",
                item.runId(),
                item.sessionId(),
                item.toolName(),
                item.toolCallId(),
                item.errorMessage()
            );
        }
        return 0;
    }

    private static int printChildren(
        EventLogQueryService query,
        Path eventsFile,
        ParsedFlags flags,
        PrintWriter out,
        PrintWriter err
    ) {
        if (flags.parentRun == null || flags.parentRun.isBlank()) {
            err.println("children 需要 --parent-run <runId>");
            return 1;
        }
        List<RunAggregate> children = query.listChildRunAggregates(eventsFile, flags.parentRun);
        if (children.isEmpty()) {
            out.println("(无子 Run)");
            return 0;
        }
        for (RunAggregate agg : children) {
            out.printf(
                "run=%s parent=%s agent=%s tokens=%d failed_tools=%d terminal=%s duration_ms=%d%n",
                agg.runId(),
                agg.parentRunId(),
                agg.agentId(),
                agg.totalTokens(),
                agg.failedToolCount(),
                agg.terminalType(),
                agg.durationMs()
            );
        }
        return 0;
    }

    private static int printSummarize(
        EventLogQueryService query,
        Path eventsFile,
        ParsedFlags flags,
        PrintWriter out
    ) {
        List<RunAggregate> runs = query.summarizeRuns(eventsFile, flags.toFilter());
        if (runs.isEmpty()) {
            out.println("(无匹配 Run)");
            return 0;
        }
        for (RunAggregate agg : runs) {
            out.printf(
                "run=%s session=%s events=%d failed_tools=%d in=%d out=%d terminal=%s duration_ms=%d%n",
                agg.runId(),
                agg.sessionId(),
                agg.eventCount(),
                agg.failedToolCount(),
                agg.inputTokens(),
                agg.outputTokens(),
                agg.terminalType(),
                agg.durationMs()
            );
        }
        return 0;
    }

    private static String[] subcommandArgs(String[] args) {
        if (args.length <= 1) {
            return new String[0];
        }
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        return rest;
    }

    private static void printUsage(PrintWriter out) {
        out.println("用法: logs <failed|children|summarize> [选项]");
        out.println("  failed [--session ID] [--run ID] [--tool NAME]");
        out.println("  children --parent-run ID");
        out.println("  summarize [--session ID] [--run ID]");
    }

    static final class ParsedFlags {
        String session;
        String run;
        String parentRun;
        String tool;

        EventLogFilter toFilter() {
            EventLogFilter.Builder builder = EventLogFilter.builder();
            if (session != null) {
                builder.sessionId(session);
            }
            if (run != null) {
                builder.runId(run);
            }
            if (parentRun != null) {
                builder.parentRunId(parentRun);
            }
            if (tool != null) {
                builder.toolName(tool);
            }
            return builder.build();
        }

        static ParsedFlags parse(String[] args) {
            ParsedFlags flags = new ParsedFlags();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--session".equals(arg) && i + 1 < args.length) {
                    flags.session = args[++i];
                } else if ("--run".equals(arg) && i + 1 < args.length) {
                    flags.run = args[++i];
                } else if ("--parent-run".equals(arg) && i + 1 < args.length) {
                    flags.parentRun = args[++i];
                } else if ("--tool".equals(arg) && i + 1 < args.length) {
                    flags.tool = args[++i];
                }
            }
            return flags;
        }
    }
}
