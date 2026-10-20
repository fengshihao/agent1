package com.agent1.javaagent.cli.ui;

import com.agent1.javaagent.session.FileSessionStore;
import com.agent1.javaagent.workspace.SessionAttachmentLimits;
import com.agent1.javaagent.workspace.WorkspaceSandbox;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 桌面 Web UI：上传文件到 workspace/imports/。 */
public final class DesktopAttachments {

    private DesktopAttachments() {
    }

    public record ListedFile(String workspaceRelativePath, String fileName, long sizeBytes) {
    }

    public static List<ListedFile> listImports(Path agentRoot, String sessionId) throws IOException {
        Path imports = importsDir(agentRoot, sessionId);
        if (!Files.isDirectory(imports)) {
            return List.of();
        }
        List<ListedFile> out = new ArrayList<>();
        try (var stream = Files.list(imports)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                out.add(new ListedFile("imports/" + name, name, Files.size(file)));
            }
        }
        return out;
    }

    public static ListedFile importBytes(Path agentRoot, String sessionId, String originalName, byte[] data)
        throws IOException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("empty file");
        }
        if (data.length > SessionAttachmentLimits.MAX_FILE_BYTES) {
            throw new IllegalArgumentException(
                "文件超过 " + SessionAttachmentLimits.MAX_FILE_SIZE_MB + "MB 上限"
            );
        }
        List<ListedFile> existing = listImports(agentRoot, sessionId);
        if (existing.size() >= SessionAttachmentLimits.MAX_FILES_PER_SESSION) {
            throw new IllegalArgumentException(
                "本会话附件已达上限 " + SessionAttachmentLimits.MAX_FILES_PER_SESSION
            );
        }
        String safeName = sanitizeFileName(originalName);
        Path imports = importsDir(agentRoot, sessionId);
        Files.createDirectories(imports);
        Path dest = imports.resolve(safeName);
        int suffix = 1;
        while (Files.exists(dest)) {
            dest = imports.resolve(stem(safeName) + "-" + suffix + ext(safeName));
            suffix++;
        }
        Files.write(dest, data);
        String relative = "imports/" + dest.getFileName();
        return new ListedFile(relative, dest.getFileName().toString(), data.length);
    }

    public static void deleteImport(Path agentRoot, String sessionId, String workspaceRelativePath)
        throws IOException {
        Path workspace = new FileSessionStore(agentRoot).workspaceDir(sessionId);
        WorkspaceSandbox sandbox = new WorkspaceSandbox(workspace, agentRoot);
        Path file = sandbox.resolveWrite(workspaceRelativePath);
        if (!file.normalize().startsWith(importsDir(agentRoot, sessionId))) {
            throw new SecurityException("only imports/ deletable");
        }
        Files.deleteIfExists(file);
    }

    private static Path importsDir(Path agentRoot, String sessionId) {
        return new FileSessionStore(agentRoot).workspaceDir(sessionId).resolve("imports");
    }

    private static String sanitizeFileName(String name) {
        String base = name == null ? "upload" : name.trim();
        if (base.isBlank()) {
            base = "upload";
        }
        base = base.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[^a-zA-Z0-9._\\-一-龥]", "_");
        if (base.isBlank()) {
            base = "upload";
        }
        return base.length() > 120 ? base.substring(0, 120) : base;
    }

    private static String stem(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot).toLowerCase(Locale.ROOT) : "";
    }
}
