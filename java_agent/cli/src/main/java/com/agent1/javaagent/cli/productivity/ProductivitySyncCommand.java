package com.agent1.javaagent.cli.productivity;

import com.agent1.javaagent.catalog.sync.CatalogSyncDiff;
import com.agent1.javaagent.catalog.sync.CatalogSyncService;
import com.agent1.javaagent.log.AgentAuditEvents;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** {@code agent1 sync check|apply [--ids a,b]}（阶段 5.3）。 */
public final class ProductivitySyncCommand {

    private ProductivitySyncCommand() {
    }

    public static int run(Path agentRoot, String[] args, PrintWriter out, PrintWriter err) {
        if (args.length == 0) {
            err.println("用法: agent1 sync check | agent1 sync apply [--ids id1,id2]");
            return 2;
        }
        String sub = args[0].toLowerCase();
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        CatalogSyncService service = new CatalogSyncService(agentRoot);
        try {
            if ("check".equals(sub)) {
                return runCheck(agentRoot, service, out, err);
            }
            if ("apply".equals(sub)) {
                return runApply(agentRoot, service, rest, out, err);
            }
            err.println("未知子命令: " + sub + "（可用 check、apply）");
            return 2;
        } catch (IllegalStateException e) {
            err.println(e.getMessage());
            return 1;
        } catch (IOException e) {
            err.println("sync 失败: " + e.getMessage());
            return 1;
        }
    }

    private static int runCheck(Path agentRoot, CatalogSyncService service, PrintWriter out, PrintWriter err)
        throws IOException {
        CatalogSyncService.SyncCheckResult result = service.check();
        AgentAuditEvents.catalogSyncChecked(agentRoot, null, result, "cli_sync_check");
        out.println("manifest: " + result.manifestUrl());
        out.println("catalogId: " + result.catalogId());
        out.println("pending: " + result.pending().size());
        for (Map.Entry<String, Integer> entry : result.pendingByKind().entrySet()) {
            out.println("  " + entry.getKey() + ": " + entry.getValue());
        }
        for (CatalogSyncDiff.PendingItem item : result.pending()) {
            out.println("- " + item.item().id() + " (" + item.reason().name().toLowerCase() + ")");
        }
        return 0;
    }

    private static int runApply(
        Path agentRoot,
        CatalogSyncService service,
        String[] rest,
        PrintWriter out,
        PrintWriter err
    ) throws IOException {
        List<String> ids = parseIds(rest);
        CatalogSyncService.SyncApplyResult result = service.apply(ids);
        if (!result.appliedIds().isEmpty() || !result.errors().isEmpty()) {
            AgentAuditEvents.catalogSyncCompleted(agentRoot, null, result, "cli_sync_apply");
        }
        out.println("manifest: " + result.manifestUrl());
        out.println("applied: " + result.appliedIds().size());
        for (String id : result.appliedIds()) {
            out.println("  ok " + id);
        }
        for (String error : result.errors()) {
            err.println("  fail " + error);
        }
        return result.errors().isEmpty() ? 0 : 1;
    }

    private static List<String> parseIds(String[] rest) {
        for (int i = 0; i < rest.length; i++) {
            if ("--ids".equalsIgnoreCase(rest[i]) && i + 1 < rest.length) {
                String raw = rest[i + 1];
                if (raw.isBlank()) {
                    return List.of();
                }
                return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            }
        }
        return List.of();
    }
}
