package com.agent1.javaagent.promote;

import com.agent1.javaagent.agent.AgentHomeBootstrap;
import com.agent1.javaagent.catalog.AgentCatalogPaths;
import com.agent1.javaagent.log.AgentAuditEvents;
import com.agent1.javaagent.util.PathIo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** workspace/staging → shared/local（阶段 6.2，审查自动通过）。 */
public final class PromotionService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path agentRoot;
    private final Path workspaceRoot;

    public PromotionService(Path agentRoot, Path workspaceRoot) {
        this.agentRoot = agentRoot.toAbsolutePath().normalize();
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
    }

    public PromotionResult promote(String auditNote) throws IOException {
        AgentHomeBootstrap.ensure(agentRoot);
        PromotionScanner.ScanResult scan = PromotionScanner.scan(workspaceRoot);
        if (!scan.rejections().isEmpty()) {
            AgentAuditEvents.promotionRejected(agentRoot, null, scan.rejections(), auditNote, workspaceRoot.toString());
            return PromotionResult.rejected(scan.rejections());
        }
        if (scan.skills().isEmpty() && scan.scripts().isEmpty()) {
            return PromotionResult.empty(
                "staging 为空：请在 workspace/staging/skills/<name>/SKILL.md 或 staging/scripts/*.js 准备内容。"
            );
        }

        List<String> promoted = new ArrayList<>();
        Path localSkills = agentRoot.resolve("shared/local/skills");
        Path localScripts = agentRoot.resolve("shared/local/scripts");
        Files.createDirectories(localSkills);
        Files.createDirectories(localScripts);

        for (PromotionScanner.StagedSkill skill : scan.skills()) {
            Path dest = localSkills.resolve(skill.dirName());
            copyTree(skill.skillMd().getParent(), dest);
            writeLocalCapability("local.skill." + skill.dirName(), "skill", dest);
            promoted.add("skill:" + skill.dirName());
        }

        Path catalogScripts = AgentCatalogPaths.catalogScriptsDir(agentRoot);
        Files.createDirectories(catalogScripts);
        for (PromotionScanner.StagedScript script : scan.scripts()) {
            Path dest = localScripts.resolve(script.fileName());
            Files.copy(script.scriptFile(), dest, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(dest, catalogScripts.resolve(script.fileName()), StandardCopyOption.REPLACE_EXISTING);
            if (script.metaFile() != null) {
                Path metaDest = localScripts.resolve(script.metaFile().getFileName());
                Files.copy(script.metaFile(), metaDest, StandardCopyOption.REPLACE_EXISTING);
            }
            writeLocalCapability("local.script." + stripJs(script.fileName()), "script", dest);
            promoted.add("script:" + script.fileName());
        }

        AgentAuditEvents.promotionCompleted(agentRoot, null, promoted, auditNote, workspaceRoot.toString());
        return PromotionResult.success(promoted, "review: auto-approved");
    }

    private void writeLocalCapability(String id, String kind, Path installedPath) throws IOException {
        ObjectNode pseudo = MAPPER.createObjectNode();
        pseudo.put("id", id);
        pseudo.put("kind", kind);
        pseudo.put("version", "local");
        pseudo.put("digest", "local");
        String relative = agentRoot.relativize(installedPath.toAbsolutePath().normalize())
            .toString()
            .replace('\\', '/');
        PathIo.writeString(
            agentRoot.resolve("docs/capabilities/" + id.replace('/', '_') + ".md"),
            "# " + id + "\n\n"
                + "- kind: `" + kind + "`\n"
                + "- source: promotion\n"
                + "- path: `" + relative + "`\n"
                + "- updatedAt: `" + Instant.now() + "`\n",
            java.nio.charset.StandardCharsets.UTF_8
        );
    }

    private static String stripJs(String name) {
        if (name.toLowerCase().endsWith(".js")) {
            return name.substring(0, name.length() - 3);
        }
        return name;
    }

    private static void copyTree(Path source, Path dest) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path target = dest.resolve(source.relativize(dir));
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path target = dest.resolve(source.relativize(file));
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public record PromotionResult(boolean ok, List<String> promoted, List<String> rejections, String message) {
        static PromotionResult success(List<String> promoted, String message) {
            return new PromotionResult(true, promoted, List.of(), message);
        }

        static PromotionResult rejected(List<String> rejections) {
            return new PromotionResult(false, List.of(), rejections, "promotion_rejected");
        }

        static PromotionResult empty(String message) {
            return new PromotionResult(false, List.of(), List.of(), message);
        }
    }
}
