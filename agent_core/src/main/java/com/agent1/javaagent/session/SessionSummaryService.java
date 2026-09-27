package com.agent1.javaagent.session;

import com.agent1.javaagent.log.AgentAuditEvents;
import com.agent1.javaagent.log.RunLogContext;
import com.agent1.javaagent.model.AgentMessage;
import com.agent1.javaagent.promote.PromotionScanner;
import com.agent1.javaagent.run.FileRunStore;
import com.agent1.javaagent.run.RunRecord;
import com.agent1.javaagent.run.RunState;
import com.agent1.javaagent.util.PathIo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 7.5：规则化 Session 总结 → {@code sessions/<id>/session.summary.md}（无额外 LLM 调用）。
 */
public final class SessionSummaryService {

    public static final String SUMMARY_FILE_NAME = "session.summary.md";

    private final Path agentRoot;
    private final FileSessionStore sessionStore;
    private final FileRunStore runStore;

    public SessionSummaryService(Path agentRoot) {
        this.agentRoot = agentRoot.toAbsolutePath().normalize();
        this.sessionStore = new FileSessionStore(this.agentRoot);
        this.runStore = new FileRunStore(sessionStore);
    }

    public Path summaryPath(String sessionId) {
        return sessionStore.sessionDir(sessionId).resolve(SUMMARY_FILE_NAME);
    }

    public Path writeSummary(String sessionId, RunLogContext auditContext) throws IOException {
        SessionMeta meta = sessionStore.getSession(sessionId);
        List<AgentMessage> transcript = sessionStore.loadTranscript(sessionId);
        List<RunRecord> runs = listRuns(sessionId);
        Path workspace = sessionStore.workspaceDir(sessionId);
        PromotionScanner.ScanResult staging = PromotionScanner.scan(workspace);

        String markdown = render(meta, transcript, runs, staging);
        Path out = summaryPath(sessionId);
        Path summaryParent = out.getParent();
        if (summaryParent != null) {
            Files.createDirectories(summaryParent);
        }
        PathIo.writeString(out, markdown, StandardCharsets.UTF_8);

        AgentAuditEvents.sessionSummaryWritten(agentRoot, auditContext, sessionId, out, staging);
        return out;
    }

    private List<RunRecord> listRuns(String sessionId) throws IOException {
        Path runsDir = sessionStore.sessionDir(sessionId).resolve("runs");
        if (!Files.isDirectory(runsDir)) {
            return List.of();
        }
        List<RunRecord> runs = new ArrayList<>();
        try (Stream<Path> files = Files.list(runsDir)) {
            for (Path file : files.filter(p -> {
                Path name = p.getFileName();
                return name != null && name.toString().endsWith(".json");
            }).toList()) {
                Path fileName = file.getFileName();
                if (fileName == null) {
                    continue;
                }
                String runId = fileName.toString().replace(".json", "");
                runStore.readOptional(sessionId, runId).ifPresent(runs::add);
            }
        }
        runs.sort((a, b) -> a.getStartedAt().compareTo(b.getStartedAt()));
        return runs;
    }

    private static String render(
        SessionMeta meta,
        List<AgentMessage> transcript,
        List<RunRecord> runs,
        PromotionScanner.ScanResult staging
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Session 总结\n\n");
        sb.append("- sessionId: `").append(meta.getSessionId()).append("`\n");
        sb.append("- title: ").append(meta.getTitle()).append('\n');
        sb.append("- generatedAt: `").append(Instant.now()).append("`\n");
        sb.append("- generator: `SessionSummaryService`（规则化，非 LLM）\n\n");

        sb.append("## 目标（首条用户消息）\n\n");
        sb.append(firstUserMessage(transcript)).append("\n\n");

        sb.append("## Run 摘要\n\n");
        if (runs.isEmpty()) {
            sb.append("（尚无落盘 Run）\n\n");
        } else {
            for (RunRecord run : runs) {
                sb.append("- `").append(run.getRunId()).append("` ");
                sb.append(run.getState().wireValue());
                if (run.getLastError().isPresent()) {
                    sb.append(" — ").append(truncate(run.getLastError().get(), 120));
                }
                sb.append('\n');
            }
            sb.append('\n');
        }

        sb.append("## 对话快照\n\n");
        sb.append("- user 消息: ").append(countRole(transcript, AgentMessage.ROLE_USER)).append('\n');
        sb.append("- assistant 消息: ").append(countRole(transcript, AgentMessage.ROLE_ASSISTANT)).append('\n');
        String lastAssistant = lastRoleContent(transcript, AgentMessage.ROLE_ASSISTANT);
        if (!lastAssistant.isBlank()) {
            sb.append("- 末条 assistant 摘要: ").append(truncate(lastAssistant, 280)).append("\n\n");
        } else {
            sb.append("\n");
        }

        sb.append("## 未解决 / 失败 Run\n\n");
        List<RunRecord> failed = runs.stream().filter(r -> r.getState() != RunState.SUCCEEDED).toList();
        if (failed.isEmpty()) {
            sb.append("（无失败或中断 Run）\n\n");
        } else {
            for (RunRecord run : failed) {
                sb.append("- `").append(run.getRunId()).append("` ").append(run.getState().wireValue());
                run.getLastError().ifPresent(err -> sb.append(": ").append(truncate(err, 160)));
                sb.append('\n');
            }
            sb.append('\n');
        }

        sb.append("## 候选晋升（staging）\n\n");
        appendStaging(sb, staging);
        sb.append("\n后续可调用 `promote_request` 沉淀到 shared/local。\n");
        return sb.toString();
    }

    private static void appendStaging(StringBuilder sb, PromotionScanner.ScanResult staging) {
        if (!staging.rejections().isEmpty()) {
            sb.append("staging 校验问题：\n");
            for (String r : staging.rejections()) {
                sb.append("- ").append(r).append('\n');
            }
            sb.append('\n');
        }
        if (staging.skills().isEmpty() && staging.scripts().isEmpty()) {
            sb.append("（staging 为空）\n");
            return;
        }
        for (PromotionScanner.StagedSkill skill : staging.skills()) {
            sb.append("- skill: `").append(skill.dirName()).append("`\n");
        }
        for (PromotionScanner.StagedScript script : staging.scripts()) {
            sb.append("- script: `").append(script.fileName()).append("`\n");
        }
    }

    private static long countRole(List<AgentMessage> messages, String role) {
        return messages.stream().filter(m -> role.equals(m.getRole())).count();
    }

    private static String firstUserMessage(List<AgentMessage> messages) {
        for (AgentMessage m : messages) {
            if (AgentMessage.ROLE_USER.equals(m.getRole())) {
                String text = m.getContent() == null ? "" : m.getContent().trim();
                return text.isBlank() ? "（空）" : truncate(text, 500);
            }
        }
        return "（无用户消息）";
    }

    private static String lastRoleContent(List<AgentMessage> messages, String role) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage m = messages.get(i);
            if (role.equals(m.getRole())) {
                return m.getContent() == null ? "" : m.getContent().trim();
            }
        }
        return "";
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text.replace("\n", " ");
        }
        return text.substring(0, max).replace("\n", " ") + "…";
    }
}
