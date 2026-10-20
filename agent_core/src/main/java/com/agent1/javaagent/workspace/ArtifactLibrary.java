package com.agent1.javaagent.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 跨会话 workspace 产物扫描（对齐 Android {@code ArtifactLibraryStore}）。
 * 排除 imports/、tmp/ 与隐藏路径。
 */
public final class ArtifactLibrary {

    private static final List<String> EXCLUDED_DIR_PREFIXES = List.of("imports/", "tmp/");

    private ArtifactLibrary() {
    }

    public record ArtifactRef(String sessionId, String workspaceRelativePath) {
    }

    public record ArtifactEntry(
        String sessionId,
        String workspaceRelativePath,
        String fileName,
        long sizeBytes,
        long lastModifiedMillis
    ) {
        public String stableKey() {
            return sessionId + "/" + workspaceRelativePath;
        }

        public ArtifactRef toRef() {
            return new ArtifactRef(sessionId, workspaceRelativePath);
        }
    }

    public static List<ArtifactEntry> listArtifacts(Path sessionsRoot) {
        if (sessionsRoot == null || !Files.isDirectory(sessionsRoot)) {
            return List.of();
        }
        List<ArtifactEntry> entries = new ArrayList<>();
        try (Stream<Path> sessions = Files.list(sessionsRoot)) {
            for (Path sessionDir : sessions.filter(Files::isDirectory).toList()) {
                String sessionId = sessionDir.getFileName().toString();
                if (sessionId.isBlank()) {
                    continue;
                }
                Path workspace = sessionDir.resolve("workspace");
                if (!Files.isDirectory(workspace)) {
                    continue;
                }
                collectWorkspaceArtifacts(workspace, sessionId, entries);
            }
        } catch (IOException ignored) {
            return List.of();
        }
        entries.sort(
            Comparator.comparingLong(ArtifactEntry::lastModifiedMillis).reversed()
                .thenComparing(ArtifactEntry::stableKey)
        );
        return entries;
    }

    public static boolean isArtifactRelativePath(String relative) {
        String normalized = relative == null ? "" : relative.trim().replace('\\', '/');
        if (normalized.isBlank() || normalized.startsWith("/")) {
            return false;
        }
        for (String prefix : EXCLUDED_DIR_PREFIXES) {
            String dir = prefix.substring(0, prefix.length() - 1);
            if (normalized.equals(dir) || normalized.startsWith(prefix)) {
                return false;
            }
        }
        for (String part : normalized.split("/")) {
            if (part.isBlank() || part.startsWith(".")) {
                return false;
            }
        }
        return true;
    }

    public static Path resolveWorkspaceFile(Path sessionsRoot, String sessionId, String workspaceRelativePath) {
        if (sessionsRoot == null || sessionId == null || sessionId.isBlank()
            || workspaceRelativePath == null || workspaceRelativePath.isBlank()) {
            return null;
        }
        Path workspace = sessionsRoot.resolve(sessionId).resolve("workspace").toAbsolutePath().normalize();
        Path candidate = workspace.resolve(workspaceRelativePath).toAbsolutePath().normalize();
        if (!candidate.startsWith(workspace) || !Files.isRegularFile(candidate)) {
            return null;
        }
        return candidate;
    }

    private static void collectWorkspaceArtifacts(
        Path workspace,
        String sessionId,
        List<ArtifactEntry> out
    ) {
        Path root = workspace.toAbsolutePath().normalize();
        try (Stream<Path> walk = Files.walk(workspace)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                Path abs = file.toAbsolutePath().normalize();
                if (!abs.startsWith(root)) {
                    continue;
                }
                String relative = root.relativize(abs).toString().replace('\\', '/');
                if (!isArtifactRelativePath(relative)) {
                    continue;
                }
                BasicFileAttributes attrs = Files.readAttributes(abs, BasicFileAttributes.class);
                out.add(
                    new ArtifactEntry(
                        sessionId,
                        relative,
                        relative.substring(relative.lastIndexOf('/') + 1),
                        attrs.size(),
                        attrs.lastModifiedTime().toMillis()
                    )
                );
            }
        } catch (IOException ignored) {
            // skip session
        }
    }
}
